package dev.mintychochip.tradingpost.config;

import java.util.Locale;
import java.util.Objects;

/** Persistence engines operators may select in plugin configuration. */
public enum DatabaseEngine {
  POSTGRESQL("postgresql"),
  MYSQL("mysql"),
  MARIADB("mariadb"),
  SQLITE("sqlite");

  private final String configName;

  DatabaseEngine(String configName) {
    this.configName = configName;
  }

  public String configName() {
    return configName;
  }

  public static DatabaseEngine parse(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("database.engine must not be blank");
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (DatabaseEngine engine : values()) {
      if (engine.configName.equals(normalized)) {
        return engine;
      }
    }
    throw new IllegalArgumentException(
        "database.engine must be postgresql, mysql, mariadb, or sqlite");
  }

  public boolean usesServerSchema() {
    return this != SQLITE;
  }

  public static DatabaseEngine from(String value) {
    return parse(Objects.requireNonNull(value, "engine"));
  }
}
