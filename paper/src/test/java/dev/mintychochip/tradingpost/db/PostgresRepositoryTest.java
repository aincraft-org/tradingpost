package dev.mintychochip.tradingpost.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.mint.api.id.AccountId;
import dev.mintychochip.mint.api.id.ClientId;
import dev.mintychochip.mint.api.id.CurrencyId;
import dev.mintychochip.mint.api.id.NamespaceId;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.domain.Market;
import dev.mintychochip.tradingpost.domain.TradingPostBlock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresRepositoryTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Test
  void migrationCreatesOrderAndSettlementConstraints() throws Exception {
    TradingPostConfig config =
        new TradingPostConfig(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword(),
            "tradingpost_test",
            4,
            ClientId.of(NamespaceId.parse("tradingpost:plugin")),
            CurrencyId.parse("mint:credits"),
            AccountId.of(NamespaceId.parse("tradingpost:escrow")),
            AccountId.of(NamespaceId.parse("tradingpost:fees")),
            AccountId.of(NamespaceId.parse("tradingpost:tax")),
            100,
            500,
            4,
            30,
            30,
            576,
            List.of(Duration.ofHours(1)),
            Duration.ofMinutes(1),
            Duration.ofSeconds(15),
            Duration.ofMinutes(10),
            Duration.ofSeconds(1));
    try (Database database = new Database(config)) {
      MigrationRunner.migrate(database, config);
      database.transaction(
          connection -> {
            try (var tables =
                connection.getMetaData().getTables(null, "tradingpost_test", "fills", null)) {
              assertTrue(tables.next());
            }
            try (var columns =
                connection
                    .getMetaData()
                    .getColumns(null, "tradingpost_test", "sell_orders", "material")) {
              assertTrue(columns.next());
            }
            try (var settlements =
                connection
                    .getMetaData()
                    .getColumns(null, "tradingpost_test", "settlements", "amount")) {
              assertTrue(settlements.next());
            }
            try (var posts =
                connection
                    .getMetaData()
                    .getColumns(null, "tradingpost_test", "trading_posts", "entity_uuid")) {
              assertTrue(posts.next());
            }
            try (var versions =
                connection
                    .createStatement()
                    .executeQuery(
                        "SELECT count(*) FROM tradingpost_test.schema_version WHERE version IN (1, 2, 3)")) {
              versions.next();
              assertEquals(3, versions.getInt(1));
            }
            try (var operations =
                connection
                    .getMetaData()
                    .getColumns(null, "tradingpost_test", "sell_now_operations", "operation_id")) {
              assertTrue(operations.next());
            }
            try (var operationIds =
                connection
                    .getMetaData()
                    .getColumns(null, "tradingpost_test", "fills", "operation_id")) {
              assertTrue(operationIds.next());
            }
            return null;
          });
      MarketRepository markets = new MarketRepository(config.schema());
      UUID firstVillager = UUID.randomUUID();
      database.transaction(
          connection -> {
            markets.insert(connection, new Market("guild-market", "Guild Market", 100, 500));
            markets.insertPost(
                connection,
                new TradingPostBlock(
                    UUID.randomUUID(), firstVillager, "guild-market", "world", 10, 64, 10));
            assertTrue(markets.hasPostForMarket(connection, "guild-market"));
            TradingPostBlock stored =
                markets.findPostByEntity(connection, firstVillager).orElseThrow();
            assertEquals(firstVillager, stored.entityId());
            assertEquals("guild-market", stored.marketName());
            return null;
          });
      assertThrows(
          IllegalStateException.class,
          () ->
              database.transaction(
                  connection -> {
                    markets.insertPost(
                        connection,
                        new TradingPostBlock(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            "guild-market",
                            "world",
                            20,
                            64,
                            20));
                    return null;
                  }));
    }
  }
}
