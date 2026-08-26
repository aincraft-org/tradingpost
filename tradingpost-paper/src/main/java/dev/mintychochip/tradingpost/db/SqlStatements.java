package dev.mintychochip.tradingpost.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlStatements {
  private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();
  private static final Pattern QUALIFIED_TABLE =
      Pattern.compile("\\{schema}\\.([A-Za-z_][A-Za-z0-9_]*)");

  private SqlStatements() {}

  public static String load(String name) {
    Objects.requireNonNull(name, "name");
    if (name.isBlank() || name.startsWith("/") || name.contains("..")) {
      throw new IllegalArgumentException("invalid SQL resource name");
    }
    return CACHE.computeIfAbsent(name, SqlStatements::read);
  }

  public static String load(String name, SqlDialect dialect) {
    Objects.requireNonNull(dialect, "dialect");
    String sql = load(name);
    Matcher matcher = QUALIFIED_TABLE.matcher(sql);
    StringBuilder expanded = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(
          expanded, Matcher.quoteReplacement(dialect.table(matcher.group(1))));
    }
    matcher.appendTail(expanded);
    return expanded.toString().replace("{schema}", dialect.schema());
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
