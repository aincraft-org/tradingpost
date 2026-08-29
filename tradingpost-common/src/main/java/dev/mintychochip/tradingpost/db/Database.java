package dev.mintychochip.tradingpost.db;

import com.zaxxer.hikari.HikariConfig;
import dev.mintychochip.tradingpost.config.DatabaseEngine;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.aincraft.db.sql.SqlDatabase;
import org.jdbi.v3.core.Handle;

/**
 * TradingPost's raw JDBC database boundary.
 *
 * <p>The pool and Jdbi/Flyway lifecycle are owned by Utilities SQL. TradingPost's existing
 * dialect-aware repositories deliberately continue to receive JDBC connections so their SQL,
 * encodings, and transaction boundaries remain unchanged.
 */
public final class Database implements AutoCloseable {
  private final SqlDatabase sqlDatabase;
  private final SqlDialect dialect;

  public Database(TradingPostConfig config) {
    Objects.requireNonNull(config, "config");
    this.dialect = SqlDialect.from(config);
    HikariConfig hikari = new HikariConfig();
    hikari.setJdbcUrl(config.jdbcUrl());
    hikari.setUsername(config.username());
    hikari.setPassword(config.password());
    hikari.setPoolName("TradingPost");
    if (config.engine() == DatabaseEngine.SQLITE) {
      hikari.setMaximumPoolSize(1);
      hikari.setConnectionInitSql("PRAGMA foreign_keys = ON");
    } else {
      hikari.setMaximumPoolSize(config.maximumPoolSize());
    }
    /*
     * Do not scan TradingPost's migration resources here. The explicit empty location keeps
     * Utilities SQL's Flyway lifecycle inert while MigrationRunner remains the sole owner of
     * the versioned TradingPost schema (schema_version 1..4); allowing Utilities SQL to scan a
     * second migration directory would create a separate flyway_schema_history and could
     * reorder or replay the existing dialect-specific migrations.
     */
    this.sqlDatabase = SqlDatabase.create(hikari, "classpath:tradingpost-no-runtime-migrations");
  }

  public SqlDialect dialect() {
    return dialect;
  }

  /**
   * Opens a raw JDBC connection backed by the Utilities SQL Jdbi handle.
   *
   * <p>The returned connection closes its owning handle, rather than bypassing Jdbi's resource
   * lifecycle. This keeps the legacy connection API source-compatible without maintaining a second
   * Hikari pool.
   */
  public Connection connection() throws SQLException {
    Handle handle = openHandle();
    try {
      return handleBoundConnection(handle);
    } catch (RuntimeException failure) {
      try {
        handle.close();
      } catch (RuntimeException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw connectionFailure(failure);
    }
  }

  public <T> T transaction(TransactionCallback<T> callback) {
    Objects.requireNonNull(callback, "callback");
    Handle handle;
    try {
      handle = openHandle();
    } catch (SQLException failure) {
      throw new IllegalStateException("TradingPost database unavailable", failure);
    }
    try {
      handle.begin();
      try {
        T value = callback.execute(handle.getConnection());
        handle.commit();
        return value;
      } catch (Exception failure) {
        handle.rollback();
        if (failure instanceof RuntimeException runtime) {
          throw runtime;
        }
        throw new IllegalStateException("TradingPost transaction failed", failure);
      } catch (Error failure) {
        try {
          handle.rollback();
        } catch (RuntimeException rollbackFailure) {
          failure.addSuppressed(rollbackFailure);
        }
        throw failure;
      }
    } finally {
      handle.close();
    }
  }

  private Handle openHandle() throws SQLException {
    try {
      return sqlDatabase.jdbi().open();
    } catch (RuntimeException failure) {
      throw connectionFailure(failure);
    }
  }

  private static Connection handleBoundConnection(Handle handle) {
    Connection connection = handle.getConnection();
    InvocationHandler invocationHandler =
        new InvocationHandler() {
          private boolean closed;

          @Override
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("close") && method.getParameterCount() == 0) {
              if (!closed) {
                closed = true;
                closeHandle(handle, connection);
              }
              return null;
            }
            if (method.getName().equals("isClosed") && method.getParameterCount() == 0) {
              return closed || connection.isClosed();
            }
            if (method.getName().equals("abort") && method.getParameterCount() == 1) {
              Throwable failure = null;
              try {
                method.invoke(connection, args);
              } catch (InvocationTargetException exception) {
                failure = exception.getCause();
              } catch (Throwable exception) {
                failure = exception;
              }
              if (!closed) {
                closed = true;
                try {
                  closeHandle(handle, connection);
                } catch (Throwable closeFailure) {
                  if (failure == null) {
                    failure = closeFailure;
                  } else {
                    failure.addSuppressed(closeFailure);
                  }
                }
              }
              if (failure != null) {
                throw failure;
              }
              return null;
            }
            if (method.getDeclaringClass() == Object.class) {
              return switch (method.getName()) {
                case "toString" -> "UtilitiesSqlConnection[" + connection + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> method.invoke(connection, args);
              };
            }
            try {
              return method.invoke(connection, args);
            } catch (InvocationTargetException failure) {
              throw failure.getCause();
            }
          }
        };
    return (Connection)
        Proxy.newProxyInstance(
            Database.class.getClassLoader(), new Class<?>[] {Connection.class}, invocationHandler);
  }

  private static void closeHandle(Handle handle, Connection connection) throws Throwable {
    Throwable failure = null;
    try {
      if (!connection.isClosed() && !connection.getAutoCommit()) {
        connection.rollback();
        connection.setAutoCommit(true);
      }
    } catch (Throwable cleanupFailure) {
      failure = cleanupFailure;
    }
    try {
      handle.close();
    } catch (Throwable closeFailure) {
      if (failure == null) {
        failure = closeFailure;
      } else {
        failure.addSuppressed(closeFailure);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private static SQLException connectionFailure(RuntimeException failure) {
    if (failure.getCause() instanceof SQLException sqlFailure) {
      return sqlFailure;
    }
    return new SQLException("TradingPost database unavailable", failure);
  }

  @Override
  public void close() {
    sqlDatabase.close();
  }
}
