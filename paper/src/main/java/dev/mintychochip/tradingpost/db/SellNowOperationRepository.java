package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.domain.SellNowOperation;
import dev.mintychochip.tradingpost.domain.SellNowOperationState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class SellNowOperationRepository {
  private final String schema;

  public SellNowOperationRepository(String schema) {
    this.schema = schema;
  }

  public void insert(Connection connection, SellNowOperation operation) throws SQLException {
    String sql =
        "INSERT INTO "
            + schema
            + ".sell_now_operations(operation_id,sell_order_id,market_name,seller,source_item_blob,source_fingerprint,original_quantity,state,failure_detail,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, operation.operationId());
      statement.setObject(2, operation.sellOrderId());
      statement.setString(3, operation.marketName());
      statement.setObject(4, operation.seller());
      statement.setBytes(5, operation.sourceItemBlob());
      statement.setString(6, operation.sourceFingerprint());
      statement.setInt(7, operation.originalQuantity());
      statement.setString(8, operation.state().name());
      statement.setString(9, operation.failureDetail());
      statement.setObject(10, sqlTime(operation.createdAt()));
      statement.setObject(11, sqlTime(operation.updatedAt()));
      statement.executeUpdate();
    }
  }

  public Optional<SellNowOperation> find(Connection connection, UUID operationId, boolean lock)
      throws SQLException {
    return find(selectSql() + " WHERE operation_id=?" + (lock ? " FOR UPDATE" : ""), connection, operationId);
  }

  public Optional<SellNowOperation> findBySellOrder(
      Connection connection, UUID sellOrderId, boolean lock) throws SQLException {
    return find(selectSql() + " WHERE sell_order_id=?" + (lock ? " FOR UPDATE" : ""), connection, sellOrderId);
  }

  public void advance(
      Connection connection,
      UUID operationId,
      SellNowOperationState from,
      SellNowOperationState to,
      String detail)
      throws SQLException {
    String sql =
        "UPDATE "
            + schema
            + ".sell_now_operations SET state=?,failure_detail=?,updated_at=clock_timestamp() WHERE operation_id=? AND state=?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, to.name());
      statement.setString(2, detail);
      statement.setObject(3, operationId);
      statement.setString(4, from.name());
      if (statement.executeUpdate() != 1) {
        throw new IllegalStateException("Sell Now operation state transition lost race: " + operationId);
      }
    }
  }

  private Optional<SellNowOperation> find(
      String sql, Connection connection, UUID key) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, key);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? Optional.of(read(rows)) : Optional.empty();
      }
    }
  }

  private String selectSql() {
    return "SELECT operation_id,sell_order_id,market_name,seller,source_item_blob,source_fingerprint,original_quantity,state,failure_detail,created_at,updated_at FROM "
        + schema
        + ".sell_now_operations";
  }

  private static SellNowOperation read(ResultSet rows) throws SQLException {
    return new SellNowOperation(
        (UUID) rows.getObject(1),
        (UUID) rows.getObject(2),
        rows.getString(3),
        (UUID) rows.getObject(4),
        rows.getBytes(5),
        rows.getString(6),
        rows.getInt(7),
        SellNowOperationState.valueOf(rows.getString(8)),
        rows.getString(9),
        instant(rows, 10),
        instant(rows, 11));
  }

  private static Instant instant(ResultSet rows, int index) throws SQLException {
    return rows.getObject(index, java.time.OffsetDateTime.class).toInstant();
  }

  private static java.time.OffsetDateTime sqlTime(Instant value) {
    return java.time.OffsetDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
  }
}
