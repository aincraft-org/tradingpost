package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.TradingPostConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class ReviewRepository {
  private final SqlDialect sql;

  public ReviewRepository(SqlDialect sql) {
    this.sql = Objects.requireNonNull(sql, "sql");
  }

  public ReviewRepository(TradingPostConfig config) {
    this(SqlDialect.from(config));
  }

  public void insert(Connection connection, UUID id, UUID player, String fingerprint, String detail)
      throws SQLException {
    String statementSql =
        "INSERT INTO "
            + sql.table("review_queue")
            + "(id,player,fingerprint,detail) VALUES(?,?,"
            + "?,"
            + sql.jsonPlaceholder()
            + ")";
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, id);
      sql.setUuid(statement, 2, player);
      statement.setString(3, fingerprint);
      statement.setString(4, detail);
      statement.executeUpdate();
    }
  }
}
