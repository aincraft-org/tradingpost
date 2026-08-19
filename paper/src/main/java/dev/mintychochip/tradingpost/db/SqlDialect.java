package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.DatabaseEngine;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/** Engine-specific SQL fragments and JDBC bindings for TradingPost persistence. */
public final class SqlDialect {
  /** Fixed-fraction UTC so SQLite TEXT comparisons of instants are lexicographic. */
  static final DateTimeFormatter SQLITE_INSTANT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

  private final DatabaseEngine engine;
  private final String schema;

  public SqlDialect(DatabaseEngine engine, String schema) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.schema = Objects.requireNonNull(schema, "schema");
    if (!schema.matches("[a-z_][a-z0-9_]{0,62}")) {
      throw new IllegalArgumentException("invalid database schema name");
    }
  }

  public static SqlDialect from(TradingPostConfig config) {
    Objects.requireNonNull(config, "config");
    return new SqlDialect(config.engine(), config.schema());
  }

  public DatabaseEngine engine() {
    return engine;
  }

  public String schema() {
    return schema;
  }

  public String table(String name) {
    Objects.requireNonNull(name, "name");
    if (engine == DatabaseEngine.SQLITE) {
      return name;
    }
    return schema + "." + name;
  }

  public String forUpdate(boolean lock) {
    if (!lock || engine == DatabaseEngine.SQLITE) {
      return "";
    }
    return " FOR UPDATE";
  }

  public String skipLocked() {
    if (engine == DatabaseEngine.SQLITE) {
      return "";
    }
    return " FOR UPDATE SKIP LOCKED";
  }

  public String now() {
    return switch (engine) {
      case POSTGRESQL -> "clock_timestamp()";
      case SQLITE -> "strftime('%Y-%m-%dT%H:%M:%f000Z','now')";
      case MYSQL, MARIADB -> "CURRENT_TIMESTAMP(6)";
    };
  }

  public String formatInstant(Instant value) {
    Objects.requireNonNull(value, "value");
    if (engine == DatabaseEngine.SQLITE) {
      return SQLITE_INSTANT.format(value);
    }
    return value.toString();
  }

  public String defaultNow() {
    return switch (engine) {
      case SQLITE -> " DEFAULT (" + now() + ")";
      default -> " DEFAULT " + now();
    };
  }

  public String limitOffset() {
    return " LIMIT ? OFFSET ?";
  }

  public String jsonPlaceholder() {
    return engine == DatabaseEngine.POSTGRESQL ? "?::jsonb" : "?";
  }

  public String uuidType() {
    return switch (engine) {
      case POSTGRESQL -> "uuid";
      case SQLITE -> "text";
      case MYSQL, MARIADB -> "char(36)";
    };
  }

  public String textType() {
    return "text";
  }

  public String intType() {
    return switch (engine) {
      case MYSQL, MARIADB -> "int";
      default -> "integer";
    };
  }

  public String numericType() {
    return switch (engine) {
      case MYSQL, MARIADB -> "decimal(38,18)";
      default -> "numeric";
    };
  }

  public String binaryType() {
    return switch (engine) {
      case POSTGRESQL -> "bytea";
      case MYSQL, MARIADB -> "mediumblob";
      case SQLITE -> "blob";
    };
  }

  public String timestampType() {
    return switch (engine) {
      case POSTGRESQL -> "timestamptz";
      case MYSQL, MARIADB -> "datetime(6)";
      case SQLITE -> "text";
    };
  }

  public String jsonType() {
    return switch (engine) {
      case POSTGRESQL -> "jsonb";
      case MYSQL, MARIADB -> "json";
      case SQLITE -> "text";
    };
  }

  public boolean supportsIfNotExistsIndex() {
    return engine != DatabaseEngine.MYSQL;
  }

  public String insertIgnore(
      String qualifiedTable, String columns, String values, String conflict) {
    String insert = "INSERT INTO " + qualifiedTable + columns + " VALUES" + values;
    return switch (engine) {
      case MYSQL, MARIADB -> "INSERT IGNORE INTO " + qualifiedTable + columns + " VALUES" + values;
      default -> insert + " ON CONFLICT (" + conflict + ") DO NOTHING";
    };
  }

  public String metadataCatalog() {
    return switch (engine) {
      case MYSQL, MARIADB -> schema;
      default -> null;
    };
  }

  public String metadataSchema() {
    return switch (engine) {
      case POSTGRESQL -> schema;
      default -> null;
    };
  }

  public void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
    if (value == null) {
      statement.setObject(index, null);
      return;
    }
    if (engine == DatabaseEngine.POSTGRESQL) {
      statement.setObject(index, value);
    } else {
      statement.setString(index, value.toString());
    }
  }

  public UUID getUuid(ResultSet rows, int index) throws SQLException {
    if (engine == DatabaseEngine.POSTGRESQL) {
      Object value = rows.getObject(index);
      if (value == null) {
        return null;
      }
      if (value instanceof UUID uuid) {
        return uuid;
      }
      return UUID.fromString(value.toString());
    }
    String value = rows.getString(index);
    return value == null || value.isBlank() ? null : UUID.fromString(value);
  }

  public void setInstant(PreparedStatement statement, int index, Instant value)
      throws SQLException {
    if (value == null) {
      statement.setObject(index, null);
      return;
    }
    switch (engine) {
      case POSTGRESQL ->
          statement.setObject(index, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
      case SQLITE -> statement.setString(index, formatInstant(value));
      case MYSQL, MARIADB -> statement.setTimestamp(index, Timestamp.from(value));
    }
  }

  public Instant getInstant(ResultSet rows, int index) throws SQLException {
    return switch (engine) {
      case POSTGRESQL -> {
        OffsetDateTime value = rows.getObject(index, OffsetDateTime.class);
        yield value == null ? null : value.toInstant();
      }
      case SQLITE -> {
        String value = rows.getString(index);
        if (value == null || value.isBlank()) {
          yield null;
        }
        yield parseInstant(value);
      }
      case MYSQL, MARIADB -> {
        Timestamp value = rows.getTimestamp(index);
        yield value == null ? null : value.toInstant();
      }
    };
  }

  private static Instant parseInstant(String value) {
    String trimmed = value.trim().replace(' ', 'T');
    if (trimmed.indexOf('T') < 0) {
      trimmed = trimmed + "T00:00:00Z";
    } else if (!trimmed.endsWith("Z")
        && trimmed.indexOf('+') < 0
        && trimmed.lastIndexOf('-') <= 9) {
      trimmed = trimmed + "Z";
    }
    if (trimmed.endsWith("Z") && trimmed.charAt(trimmed.length() - 2) == '.') {
      trimmed = trimmed.substring(0, trimmed.length() - 2) + "Z";
    }
    return Instant.parse(trimmed);
  }
}
