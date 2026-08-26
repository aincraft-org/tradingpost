package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.DatabaseEngine;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MigrationRunner {
  private MigrationRunner() {}

  public static void migrate(Database database, TradingPostConfig config) {
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(config, "config");
    SqlDialect sql = SqlDialect.from(config);
    database.transaction(
        connection -> {
          apply(connection, sql, 1, SchemaSql.v1(sql));
          applyV2(connection, sql);
          applyV3(connection, sql);
          applyV4(connection, sql);
          return null;
        });
  }

  private static void apply(
      Connection connection, SqlDialect sql, int version, List<String> statements)
      throws SQLException {
    if (applied(connection, sql, version)) {
      return;
    }
    executeAll(connection, statements);
    recordVersion(connection, sql, version);
  }

  private static void applyV2(Connection connection, SqlDialect sql) throws SQLException {
    if (applied(connection, sql, 2)) {
      return;
    }
    addColumnIfMissing(connection, sql, "trading_posts", "entity_uuid", sql.uuidType());
    if (sql.engine() == DatabaseEngine.POSTGRESQL) {
      execute(
          connection,
          SchemaSql.postgresPartialUnique(
              sql, "trading_posts_entity_uuid_uq", "trading_posts", "entity_uuid"));
      execute(
          connection,
          SchemaSql.postgresPartialUnique(
              sql, "trading_posts_market_npc_uq", "trading_posts", "market_name"));
    } else {
      createIndexIfMissing(
          connection, sql, true, "trading_posts_entity_uuid_uq", "trading_posts", "entity_uuid");
      createIndexIfMissing(
          connection, sql, true, "trading_posts_market_npc_uq", "trading_posts", "market_name");
    }
    recordVersion(connection, sql, 2);
  }

  private static void applyV3(Connection connection, SqlDialect sql) throws SQLException {
    if (applied(connection, sql, 3)) {
      return;
    }
    executeAll(connection, SchemaSql.v3CreateOperations(sql));
    addColumnIfMissing(connection, sql, "fills", "operation_id", sql.uuidType());
    addColumnIfMissing(connection, sql, "settlements", "operation_id", sql.uuidType());
    createIndexIfMissing(
        connection,
        sql,
        false,
        "sell_now_operations_state_idx",
        "sell_now_operations",
        "state, updated_at");
    createIndexIfMissing(
        connection, sql, false, "fills_operation_idx", "fills", "operation_id, status, created_at");
    createIndexIfMissing(
        connection,
        sql,
        false,
        "settlements_operation_idx",
        "settlements",
        "operation_id, state, created_at");
    recordVersion(connection, sql, 3);
  }

  private static void applyV4(Connection connection, SqlDialect sql) throws SQLException {
    if (applied(connection, sql, 4)) {
      return;
    }
    execute(connection, SchemaSql.dropTable(sql, "mailbox_items"));
    recordVersion(connection, sql, 4);
  }

  private static void executeAll(Connection connection, List<String> statements)
      throws SQLException {
    for (String statement : statements) {
      execute(connection, statement);
    }
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static void recordVersion(Connection connection, SqlDialect sql, int version)
      throws SQLException {
    String resource =
        sql.engine() == DatabaseEngine.MYSQL || sql.engine() == DatabaseEngine.MARIADB
            ? "schema/insert-version-ignore.sql"
            : "schema/insert-version.sql";
    try (PreparedStatement statement =
        connection.prepareStatement(SqlStatements.load(resource, sql))) {
      statement.setInt(1, version);
      statement.executeUpdate();
    }
  }

  private static boolean applied(Connection connection, SqlDialect sql, int version)
      throws SQLException {
    if (!tableExists(connection, sql, "schema_version")) {
      return false;
    }
    try (PreparedStatement statement =
        connection.prepareStatement(SqlStatements.load("schema/select-version.sql", sql))) {
      statement.setInt(1, version);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next();
      }
    }
  }

  private static void addColumnIfMissing(
      Connection connection, SqlDialect sql, String table, String column, String definition)
      throws SQLException {
    if (columnExists(connection, sql, table, column)) {
      return;
    }
    execute(
        connection,
        SqlStatements.load("schema/alter-add-column.sql", sql)
            .replace("{qualifiedTable}", sql.table(table))
            .replace("{column}", column)
            .replace("{definition}", definition));
  }

  private static void createIndexIfMissing(
      Connection connection,
      SqlDialect sql,
      boolean unique,
      String name,
      String table,
      String columns)
      throws SQLException {
    if (indexExists(connection, sql, table, name)) {
      return;
    }
    execute(connection, SchemaSql.index(sql, unique, name, table, columns));
  }

  static boolean tableExists(Connection connection, SqlDialect sql, String table)
      throws SQLException {
    DatabaseMetaData metadata = connection.getMetaData();
    try (ResultSet tables =
        metadata.getTables(
            sql.metadataCatalog(), sql.metadataSchema(), table, new String[] {"TABLE"})) {
      if (tables.next()) {
        return true;
      }
    }
    try (ResultSet tables =
        metadata.getTables(
            sql.metadataCatalog(),
            sql.metadataSchema(),
            table.toUpperCase(Locale.ROOT),
            new String[] {"TABLE"})) {
      if (tables.next()) {
        return true;
      }
    }
    try (ResultSet tables =
        metadata.getTables(
            sql.metadataCatalog(),
            sql.metadataSchema(),
            table.toLowerCase(Locale.ROOT),
            new String[] {"TABLE"})) {
      return tables.next();
    }
  }

  static boolean columnExists(Connection connection, SqlDialect sql, String table, String column)
      throws SQLException {
    DatabaseMetaData metadata = connection.getMetaData();
    if (columnListed(metadata, sql, table, column)
        || columnListed(
            metadata, sql, table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))
        || columnListed(
            metadata, sql, table.toLowerCase(Locale.ROOT), column.toLowerCase(Locale.ROOT))) {
      return true;
    }
    return false;
  }

  private static boolean columnListed(
      DatabaseMetaData metadata, SqlDialect sql, String table, String column) throws SQLException {
    try (ResultSet columns =
        metadata.getColumns(sql.metadataCatalog(), sql.metadataSchema(), table, column)) {
      return columns.next();
    }
  }

  static boolean indexExists(Connection connection, SqlDialect sql, String table, String name)
      throws SQLException {
    DatabaseMetaData metadata = connection.getMetaData();
    return indexListed(metadata, sql, table, name)
        || indexListed(metadata, sql, table.toUpperCase(Locale.ROOT), name)
        || indexListed(metadata, sql, table.toLowerCase(Locale.ROOT), name);
  }

  private static boolean indexListed(
      DatabaseMetaData metadata, SqlDialect sql, String table, String name) throws SQLException {
    try (ResultSet indexes =
        metadata.getIndexInfo(sql.metadataCatalog(), sql.metadataSchema(), table, false, false)) {
      while (indexes.next()) {
        String indexName = indexes.getString("INDEX_NAME");
        if (indexName != null && indexName.equalsIgnoreCase(name)) {
          return true;
        }
      }
    }
    return false;
  }
}
