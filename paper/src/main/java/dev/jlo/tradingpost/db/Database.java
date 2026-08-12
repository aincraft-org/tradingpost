package dev.jlo.tradingpost.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.jlo.tradingpost.config.TradingPostConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class Database implements AutoCloseable {
    private final HikariDataSource dataSource;

    public Database(TradingPostConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.maximumPoolSize());
        hikari.setPoolName("TradingPost");
        this.dataSource = new HikariDataSource(hikari);
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    public <T> T transaction(TransactionCallback<T> callback) {
        Objects.requireNonNull(callback, "callback");
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                T value = callback.execute(connection);
                connection.commit();
                return value;
            } catch (Exception failure) {
                connection.rollback();
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("TradingPost transaction failed", failure);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("TradingPost database unavailable", failure);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
