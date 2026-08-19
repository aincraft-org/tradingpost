package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.domain.Settlement;
import dev.mintychochip.tradingpost.domain.SettlementKind;
import dev.mintychochip.tradingpost.domain.SettlementState;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SettlementRepository {
  private final SqlDialect sql;

  public SettlementRepository(SqlDialect sql) {
    this.sql = Objects.requireNonNull(sql, "sql");
  }

  public SettlementRepository(TradingPostConfig config) {
    this(SqlDialect.from(config));
  }

  public void insertReserved(Connection connection, SettlementDraft draft) throws SQLException {
    String statementSql =
        "INSERT INTO "
            + sql.table("settlements")
            + "(id,kind,idempotency_key,fill_id,order_id,amount,state) VALUES(?,?,?,?,?,?, 'RESERVED')";
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, draft.id());
      statement.setString(2, draft.kind().name());
      statement.setString(3, draft.idempotencyKey());
      sql.setUuid(statement, 4, draft.fillId());
      sql.setUuid(statement, 5, draft.orderId());
      statement.setBigDecimal(6, draft.amount());
      statement.executeUpdate();
    }
  }

  public Optional<Settlement> find(Connection connection, UUID id, boolean lock)
      throws SQLException {
    String statementSql = selectSql() + " WHERE id=?" + sql.forUpdate(lock);
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, id);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? Optional.of(read(rows)) : Optional.empty();
      }
    }
  }

  public Optional<Settlement> leaseNext(Connection connection, String nodeId, Instant now)
      throws SQLException {
    String statementSql =
        selectSql()
            + " WHERE state IN ('RESERVED','MONEY_SETTLED') "
            + "AND (lease_until IS NULL OR lease_until < ?) ORDER BY updated_at ASC LIMIT 1"
            + sql.skipLocked();
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setInstant(statement, 1, now);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          return Optional.empty();
        }
        Settlement before = read(rows);
        Instant leaseUntil = now.plusSeconds(30);
        try (PreparedStatement update =
            connection.prepareStatement(
                "UPDATE "
                    + sql.table("settlements")
                    + " SET lease_owner=?,lease_until=?,attempts=attempts+1,updated_at=? WHERE id=?")) {
          update.setString(1, nodeId);
          sql.setInstant(update, 2, leaseUntil);
          sql.setInstant(update, 3, now);
          sql.setUuid(update, 4, before.id());
          update.executeUpdate();
        }
        return find(connection, before.id(), true);
      }
    }
  }

  public void advance(
      Connection connection, UUID id, SettlementState from, SettlementState to, String error)
      throws SQLException {
    String statementSql =
        "UPDATE "
            + sql.table("settlements")
            + " SET state=?,last_error=?,lease_owner=NULL,lease_until=NULL,updated_at="
            + sql.now()
            + " WHERE id=? AND state=?";
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      statement.setString(1, to.name());
      statement.setString(2, error);
      sql.setUuid(statement, 3, id);
      statement.setString(4, from.name());
      if (statement.executeUpdate() != 1) {
        throw new IllegalStateException("settlement state transition lost race: " + id);
      }
    }
  }

  private String selectSql() {
    return "SELECT id,kind,idempotency_key,fill_id,order_id,amount,state,attempts,last_error,lease_owner,lease_until,created_at,updated_at FROM "
        + sql.table("settlements");
  }

  private Settlement read(ResultSet rows) throws SQLException {
    return new Settlement(
        sql.getUuid(rows, 1),
        SettlementKind.valueOf(rows.getString(2)),
        rows.getString(3),
        sql.getUuid(rows, 4),
        sql.getUuid(rows, 5),
        rows.getBigDecimal(6),
        SettlementState.valueOf(rows.getString(7)),
        rows.getInt(8),
        rows.getString(9),
        rows.getString(10),
        sql.getInstant(rows, 11),
        sql.getInstant(rows, 12),
        sql.getInstant(rows, 13));
  }

  public record SettlementDraft(
      UUID id,
      SettlementKind kind,
      String idempotencyKey,
      UUID fillId,
      UUID orderId,
      BigDecimal amount) {}
}
