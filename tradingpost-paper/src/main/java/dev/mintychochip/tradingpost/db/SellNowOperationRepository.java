package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.domain.SellNowOperation;
import dev.mintychochip.tradingpost.domain.SellNowOperationState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SellNowOperationRepository {
  private final SqlDialect sql;

  public SellNowOperationRepository(SqlDialect sql) {
    this.sql = Objects.requireNonNull(sql, "sql");
  }

  public SellNowOperationRepository(TradingPostConfig config) {
    this(SqlDialect.from(config));
  }

  public String schema() {
    return sql.schema();
  }

  public void insert(Connection connection, SellNowOperation operation) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("sell-now/insert.sql"))) {
      sql.setUuid(statement, 1, operation.operationId());
      sql.setUuid(statement, 2, operation.sellOrderId());
      statement.setString(3, operation.marketName());
      sql.setUuid(statement, 4, operation.seller());
      statement.setBytes(5, operation.sourceItemBlob());
      statement.setString(6, operation.sourceFingerprint());
      statement.setInt(7, operation.originalQuantity());
      statement.setString(8, operation.state().name());
      statement.setString(9, operation.failureDetail());
      sql.setInstant(statement, 10, operation.createdAt());
      sql.setInstant(statement, 11, operation.updatedAt());
      statement.executeUpdate();
    }
  }

  public Optional<SellNowOperation> find(Connection connection, UUID operationId, boolean lock)
      throws SQLException {
    return find(statement("sell-now/find.sql") + sql.forUpdate(lock), connection, operationId);
  }

  public Optional<SellNowOperation> findBySellOrder(
      Connection connection, UUID sellOrderId, boolean lock) throws SQLException {
    return find(
        statement("sell-now/find-by-sell-order.sql") + sql.forUpdate(lock),
        connection,
        sellOrderId);
  }

  public void advance(
      Connection connection,
      UUID operationId,
      SellNowOperationState from,
      SellNowOperationState to,
      String detail)
      throws SQLException {
    String statementSql = statement("sell-now/advance.sql").replace("{now}", sql.now());
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      statement.setString(1, to.name());
      statement.setString(2, detail);
      sql.setUuid(statement, 3, operationId);
      statement.setString(4, from.name());
      if (statement.executeUpdate() != 1) {
        throw new IllegalStateException(
            "Sell Now operation state transition lost race: " + operationId);
      }
    }
  }

  private Optional<SellNowOperation> find(String statementSql, Connection connection, UUID key)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, key);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? Optional.of(read(rows)) : Optional.empty();
      }
    }
  }

  private String statement(String name) {
    return SqlStatements.load(name, sql);
  }

  private SellNowOperation read(ResultSet rows) throws SQLException {
    return new SellNowOperation(
        sql.getUuid(rows, 1),
        sql.getUuid(rows, 2),
        rows.getString(3),
        sql.getUuid(rows, 4),
        rows.getBytes(5),
        rows.getString(6),
        rows.getInt(7),
        SellNowOperationState.valueOf(rows.getString(8)),
        rows.getString(9),
        sql.getInstant(rows, 10),
        sql.getInstant(rows, 11));
  }
}
