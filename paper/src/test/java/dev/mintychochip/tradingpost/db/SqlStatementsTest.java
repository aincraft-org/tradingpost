package dev.mintychochip.tradingpost.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.tradingpost.config.DatabaseEngine;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SqlStatementsTest {
  @Test
  void loadQualifiesThroughDialectTable() {
    SqlDialect postgres = new SqlDialect(DatabaseEngine.POSTGRESQL, "tradingpost");
    String pg = SqlStatements.load("orders/insert-sell.sql", postgres);
    assertTrue(pg.startsWith("INSERT INTO " + postgres.table("sell_orders")));
    assertTrue(pg.contains("tradingpost.sell_orders"));

    SqlDialect sqlite = new SqlDialect(DatabaseEngine.SQLITE, "tradingpost");
    String lite = SqlStatements.load("orders/insert-sell.sql", sqlite);
    assertTrue(lite.startsWith("INSERT INTO " + sqlite.table("sell_orders")));
    assertTrue(lite.startsWith("INSERT INTO sell_orders"));
    assertFalse(lite.contains("tradingpost."));
  }

  @Test
  void transitionSqlQualifiesTableThroughDialect() {
    SqlDialect sqlite = new SqlDialect(DatabaseEngine.SQLITE, "tradingpost");
    String expanded =
        SqlStatements.load("orders/transition.sql", sqlite)
            .replace("{qualifiedTable}", sqlite.table("sell_orders"));
    assertTrue(expanded.startsWith("UPDATE sell_orders "));
    assertFalse(expanded.contains("tradingpost."));
    assertEquals("sell_orders", sqlite.table("sell_orders"));
  }

  @Test
  void loadRejectsMissingResource() {
    assertThrows(IllegalStateException.class, () -> SqlStatements.load("missing.sql"));
  }

  @Test
  void sqliteInstantsAreLexicographicallyOrdered() {
    SqlDialect sqlite = new SqlDialect(DatabaseEngine.SQLITE, "tradingpost");
    String whole = sqlite.formatInstant(Instant.parse("2026-08-19T12:00:00Z"));
    String later = sqlite.formatInstant(Instant.parse("2026-08-19T12:00:00.500Z"));
    assertEquals("2026-08-19T12:00:00.000000Z", whole);
    assertEquals("2026-08-19T12:00:00.500000Z", later);
    assertTrue(whole.compareTo(later) < 0);
  }
}
