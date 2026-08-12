package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.TradingPostConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class MigrationRunner {
    private MigrationRunner() {
    }
    public static void migrate(Database database, TradingPostConfig config) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(config, "config");
        database.transaction(connection -> {
            String schema = config.schema();
            if (!schema.matches("[a-z_][a-z0-9_]{0,62}")) {
                throw new IllegalArgumentException("invalid PostgreSQL schema name");
            }
            apply(connection, schema, 1, "/db/migration/V1__tradingpost.sql");
            apply(connection, schema, 2, "/db/migration/V2__villager_trading_posts.sql");
            return null;
        });
    }

    private static void apply(Connection connection, String schema, int version, String resource) throws SQLException {
        if (applied(connection, schema, version)) return;
        String sql = read(resource).replace("{{schema}}", schema);
        try (Statement statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                String trimmed = command.trim();
                if (!trimmed.isEmpty()) statement.execute(trimmed);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + schema + ".schema_version(version) VALUES (?) ON CONFLICT (version) DO NOTHING")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }

    private static boolean applied(Connection connection, String schema, int version) throws SQLException {
        String table = schema + ".schema_version";
        try (PreparedStatement exists = connection.prepareStatement("SELECT to_regclass(?)")) {
            exists.setString(1, table);
            try (var rows = exists.executeQuery()) {
                if (!rows.next() || rows.getString(1) == null) return false;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM " + schema + ".schema_version WHERE version=?")) {
            statement.setInt(1, version);
            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static String read(String resource) {
        try (InputStream input = MigrationRunner.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("missing migration resource: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read migration resource: " + resource, failure);
        }
    }
}
