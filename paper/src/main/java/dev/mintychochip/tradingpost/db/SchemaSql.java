package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.DatabaseEngine;
import java.util.ArrayList;
import java.util.List;

final class SchemaSql {
  private SchemaSql() {}

  static List<String> v1(SqlDialect sql) {
    List<String> statements = new ArrayList<>();
    if (sql.engine() == DatabaseEngine.POSTGRESQL) {
      statements.add("CREATE SCHEMA IF NOT EXISTS " + sql.schema());
    }
    Types t = Types.of(sql);
    statements.add(
        "CREATE TABLE IF NOT EXISTS "
            + sql.table("schema_version")
            + " (version "
            + t.integer
            + " PRIMARY KEY, applied_at "
            + t.ts
            + " NOT NULL"
            + t.defaultNow
            + ")");
    statements.add(markets(sql, t));
    statements.add(tradingPosts(sql, t));
    statements.add(sellOrders(sql, t));
    statements.add(buyOrders(sql, t));
    statements.add(fills(sql, t));
    statements.add(settlements(sql, t));
    statements.add(mailboxItems(sql, t));
    statements.add(reviewQueue(sql, t));
    statements.addAll(v1Indexes(sql));
    return statements;
  }

  static List<String> v3CreateOperations(SqlDialect sql) {
    Types t = Types.of(sql);
    return List.of(
        "CREATE TABLE IF NOT EXISTS "
            + sql.table("sell_now_operations")
            + " (operation_id "
            + t.uuid
            + " PRIMARY KEY, sell_order_id "
            + t.uuid
            + " NOT NULL UNIQUE REFERENCES "
            + sql.table("sell_orders")
            + "(id), market_name "
            + t.keyed
            + " NOT NULL REFERENCES "
            + sql.table("markets")
            + "(name), seller "
            + t.uuid
            + " NOT NULL, source_item_blob "
            + t.blob
            + " NOT NULL, source_fingerprint "
            + t.keyed
            + " NOT NULL, original_quantity "
            + t.integer
            + " NOT NULL CHECK (original_quantity > 0), state "
            + t.status
            + " NOT NULL CHECK (state IN ('RESERVED', 'SETTLING', 'COMPLETED', 'FAILED', 'REVIEW')), failure_detail "
            + t.body
            + ", created_at "
            + t.ts
            + " NOT NULL"
            + t.defaultNow
            + ", updated_at "
            + t.ts
            + " NOT NULL"
            + t.defaultNow
            + ")");
  }

  private static String markets(SqlDialect sql, Types t) {
    return "CREATE TABLE IF NOT EXISTS "
        + sql.table("markets")
        + " (name "
        + t.keyed
        + " PRIMARY KEY, display_name "
        + t.keyed
        + " NOT NULL, fee_bps "
        + t.integer
        + " NOT NULL CHECK (fee_bps BETWEEN 0 AND 10000), tax_bps "
        + t.integer
        + " NOT NULL CHECK (tax_bps BETWEEN 0 AND 10000), created_at "
        + t.ts
        + " NOT NULL"
        + t.defaultNow
        + ")";
  }

  private static String tradingPosts(SqlDialect sql, Types t) {
    return "CREATE TABLE IF NOT EXISTS "
        + sql.table("trading_posts")
        + " (id "
        + t.uuid
        + " PRIMARY KEY, market_name "
        + t.keyed
        + " NOT NULL REFERENCES "
        + sql.table("markets")
        + "(name), world "
        + t.keyed
        + " NOT NULL, x "
        + t.integer
        + " NOT NULL, y "
        + t.integer
        + " NOT NULL, z "
        + t.integer
        + " NOT NULL, created_at "
        + t.ts
        + " NOT NULL"
        + t.defaultNow
        + ", UNIQUE (world, x, y, z))";
  }

  private static String sellOrders(SqlDialect sql, Types t) {
    return "CREATE TABLE IF NOT EXISTS "
        + sql.table("sell_orders")
        + " (id "
        + t.uuid
        + " PRIMARY KEY, market_name "
        + t.keyed
        + " NOT NULL REFERENCES "
        + sql.table("markets")
        + "(name), seller "
        + t.uuid
        + " NOT NULL, material "
        + t.keyed
        + " NOT NULL, item_blob "
        + t.blob
        + " NOT NULL, fingerprint "
        + t.keyed
        + " NOT NULL, quantity "
        + t.integer
        + " NOT NULL CHECK (quantity > 0), quantity_remaining "
        + t.integer
        + " NOT NULL CHECK (quantity_remaining >= 0), unit_price "
        + t.numeric
        + " NOT NULL CHECK (unit_price > 0), mode "
        + t.status
        + " NOT NULL CHECK (mode IN ('NORMAL', 'INSTANT')), status "
        + t.status
        + " NOT NULL CHECK (status IN ('CREATING', 'ACTIVE', 'FILLED', 'CANCELED', 'EXPIRED', 'VOIDED')), expires_at "
        + t.ts
        + " NOT NULL, created_at "
        + t.ts
        + " NOT NULL"
        + t.defaultNow
        + ", CHECK (quantity_remaining <= quantity))";
  }

