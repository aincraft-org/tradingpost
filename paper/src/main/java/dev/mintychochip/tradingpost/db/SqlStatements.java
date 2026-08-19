package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.DatabaseEngine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SqlStatements {
  private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

  private SqlStatements() {}

  public static String load(String name) {
    Objects.requireNonNull(name, "name");
    if (name.isBlank() || name.startsWith("/") || name.contains("..")) {
      throw new IllegalArgumentException("invalid SQL resource name");
    }
    return CACHE.computeIfAbsent(name, SqlStatements::read);
  }

  public static String load(String name, String schema) {
    Objects.requireNonNull(schema, "schema");
    return load(name).replace("{schema}", schema);
  }

  public static String load(String name, SqlDialect dialect) {
    Objects.requireNonNull(dialect, "dialect");
    if (dialect.engine() == DatabaseEngine.SQLITE) {
      return load(name).replace("{schema}.", "");
    }
    return load(name, dialect.schema());
  }

  private static String read(String name) {
    String path = "/sql/" + name;
    try (InputStream in = SqlStatements.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("missing SQL resource: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (IOException failure) {
      throw new IllegalStateException("failed to load SQL resource: " + path, failure);
    }
  }
}
