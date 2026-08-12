package dev.jlo.tradingpost.settlement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.mint.api.id.AccountId;
import dev.jlo.mint.api.id.ActorId;
import dev.jlo.mint.api.id.ClientId;
import dev.jlo.mint.api.id.CurrencyId;
import dev.jlo.mint.api.id.IdempotencyKey;
import dev.jlo.mint.api.id.NamespaceId;
import dev.jlo.mint.api.ledger.BalanceSnapshot;
import dev.jlo.mint.api.ledger.Posting;
import dev.jlo.mint.api.ledger.TransactionKind;
import dev.jlo.mint.api.ledger.TransactionReceipt;
import dev.jlo.mint.api.result.Committed;
import dev.jlo.mint.api.result.OperationOutcome;
import dev.jlo.mint.api.result.Rejected;
import dev.jlo.mint.api.result.Rejection;
import dev.jlo.mint.api.result.RejectionCode;
import dev.jlo.tradingpost.config.TradingPostConfig;
import dev.jlo.tradingpost.db.Database;
import dev.jlo.tradingpost.db.MarketRepository;
import dev.jlo.tradingpost.db.MigrationRunner;
import dev.jlo.tradingpost.db.OrderRepository;
import dev.jlo.tradingpost.db.SettlementRepository;
import dev.jlo.tradingpost.domain.BuyOrder;
import dev.jlo.tradingpost.domain.Market;
import dev.jlo.tradingpost.domain.OrderStatus;
import dev.jlo.tradingpost.domain.SellOrder;
import dev.jlo.tradingpost.domain.SellOrderMode;
import dev.jlo.tradingpost.domain.SettlementKind;
import dev.jlo.tradingpost.domain.SettlementState;
import dev.jlo.tradingpost.lifecycle.AsyncExecutor;
import dev.jlo.tradingpost.market.MatchingEngine;
import dev.jlo.tradingpost.mint.MintOperations;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SettlementServiceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private TradingPostConfig config;
    private Database database;
    private AsyncExecutor executor;
    private FakeMint mint;
    private SettlementService settlements;
    private OrderRepository orders;
    private SettlementRepository settlementRows;
    private MarketRepository markets;

    @BeforeEach
    void setUp() {
        config = new TradingPostConfig(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), "settlement_it", 4,
                ClientId.of(NamespaceId.parse("tradingpost:plugin")), CurrencyId.parse("mint:credits"),
                AccountId.of(NamespaceId.parse("tradingpost:escrow")),
                AccountId.of(NamespaceId.parse("tradingpost:fees")),
                AccountId.of(NamespaceId.parse("tradingpost:tax")), 100, 500, 4, 30, 30, 576,
                List.of(Duration.ofHours(1)), Duration.ofMinutes(1), Duration.ofSeconds(15),
                Duration.ofMinutes(10), Duration.ofSeconds(1));
        database = new Database(config);
        MigrationRunner.migrate(database, config);
        executor = new AsyncExecutor(8);
        mint = new FakeMint(config);
        settlements = new SettlementService(database, config, mint, executor);
        orders = new OrderRepository(config.schema());
        settlementRows = new SettlementRepository(config.schema());
        markets = new MarketRepository(config.schema());
        database.transaction(connection -> {
            if (markets.find(connection, "spawn").isEmpty()) {
                markets.insert(connection, new Market("spawn", "Spawn", 100, 500));
            }
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown(Duration.ofSeconds(2));
        }
        if (database != null) {
            database.close();
        }
    }

    @Test
    void listingFeeSubmitActivatesSellOrder() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID sellId = UUID.randomUUID();
        UUID feeId = UUID.randomUUID();
        Instant now = Instant.now();
        SellOrder sell = new SellOrder(sellId, "spawn", seller, "minecraft:diamond", new byte[] {1}, "fp",
                2, 2, new BigDecimal("10.00"), SellOrderMode.NORMAL, OrderStatus.CREATING,
                now.plus(Duration.ofHours(1)), now);
        database.transaction(connection -> {
            orders.insertSell(connection, sell);
            settlementRows.insertReserved(connection, new SettlementRepository.SettlementDraft(
                    feeId, SettlementKind.LISTING_FEE, "ah/listing-fee/" + sellId, null, sellId,
                    new BigDecimal("0.20")));
            return null;
        });

        settlements.submitReserved(feeId).toCompletableFuture().get(10, TimeUnit.SECONDS);

        database.transaction(connection -> {
            SellOrder after = orders.findSell(connection, sellId, false).orElseThrow();
            assertEquals(OrderStatus.ACTIVE, after.status());
            var settlement = settlementRows.find(connection, feeId, false).orElseThrow();
            assertEquals(SettlementState.DELIVERED, settlement.state());
            return null;
        });
    }

    @Test
    void matchSubmitDeliversMailboxAndRejectCompensates() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        UUID sellId = UUID.randomUUID();
        UUID buyId = UUID.randomUUID();
        Instant now = Instant.now();
        SellOrder sell = new SellOrder(sellId, "spawn", seller, "minecraft:diamond", new byte[] {1, 2}, "fp",
                4, 4, new BigDecimal("8.00"), SellOrderMode.NORMAL, OrderStatus.ACTIVE,
                now.plus(Duration.ofHours(1)), now);
        BuyOrder buy = new BuyOrder(buyId, "spawn", buyer, "minecraft:diamond", "fp", 4, 4,
                new BigDecimal("10.00"), new BigDecimal("40.00"), OrderStatus.OPEN,
                now.plus(Duration.ofHours(1)), now);
        UUID matchSettlementId = UUID.randomUUID();
        database.transaction(connection -> {
            orders.insertSell(connection, sell);
            orders.insertBuy(connection, buy);
            orders.reserveMatch(connection, new MatchingEngine.MatchDecision(sellId, buyId, 2, new BigDecimal("8.00")),
                    new OrderRepository.SettlementDraft(matchSettlementId, SettlementKind.MATCH_SETTLEMENT,
                            "ignored", null, null, new BigDecimal("16.00")),
                    new byte[] {1}, new byte[] {2});
            return null;
        });

        settlements.submitReserved(matchSettlementId).toCompletableFuture().get(10, TimeUnit.SECONDS);

        database.transaction(connection -> {
            var settlement = settlementRows.find(connection, matchSettlementId, false).orElseThrow();
            assertEquals(SettlementState.DELIVERED, settlement.state());
            try (var rows = connection.createStatement().executeQuery(
                    "SELECT count(*) FROM settlement_it.mailbox_items WHERE reason='PURCHASE'")) {
                rows.next();
                assertEquals(1, rows.getInt(1));
            }
            return null;
        });

        // Second path: reserved partial match rejected by Mint — restore book + full pre-match stack, no mailbox.
        UUID sellId2 = UUID.randomUUID();
        UUID buyId2 = UUID.randomUUID();
        UUID rejectSettlementId = UUID.randomUUID();
        byte[] originalBlob = new byte[] {9, 9, 9}; // pre-match full stack encoding
        byte[] filledBlob = new byte[] {9};         // 1 unit detached for the fill
        byte[] remainingBlob = new byte[] {9, 9};   // 2 units left on the book
        SellOrder sell2 = new SellOrder(sellId2, "spawn", seller, "minecraft:emerald", originalBlob, "fp2",
                3, 3, new BigDecimal("5.00"), SellOrderMode.NORMAL, OrderStatus.ACTIVE,
                now.plus(Duration.ofHours(1)), now);
        BuyOrder buy2 = new BuyOrder(buyId2, "spawn", buyer, "minecraft:emerald", "fp2", 3, 3,
                new BigDecimal("5.00"), new BigDecimal("15.00"), OrderStatus.OPEN,
                now.plus(Duration.ofHours(1)), now);
        database.transaction(connection -> {
            orders.insertSell(connection, sell2);
            orders.insertBuy(connection, buy2);
            orders.reserveMatch(connection, new MatchingEngine.MatchDecision(sellId2, buyId2, 1, new BigDecimal("5.00")),
                    new OrderRepository.SettlementDraft(rejectSettlementId, SettlementKind.MATCH_SETTLEMENT,
                            "ignored", null, null, new BigDecimal("5.00")),
                    filledBlob, remainingBlob);
            // After reserve: sell should hold the post-match remainder only.
            SellOrder mid = orders.findSell(connection, sellId2, false).orElseThrow();
            assertEquals(2, mid.quantityRemaining());
            assertArrayEquals(remainingBlob, mid.itemBlob());
            return null;
        });
        mint.rejectNext.set(true);
        settlements.submitReserved(rejectSettlementId).toCompletableFuture().get(10, TimeUnit.SECONDS);

        database.transaction(connection -> {
            SellOrder after = orders.findSell(connection, sellId2, false).orElseThrow();
            assertEquals(3, after.quantityRemaining());
            assertEquals(OrderStatus.ACTIVE, after.status());
            // Restored item_blob must match the pre-match stack (not only the filled portion).
            assertArrayEquals(originalBlob, after.itemBlob());
            BuyOrder afterBuy = orders.findBuy(connection, buyId2, false).orElseThrow();
            assertEquals(3, afterBuy.quantityRemaining());
            assertEquals(new BigDecimal("15.00"), afterBuy.escrowReserved());
            var settlement = settlementRows.find(connection, rejectSettlementId, false).orElseThrow();
            assertEquals(SettlementState.FAILED, settlement.state());
            try (var rows = connection.createStatement().executeQuery(
                    "SELECT status FROM settlement_it.fills WHERE sell_order_id='" + sellId2 + "'")) {
                rows.next();
                assertEquals("VOIDED", rows.getString(1));
            }
            // Design: match rejection restores the book only — never mailboxes MATCH_REJECTED (double grant).
            try (var rows = connection.createStatement().executeQuery(
                    "SELECT count(*) FROM settlement_it.mailbox_items WHERE reason='MATCH_REJECTED'")) {
                rows.next();
                assertEquals(0, rows.getInt(1));
            }
            try (var rows = connection.createStatement().executeQuery(
                    "SELECT count(*) FROM settlement_it.mailbox_items WHERE settlement_id='"
                            + rejectSettlementId + "'")) {
                rows.next();
                assertEquals(0, rows.getInt(1));
            }
            return null;
        });
    }

    @Test
    void rejectAfterPriorPartialThatZeroedOrderReopensActiveAndOpen() throws Exception {
        // Prior successful partial leaves qty_remaining=2; a later fill zeros the order (FILLED).
        // Rejecting that zeroing fill must restore qty and reopen ACTIVE/OPEN — not leave FILLED.
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        UUID sellId = UUID.randomUUID();
        UUID buyId = UUID.randomUUID();
        Instant now = Instant.now();
        byte[] fullBlob = new byte[] {7, 7, 7, 7};
        byte[] afterFirstRemaining = new byte[] {7, 7};
        byte[] afterSecondRemaining = new byte[] {};
        SellOrder sell = new SellOrder(sellId, "spawn", seller, "minecraft:gold_ingot", fullBlob, "fp-gold",
                4, 4, new BigDecimal("3.00"), SellOrderMode.NORMAL, OrderStatus.ACTIVE,
                now.plus(Duration.ofHours(1)), now);
        BuyOrder buy = new BuyOrder(buyId, "spawn", buyer, "minecraft:gold_ingot", "fp-gold", 4, 4,
                new BigDecimal("3.00"), new BigDecimal("12.00"), OrderStatus.OPEN,
                now.plus(Duration.ofHours(1)), now);
        UUID firstSettlementId = UUID.randomUUID();
        UUID zeroingSettlementId = UUID.randomUUID();

        database.transaction(connection -> {
            orders.insertSell(connection, sell);
            orders.insertBuy(connection, buy);
            // First partial: 2 of 4
            orders.reserveMatch(connection, new MatchingEngine.MatchDecision(sellId, buyId, 2, new BigDecimal("3.00")),
                    new OrderRepository.SettlementDraft(firstSettlementId, SettlementKind.MATCH_SETTLEMENT,
                            "ignored", null, null, new BigDecimal("6.00")),
                    new byte[] {7, 7}, afterFirstRemaining);
            return null;
        });
        settlements.submitReserved(firstSettlementId).toCompletableFuture().get(10, TimeUnit.SECONDS);

        database.transaction(connection -> {
            SellOrder mid = orders.findSell(connection, sellId, false).orElseThrow();
            assertEquals(2, mid.quantityRemaining());
            assertEquals(OrderStatus.ACTIVE, mid.status());
            BuyOrder midBuy = orders.findBuy(connection, buyId, false).orElseThrow();
            assertEquals(2, midBuy.quantityRemaining());
            assertEquals(OrderStatus.OPEN, midBuy.status());
            // Zeroing fill: takes remaining 2 → FILLED
            orders.reserveMatch(connection, new MatchingEngine.MatchDecision(sellId, buyId, 2, new BigDecimal("3.00")),
                    new OrderRepository.SettlementDraft(zeroingSettlementId, SettlementKind.MATCH_SETTLEMENT,
                            "ignored", null, null, new BigDecimal("6.00")),
                    new byte[] {7, 7}, afterSecondRemaining);
            SellOrder zeroed = orders.findSell(connection, sellId, false).orElseThrow();
            assertEquals(0, zeroed.quantityRemaining());
            assertEquals(OrderStatus.FILLED, zeroed.status());
            BuyOrder zeroedBuy = orders.findBuy(connection, buyId, false).orElseThrow();
            assertEquals(0, zeroedBuy.quantityRemaining());
            assertEquals(OrderStatus.FILLED, zeroedBuy.status());
            return null;
        });

        mint.rejectNext.set(true);
        settlements.submitReserved(zeroingSettlementId).toCompletableFuture().get(10, TimeUnit.SECONDS);

        database.transaction(connection -> {
            SellOrder after = orders.findSell(connection, sellId, false).orElseThrow();
            assertEquals(2, after.quantityRemaining());
            assertEquals(OrderStatus.ACTIVE, after.status(),
                    "compensating a zeroing fill must reopen ACTIVE, not leave FILLED");
            assertArrayEquals(afterFirstRemaining, after.itemBlob());
            BuyOrder afterBuy = orders.findBuy(connection, buyId, false).orElseThrow();
            assertEquals(2, afterBuy.quantityRemaining());
            assertEquals(OrderStatus.OPEN, afterBuy.status(),
                    "compensating a zeroing fill must reopen OPEN, not leave FILLED");
            var settlement = settlementRows.find(connection, zeroingSettlementId, false).orElseThrow();
            assertEquals(SettlementState.FAILED, settlement.state());
            try (var rows = connection.createStatement().executeQuery(
                    "SELECT count(*) FROM settlement_it.mailbox_items WHERE reason='MATCH_REJECTED'")) {
                rows.next();
                assertEquals(0, rows.getInt(1));
            }
            return null;
        });
    }

    @Test
    void recoverySubmitsOrphanedReservedSettlement() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID sellId = UUID.randomUUID();
        UUID feeId = UUID.randomUUID();
        Instant now = Instant.now();
        SellOrder sell = new SellOrder(sellId, "spawn", seller, "minecraft:iron_ingot", new byte[] {3}, "fp3",
                1, 1, new BigDecimal("2.00"), SellOrderMode.NORMAL, OrderStatus.CREATING,
                now.plus(Duration.ofHours(1)), now);
        database.transaction(connection -> {
            orders.insertSell(connection, sell);
            settlementRows.insertReserved(connection, new SettlementRepository.SettlementDraft(
                    feeId, SettlementKind.LISTING_FEE, "ah/listing-fee/" + sellId, null, sellId,
                    new BigDecimal("0.02")));
            return null;
        });

        SettlementRecoveryWorker recovery = new SettlementRecoveryWorker(
                database, config.schema(), "test-node", settlements, executor);
        int processed = recovery.runOnce().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(1, processed);

        database.transaction(connection -> {
            assertEquals(OrderStatus.ACTIVE, orders.findSell(connection, sellId, false).orElseThrow().status());
            assertEquals(SettlementState.DELIVERED,
                    settlementRows.find(connection, feeId, false).orElseThrow().state());
            return null;
        });
        assertTrue(mint.transferKeys.contains("ah/listing-fee/" + sellId));
    }

    private static final class FakeMint implements MintOperations {
        private final TradingPostConfig config;
        private final AtomicBoolean rejectNext = new AtomicBoolean();
        private final Map<String, TransactionReceipt> receipts = new ConcurrentHashMap<>();
        private final java.util.Set<String> transferKeys = ConcurrentHashMap.newKeySet();

        private FakeMint(TradingPostConfig config) {
            this.config = config;
        }

        @Override
        public CompletionStage<Boolean> accountsExist(List<AccountId> accountIds) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<OperationOutcome<TransactionReceipt>> transfer(
                IdempotencyKey key, ActorId actor, List<Posting> postings, String reason,
                Map<String, String> metadata) {
            transferKeys.add(key.value());
            if (rejectNext.compareAndSet(true, false)) {
                return CompletableFuture.completedFuture(new Rejected<>(
                        new Rejection(RejectionCode.INSUFFICIENT_AVAILABLE, "not enough funds", Map.of())));
            }
            TransactionReceipt receipt = new TransactionReceipt(
                    UUID.randomUUID(), key, TransactionKind.TRANSFER, postings, Instant.now(), metadata);
            receipts.put(key.value(), receipt);
            return CompletableFuture.completedFuture(new Committed<>(receipt));
        }

        @Override
        public CompletionStage<Optional<TransactionReceipt>> receipt(IdempotencyKey key) {
            return CompletableFuture.completedFuture(Optional.ofNullable(receipts.get(key.value())));
        }

        @Override
        public CompletionStage<BalanceSnapshot> balance(AccountId accountId) {
            return CompletableFuture.completedFuture(
                    new BalanceSnapshot(accountId, config.currencyId(), BigDecimal.ZERO, BigDecimal.ZERO, 0L, Instant.now()));
        }

        @Override
        public TradingPostConfig config() {
            return config;
        }
    }
}