  private static String buyOrders(SqlDialect sql, Types t) {
    return "CREATE TABLE IF NOT EXISTS "
        + sql.table("buy_orders")
        + " (id "
        + t.uuid
        + " PRIMARY KEY, market_name "
        + t.keyed
        + " NOT NULL REFERENCES "
        + sql.table("markets")
        + "(name), buyer "
        + t.uuid
        + " NOT NULL, material "
        + t.keyed
        + " NOT NULL, template_fingerprint "
        + t.keyed
        + ", quantity "
        + t.integer
        + " NOT NULL CHECK (quantity > 0), quantity_remaining "
        + t.integer
        + " NOT NULL CHECK (quantity_remaining >= 0), unit_price "
        + t.numeric
        + " NOT NULL CHECK (unit_price > 0), escrow_reserved "
        + t.numeric
        + " NOT NULL CHECK (escrow_reserved >= 0), status "
        + t.status
        + " NOT NULL CHECK (status IN ('CREATING', 'OPEN', 'FILLED', 'CANCELED', 'EXPIRED', 'VOIDED')), expires_at "
        + t.ts
        + " NOT NULL, created_at "
        + t.ts
        + " NOT NULL"
        + t.defaultNow
        + ", CHECK (quantity_remaining <= quantity))";
  }

  private static String fills(SqlDialect sql, Types t) {
    return "CREATE TABLE IF NOT EXISTS "
        + sql.table("fills")
        + " (fill_id "
        + t.uuid
        + " PRIMARY KEY, market_name "
        + t.keyed
        + " NOT NULL REFERENCES "
        + sql.table("markets")
        + "(name), sell_order_id "
        + t.uuid
        + " NOT NULL REFERENCES "
        + sql.table("sell_orders")
        + "(id), buy_order_id "
        + t.uuid
        + " NOT NULL REFERENCES "
        + sql.table("buy_orders")
        + "(id), quantity "
        + t.integer
        + " NOT NULL CHECK (quantity > 0), unit_price "
        + t.numeric
        + " NOT NULL CHECK (unit_price > 0), item_blob "
        + t.blob
        + " NOT NULL, remaining_item_blob "
        + t.blob
        + " NOT NULL, status "
        + t.status
        + " NOT NULL CHECK (status IN ('RESERVED', 'MONEY_SETTLED', 'DELIVERED', 'VOIDED')), created_at "
        + t.ts
        + " NOT NULL"
        + t.defaultNow
        + ")";
  }

  private static String settlements(SqlDialect sql, Types t) {
    return "CREATE TABLE IF NOT EXISTS "
        + sql.table("settlements")
        + " (id "
        + t.uuid
        + " PRIMARY KEY, kind "
        + t.status
        + " NOT NULL CHECK (kind IN ('BUY_ESCROW', 'LISTING_FEE', 'MATCH_SETTLEMENT', 'REFUND', 'FEE_REFUND')), idempotency_key "
        + t.keyed
        + " NOT NULL UNIQUE, fill_id "
        + t.uuid
        + " UNIQUE REFERENCES "
        + sql.table("fills")
        + "(fill_id), order_id "
        + t.uuid
        + ", amount "
        + t.numeric
        + " NOT NULL CHECK (amount > 0), state "
        + t.status
        + " NOT NULL CHECK (state IN ('RESERVED', 'MONEY_SETTLED', 'DELIVERED', 'FAILED')), attempts "
        + t.integer
        + " NOT NULL DEFAULT 0 CHECK (attempts >= 0), last_error "
        + t.body
        + ", lease_owner "
        + t.keyed
        + ", lease_until "
        + t.ts
        + ", created_at "
        + t.ts
        + " NOT NULL"
        + t.defaultNow
        + ", updated_at "
        + t.ts
        + " NOT NULL"
        + t.defaultNow
        + ", CHECK ((lease_owner IS NULL) = (lease_until IS NULL)))";
  }

  private static String mailboxItems(SqlDialect sql, Types t) {
    return "CREATE TABLE IF NOT EXISTS "
        + sql.table("mailbox_items")
        + " (id "
        + t.uuid
        + " PRIMARY KEY, market_name "
        + t.keyed
        + " NOT NULL REFERENCES "
        + sql.table("markets")
        + "(name), owner "
        + t.uuid
        + " NOT NULL, item_blob "
        + t.blob
        + " NOT NULL, fingerprint "
        + t.keyed
        + " NOT NULL, reason "
        + t.keyed
        + " NOT NULL, state "
        + t.status
        + " NOT NULL CHECK (state IN ('UNCLAIMED', 'CLAIMING', 'CLAIMED', 'REVIEW')), settlement_id "
        + t.uuid
        + " UNIQUE, created_at "
        + t.ts
        + " NOT NULL"
        + t.defaultNow
        + ", claimed_at "
        + t.ts
        + ")";
  }

  private static String reviewQueue(SqlDialect sql, Types t) {
    return "CREATE TABLE IF NOT EXISTS "
        + sql.table("review_queue")
        + " (id "
        + t.uuid
        + " PRIMARY KEY, player "
        + t.uuid
        + ", fingerprint "
        + t.keyed
        + ", detail "
        + t.json
        + " NOT NULL, resolved_by "
        + t.uuid
        + ", resolved_at "
        + t.ts
        + ", created_at "
        + t.ts
        + " NOT NULL"
        + t.defaultNow
        + ")";
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
    return "CREATE "
        + (unique ? "UNIQUE " : "")
        + "INDEX "
        + ifNotExists
        + name
        + " ON "
        + sql.table(table)
        + " ("
        + columns
        + ")";
  }

  static String postgresPartialUnique(SqlDialect sql, String name, String table, String columns) {
    return "CREATE UNIQUE INDEX IF NOT EXISTS "
        + name
        + " ON "
        + sql.table(table)
        + " ("
        + columns
        + ") WHERE entity_uuid IS NOT NULL";
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
