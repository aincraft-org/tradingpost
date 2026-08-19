package dev.mintychochip.tradingpost.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SqlStatementsTest {
  @Test
  void loadReplacesSchemaPlaceholder() {
    assertTrue(SqlStatements.load("orders/insert-sell.sql").contains("{schema}"));
    String sql = SqlStatements.load("orders/insert-sell.sql", "tradingpost");
    assertTrue(sql.startsWith("INSERT INTO tradingpost.sell_orders"));
  }

  @Test
  void loadRejectsMissingResource() {
    assertThrows(IllegalStateException.class, () -> SqlStatements.load("missing.sql"));
  }

  @Test
  void hikariConnectionRunsRepositorySqlFromResources() throws Exception {
    Path sqliteFile = Files.createTempFile("tradingpost-sql", ".db");
    TradingPostConfig config =
        TestConfigs.jdbc(
            DatabaseEngine.SQLITE, "jdbc:sqlite:" + sqliteFile.toAbsolutePath(), "", "", "main");
    try (Database database = new Database(config)) {
      MigrationRunner.migrate(database, config);
      MarketRepository markets = new MarketRepository(config);
      OrderRepository orders = new OrderRepository(config);
      UUID sellId = UUID.randomUUID();
      Instant now = Instant.parse("2026-08-19T12:00:00Z");
      SellOrder sell =
          new SellOrder(
              sellId,
              "spawn",
              UUID.randomUUID(),
              "minecraft:diamond",
              new byte[] {1, 2, 3},
              "fp",
              4,
              4,
              new BigDecimal("8.50"),
              SellOrderMode.NORMAL,
              OrderStatus.ACTIVE,
              now.plusSeconds(3600),
              now);
      try (Connection connection = database.connection()) {
        markets.insert(connection, new Market("spawn", "Spawn", 100, 500));
        orders.insertSell(connection, sell);
        SellOrder loaded = orders.findSell(connection, sellId, false).orElseThrow();
        assertEquals(sellId, loaded.id());
        assertEquals("minecraft:diamond", loaded.material());
        assertEquals(4, loaded.quantityRemaining());
        assertTrue(
            SqlStatements.load("orders/insert-sell.sql", SqlDialect.from(config))
                .startsWith("INSERT INTO sell_orders"));
      }
    } finally {
      Files.deleteIfExists(sqliteFile);
    }
  }
}
