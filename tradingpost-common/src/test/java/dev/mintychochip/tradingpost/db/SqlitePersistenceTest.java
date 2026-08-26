package dev.mintychochip.tradingpost.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.tradingpost.config.DatabaseEngine;
import dev.mintychochip.tradingpost.config.TestConfigs;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.domain.Market;
import dev.mintychochip.tradingpost.domain.OrderStatus;
import dev.mintychochip.tradingpost.domain.SellOrder;
import dev.mintychochip.tradingpost.domain.SellOrderMode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SqlitePersistenceTest {

  @Test
  void migrateAndRoundTripUseConfigYmlSchemaTradingpost() throws Exception {
    Path sqliteFile = Files.createTempFile("tradingpost", ".db");
    TradingPostConfig config =
        TestConfigs.jdbc(
            DatabaseEngine.SQLITE,
            "jdbc:sqlite:" + sqliteFile.toAbsolutePath(),
            "",
            "",
            "tradingpost");
    try (Database database = new Database(config)) {
      MigrationRunner.migrate(database, config);
      MarketRepository markets = new MarketRepository(config);
      OrderRepository orders = new OrderRepository(config);
      UUID sellId = UUID.randomUUID();
      Instant now = Instant.parse("2026-08-19T12:00:00Z");
      byte[] blob = new byte[] {9, 8, 7};
      database.transaction(
          connection -> {
            markets.insert(connection, new Market("spawn", "Spawn", 100, 500));
            orders.insertSell(
                connection,
                new SellOrder(
                    sellId,
                    "spawn",
                    UUID.randomUUID(),
                    "minecraft:oak_log",
                    blob,
                    "fp",
                    2,
                    2,
                    new BigDecimal("1.00"),
                    SellOrderMode.NORMAL,
                    OrderStatus.ACTIVE,
                    now.plusSeconds(60),
                    now));
            return null;
          });
      database.transaction(
          connection -> {
            assertEquals("spawn", markets.find(connection, "spawn").orElseThrow().name());
            assertEquals(sellId, orders.findSell(connection, sellId, false).orElseThrow().id());
            assertFalse(
                MigrationRunner.tableExists(connection, SqlDialect.from(config), "mailbox_items"));
            String insert = SqlStatements.load("orders/insert-sell.sql", SqlDialect.from(config));
            assertTrue(insert.startsWith("INSERT INTO sell_orders"));
            assertFalse(insert.contains("tradingpost."));
            assertEquals("sell_orders", SqlDialect.from(config).table("sell_orders"));
            return null;
          });
    } finally {
      Files.deleteIfExists(sqliteFile);
    }
  }

  @Test
  void v4DropsLeftoverMailboxItemsTable() throws Exception {
    Path sqliteFile = Files.createTempFile("tradingpost-mailbox-drop", ".db");
    TradingPostConfig config =
        TestConfigs.jdbc(
            DatabaseEngine.SQLITE,
            "jdbc:sqlite:" + sqliteFile.toAbsolutePath(),
            "",
            "",
            "tradingpost");
    try (Database database = new Database(config)) {
      SqlDialect sql = SqlDialect.from(config);
      database.transaction(
          connection -> {
            try (var statement = connection.createStatement()) {
              statement.execute(
                  "CREATE TABLE schema_version (version integer PRIMARY KEY, applied_at text NOT NULL DEFAULT 'now')");
              statement.execute(
                  "INSERT INTO schema_version(version, applied_at) VALUES (1, 'now')");
              statement.execute(
                  "INSERT INTO schema_version(version, applied_at) VALUES (2, 'now')");
              statement.execute(
                  "INSERT INTO schema_version(version, applied_at) VALUES (3, 'now')");
              statement.execute("CREATE TABLE mailbox_items (id text PRIMARY KEY)");
            }
            assertTrue(MigrationRunner.tableExists(connection, sql, "mailbox_items"));
            return null;
          });
      MigrationRunner.migrate(database, config);
      database.transaction(
          connection -> {
            assertFalse(MigrationRunner.tableExists(connection, sql, "mailbox_items"));
            return null;
          });
    } finally {
      Files.deleteIfExists(sqliteFile);
    }
  }

  @Test
  void expiredSellsComparesWholeSecondExpiryAgainstFractionalNow() throws Exception {
    Path sqliteFile = Files.createTempFile("tradingpost-exp", ".db");
    TradingPostConfig config =
        TestConfigs.jdbc(
            DatabaseEngine.SQLITE,
            "jdbc:sqlite:" + sqliteFile.toAbsolutePath(),
            "",
            "",
            "tradingpost");
    try (Database database = new Database(config)) {
      MigrationRunner.migrate(database, config);
      OrderRepository orders = new OrderRepository(config);
      MarketRepository markets = new MarketRepository(config);
      UUID sellId = UUID.randomUUID();
      Instant expiresAt = Instant.parse("2026-08-19T12:00:00Z");
      Instant now = Instant.parse("2026-08-19T12:00:00.500Z");
      database.transaction(
          connection -> {
            markets.insert(connection, new Market("spawn", "Spawn", 100, 500));
            orders.insertSell(
                connection,
                new SellOrder(
                    sellId,
                    "spawn",
                    UUID.randomUUID(),
                    "minecraft:dirt",
                    new byte[] {1},
                    "fp",
                    1,
                    1,
                    new BigDecimal("1.00"),
                    SellOrderMode.NORMAL,
                    OrderStatus.ACTIVE,
                    expiresAt,
                    expiresAt.minusSeconds(3600)));
            return null;
          });
      List<SellOrder> expired =
          database.transaction(connection -> orders.expiredSells(connection, now, 10));
      assertEquals(1, expired.size());
      assertEquals(sellId, expired.getFirst().id());
    } finally {
      Files.deleteIfExists(sqliteFile);
    }
  }

  @Test
  void activateSellWithConfigSchemaTradingpost() throws Exception {
    Path sqliteFile = Files.createTempFile("tradingpost-activate", ".db");
    TradingPostConfig config =
        TestConfigs.jdbc(
            DatabaseEngine.SQLITE,
            "jdbc:sqlite:" + sqliteFile.toAbsolutePath(),
            "",
            "",
            "tradingpost");
    try (Database database = new Database(config)) {
      MigrationRunner.migrate(database, config);
      OrderRepository orders = new OrderRepository(config);
      MarketRepository markets = new MarketRepository(config);
      UUID sellId = UUID.randomUUID();
      Instant now = Instant.parse("2026-08-19T12:00:00Z");
      database.transaction(
          connection -> {
            markets.insert(connection, new Market("spawn", "Spawn", 100, 500));
            orders.insertSell(
                connection,
                new SellOrder(
                    sellId,
                    "spawn",
                    UUID.randomUUID(),
                    "minecraft:stone",
                    new byte[] {1},
                    "fp",
                    1,
                    1,
                    new BigDecimal("1.00"),
                    SellOrderMode.NORMAL,
                    OrderStatus.CREATING,
                    now.plusSeconds(3600),
                    now));
            orders.activateSell(connection, sellId);
            return null;
          });
      database.transaction(
          connection -> {
            SellOrder loaded = orders.findSell(connection, sellId, false).orElseThrow();
            assertEquals(OrderStatus.ACTIVE, loaded.status());
            return null;
          });
    } finally {
      Files.deleteIfExists(sqliteFile);
    }
  }
}
