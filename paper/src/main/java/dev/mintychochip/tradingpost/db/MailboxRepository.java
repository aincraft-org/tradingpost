package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.domain.MailboxItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MailboxRepository {
  private final SqlDialect sql;

  public MailboxRepository(SqlDialect sql) {
    this.sql = Objects.requireNonNull(sql, "sql");
  }

  public MailboxRepository(TradingPostConfig config) {
    this(SqlDialect.from(config));
  }

  public void insert(Connection connection, MailboxItem item) throws SQLException {
    String statementSql =
        "INSERT INTO "
            + sql.table("mailbox_items")
            + " (id,market_name,owner,item_blob,fingerprint,reason,state,settlement_id) VALUES(?,?,?,?,?,?,?,?)";
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, item.id());
      statement.setString(2, item.marketName());
      sql.setUuid(statement, 3, item.owner());
      statement.setBytes(4, item.itemBlob());
      statement.setString(5, item.fingerprint());
      statement.setString(6, item.reason());
      statement.setString(7, item.state());
      sql.setUuid(statement, 8, item.settlementId());
      statement.executeUpdate();
    }
  }

  public void insertDelivery(
      Connection connection,
      UUID settlementId,
      String marketName,
      UUID owner,
      byte[] itemBlob,
      String fingerprint,
      String reason)
      throws SQLException {
    String statementSql =
        sql.insertIgnore(
            sql.table("mailbox_items"),
            " (id,market_name,owner,item_blob,fingerprint,reason,state,settlement_id) ",
            "(?,?,?,?,?,?, 'UNCLAIMED',?)",
            "settlement_id");
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, settlementId);
      statement.setString(2, marketName);
      sql.setUuid(statement, 3, owner);
      statement.setBytes(4, itemBlob);
      statement.setString(5, fingerprint);
      statement.setString(6, reason);
      sql.setUuid(statement, 7, settlementId);
      statement.executeUpdate();
    }
  }

  public Optional<MailboxItem> find(Connection connection, UUID id, boolean lock)
      throws SQLException {
    String statementSql = selectSql() + " WHERE id=?" + sql.forUpdate(lock);
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, id);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? Optional.of(read(rows)) : Optional.empty();
      }
    }
  }

  public List<MailboxItem> list(Connection connection, UUID owner, String marketName)
      throws SQLException {
    String statementSql =
        selectSql()
            + " WHERE owner=? AND market_name=? AND state='UNCLAIMED' ORDER BY created_at ASC";
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, owner);
      statement.setString(2, marketName);
      try (ResultSet rows = statement.executeQuery()) {
        List<MailboxItem> items = new ArrayList<>();
        while (rows.next()) {
          items.add(read(rows));
        }
        return List.copyOf(items);
      }
    }
  }

  public boolean markClaiming(Connection connection, UUID id, UUID owner) throws SQLException {
    String statementSql =
        "UPDATE "
            + sql.table("mailbox_items")
            + " SET state='CLAIMING' WHERE id=? AND owner=? AND state='UNCLAIMED'";
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, id);
      sql.setUuid(statement, 2, owner);
      return statement.executeUpdate() == 1;
    }
  }

  public void releaseClaiming(Connection connection, UUID id) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "UPDATE "
                + sql.table("mailbox_items")
                + " SET state='UNCLAIMED' WHERE id=? AND state='CLAIMING'")) {
      sql.setUuid(statement, 1, id);
      statement.executeUpdate();
    }
  }

  public void markClaimed(Connection connection, UUID id) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "UPDATE "
                + sql.table("mailbox_items")
                + " SET state='CLAIMED',claimed_at="
                + sql.now()
                + " WHERE id=? AND state='CLAIMING'")) {
      sql.setUuid(statement, 1, id);
      if (statement.executeUpdate() != 1) {
        throw new IllegalStateException("mailbox claim state lost race");
      }
    }
  }

  private String selectSql() {
    return "SELECT id,market_name,owner,item_blob,fingerprint,reason,state,settlement_id,created_at FROM "
        + sql.table("mailbox_items");
  }

  private MailboxItem read(ResultSet rows) throws SQLException {
    return new MailboxItem(
        sql.getUuid(rows, 1),
        rows.getString(2),
        sql.getUuid(rows, 3),
        rows.getBytes(4),
        rows.getString(5),
        rows.getString(6),
        rows.getString(7),
        sql.getUuid(rows, 8),
        sql.getInstant(rows, 9));
  }
}
