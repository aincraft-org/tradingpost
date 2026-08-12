package dev.jlo.tradingpost.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import dev.jlo.mint.api.id.AccountId;
import dev.jlo.mint.api.id.ClientId;
import dev.jlo.mint.api.id.CurrencyId;
import dev.jlo.mint.api.id.NamespaceId;
import dev.jlo.tradingpost.config.TradingPostConfig;
import dev.jlo.tradingpost.domain.BuyOrder;
import dev.jlo.tradingpost.domain.Market;
import dev.jlo.tradingpost.domain.OrderStatus;
import dev.jlo.tradingpost.domain.SellOrder;
import dev.jlo.tradingpost.domain.SellOrderMode;
import dev.jlo.tradingpost.domain.SettlementKind;
import dev.jlo.tradingpost.market.MatchingEngine;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OrderRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void reserveMatchDecrementsEscrowAndUsesValidDeterministicKey() {
        TradingPostConfig config = new TradingPostConfig(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), "orders_test", 4,
                ClientId.of(NamespaceId.parse("tradingpost:plugin")), CurrencyId.parse("mint:credits"),
                AccountId.of(NamespaceId.parse("tradingpost:escrow")), AccountId.of(NamespaceId.parse("tradingpost:fees")),
                AccountId.of(NamespaceId.parse("tradingpost:tax")), 100, 500, 4, 30, 30, 576,
                List.of(Duration.ofHours(1)), Duration.ofMinutes(1), Duration.ofMinutes(1),
                Duration.ofMinutes(10), Duration.ofSeconds(1));
        try (Database database = new Database(config)) {
            MigrationRunner.migrate(database, config);
            OrderRepository orders = new OrderRepository(config.schema());
            MarketRepository markets = new MarketRepository(config.schema());
            UUID seller = UUID.randomUUID();
            UUID buyer = UUID.randomUUID();
            UUID sellId = UUID.randomUUID();
            UUID buyId = UUID.randomUUID();
            Instant now = Instant.now();
            SellOrder sell = new SellOrder(sellId, "spawn", seller, "minecraft:diamond", new byte[] {1}, "fp",
                    4, 4, new BigDecimal("8.00"), SellOrderMode.NORMAL, OrderStatus.ACTIVE,
                    now.plus(Duration.ofHours(1)), now);
            BuyOrder buy = new BuyOrder(buyId, "spawn", buyer, "minecraft:diamond", "fp", 4, 4,
                    new BigDecimal("10.00"), new BigDecimal("40.00"), OrderStatus.OPEN, now.plus(Duration.ofHours(1)), now);
            database.transaction(connection -> {
                markets.insert(connection, new Market("spawn", "Spawn", 100, 500));
                orders.insertSell(connection, sell);
                orders.insertBuy(connection, buy);
                orders.reserveMatch(connection, new MatchingEngine.MatchDecision(sellId, buyId, 2,
                                new BigDecimal("8.00")),
                        new OrderRepository.SettlementDraft(UUID.randomUUID(), SettlementKind.MATCH_SETTLEMENT,
                                "ignored", null, null, new BigDecimal("16.00")),
                        new byte[] {2}, new byte[] {3});
                return null;
            });
            database.transaction(connection -> {
                try (var rows = connection.createStatement().executeQuery(
                        "SELECT escrow_reserved FROM orders_test.buy_orders WHERE id='" + buyId + "'")) {
                    rows.next();
                    assertEquals(new BigDecimal("24.00"), rows.getBigDecimal(1));
                }
                try (var rows = connection.createStatement().executeQuery(
                        "SELECT idempotency_key FROM orders_test.settlements")) {
                    rows.next();
                    String key = rows.getString(1);
                    assertTrue(key.startsWith("ah/match/"));
                    assertFalse(key.contains(":"));
                }
                return null;
            });
            database.transaction(connection -> {
                UUID fillId;
                try (var rows = connection.createStatement().executeQuery(
                        "SELECT fill_id FROM orders_test.fills")) {
                    rows.next();
                    fillId = (UUID) rows.getObject(1);
                }
                // Auto-restore uses pre-match sell blob stored on the fill (original {1}).
                OrderRepository.Compensation compensation = orders.compensateFill(connection, fillId);
                assertEquals(seller, compensation.seller());
                try (var rows = connection.createStatement().executeQuery(
                        "SELECT quantity_remaining,item_blob,status FROM orders_test.sell_orders WHERE id='" + sellId + "'")) {
                    rows.next();
                    assertEquals(4, rows.getInt(1));
                    assertArrayEquals(new byte[] {1}, rows.getBytes(2));
                    assertEquals("ACTIVE", rows.getString(3));
                }
                try (var rows = connection.createStatement().executeQuery(
                        "SELECT quantity_remaining,escrow_reserved FROM orders_test.buy_orders WHERE id='" + buyId + "'")) {
                    rows.next();
                    assertEquals(4, rows.getInt(1));
                    assertEquals(new BigDecimal("40.00"), rows.getBigDecimal(2));
                }
                try (var rows = connection.createStatement().executeQuery(
                        "SELECT status FROM orders_test.fills")) {
                    rows.next();
                    assertEquals("VOIDED", rows.getString(1));
                }
                try (var rows = connection.createStatement().executeQuery(
                        "SELECT count(*) FROM orders_test.mailbox_items WHERE settlement_id IS NOT NULL")) {
                    rows.next();
                    assertEquals(0, rows.getInt(1));
                }
                return null;
            });
        }
    }
}
