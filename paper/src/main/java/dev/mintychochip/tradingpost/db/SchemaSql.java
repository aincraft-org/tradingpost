package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.DatabaseEngine;
import java.util.ArrayList;
import java.util.List;

final class SchemaSql {
  private SchemaSql() {}

  static List<String> v1(SqlDialect sql) {
    List<String> statements = new ArrayList<>();
    if (sql.engine() == DatabaseEngine.POSTGRESQL) {
      statements.add(SqlStatements.load("schema/create-schema.sql", sql));
    }
    statements.add(render("schema/v1-schema-version.sql", sql));
    statements.add(render("schema/v1-markets.sql", sql));
    statements.add(render("schema/v1-trading-posts.sql", sql));
    statements.add(render("schema/v1-sell-orders.sql", sql));
    statements.add(render("schema/v1-buy-orders.sql", sql));
    statements.add(render("schema/v1-fills.sql", sql));
    statements.add(render("schema/v1-settlements.sql", sql));
    statements.add(render("schema/v1-mailbox-items.sql", sql));
    statements.add(render("schema/v1-review-queue.sql", sql));
    statements.addAll(v1Indexes(sql));
    return statements;
  }

  static List<String> v3CreateOperations(SqlDialect sql) {
    return List.of(render("schema/v3-sell-now-operations.sql", sql));
  }

  private static List<String> v1Indexes(SqlDialect sql) {
    return List.of(
        index(
            sql,
            false,
            "sell_orders_market_book_idx",
            "sell_orders",
            "market_name, status, unit_price, created_at"),
        index(
            sql,
            false,
            "buy_orders_market_book_idx",
            "buy_orders",
            "market_name, status, unit_price DESC, created_at"),
        index(sql, false, "sell_orders_expiry_idx", "sell_orders", "status, expires_at"),
        index(sql, false, "buy_orders_expiry_idx", "buy_orders", "status, expires_at"),
        index(
            sql,
            false,
            "settlements_recovery_idx",
            "settlements",
            "state, lease_until, updated_at"),
        index(
            sql,
            false,
            "mailbox_owner_idx",
            "mailbox_items",
            "owner, market_name, state, created_at"));
  }

  static String index(SqlDialect sql, boolean unique, String name, String table, String columns) {
    String ifNotExists = sql.supportsIfNotExistsIndex() ? "IF NOT EXISTS " : "";
    return SqlStatements.load("schema/index.sql", sql)
        .replace("{unique}", unique ? "UNIQUE " : "")
        .replace("{ifNotExists}", ifNotExists)
        .replace("{name}", name)
        .replace("{qualifiedTable}", sql.table(table))
        .replace("{columns}", columns);
  }

  static String postgresPartialUnique(SqlDialect sql, String name, String table, String columns) {
    return SqlStatements.load("schema/postgres-partial-unique.sql", sql)
        .replace("{name}", name)
        .replace("{qualifiedTable}", sql.table(table))
        .replace("{columns}", columns);
  }

  static String keyedText(SqlDialect sql) {
    return switch (sql.engine()) {
      case MYSQL, MARIADB -> "varchar(255)";
      default -> "text";
    };
  }

  static String shortText(SqlDialect sql) {
    return switch (sql.engine()) {
      case MYSQL, MARIADB -> "varchar(32)";
      default -> "text";
    };
  }

  private static String render(String name, SqlDialect sql) {
    Types t = Types.of(sql);
    return SqlStatements.load(name, sql)
        .replace("{uuid}", t.uuid)
        .replace("{keyed}", t.keyed)
        .replace("{status}", t.status)
        .replace("{body}", t.body)
        .replace("{integer}", t.integer)
        .replace("{numeric}", t.numeric)
        .replace("{blob}", t.blob)
        .replace("{ts}", t.ts)
        .replace("{json}", t.json)
        .replace("{defaultNow}", t.defaultNow);
  }

  private record Types(
      String uuid,
      String keyed,
      String status,
      String body,
      String integer,
      String numeric,
      String blob,
      String ts,
      String json,
      String defaultNow) {
    static Types of(SqlDialect sql) {
      return new Types(
          sql.uuidType(),
          keyedText(sql),
          shortText(sql),
          sql.textType(),
          sql.intType(),
          sql.numericType(),
          sql.binaryType(),
          sql.timestampType(),
          sql.jsonType(),
          sql.defaultNow());
    }
  }
}
