package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.domain.Market;
import dev.mintychochip.tradingpost.domain.TradingPostBlock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketRepository {
  private final SqlDialect sql;

  public MarketRepository(SqlDialect sql) {
    this.sql = Objects.requireNonNull(sql, "sql");
  }

  public MarketRepository(TradingPostConfig config) {
    this(SqlDialect.from(config));
  }

  public void insert(Connection connection, Market market) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("markets/insert.sql"))) {
      statement.setString(1, market.name());
      statement.setString(2, market.displayName());
      statement.setInt(3, market.feeBps());
      statement.setInt(4, market.taxBps());
      statement.executeUpdate();
    }
  }

  public Optional<Market> find(Connection connection, String name) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(statement("markets/find.sql"))) {
      statement.setString(1, name);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next()
            ? Optional.of(
                new Market(rows.getString(1), rows.getString(2), rows.getInt(3), rows.getInt(4)))
            : Optional.empty();
      }
    }
  }

  public void insertPost(Connection connection, TradingPostBlock post) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("markets/insert-post.sql"))) {
      sql.setUuid(statement, 1, post.id());
      sql.setUuid(statement, 2, post.entityId());
      statement.setString(3, post.marketName());
      statement.setString(4, post.world());
      statement.setInt(5, post.x());
      statement.setInt(6, post.y());
      statement.setInt(7, post.z());
      statement.executeUpdate();
    }
  }

  public boolean hasPostForMarket(Connection connection, String marketName) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("markets/has-post.sql"))) {
      statement.setString(1, marketName);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next();
      }
    }
  }

  public Optional<TradingPostBlock> findPostByEntity(Connection connection, UUID entityId)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("markets/find-post-by-entity.sql"))) {
      sql.setUuid(statement, 1, entityId);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? Optional.of(readPost(sql, rows)) : Optional.empty();
      }
    }
  }

  public List<TradingPostBlock> listPosts(Connection connection) throws SQLException {
    try (PreparedStatement statement =
            connection.prepareStatement(statement("markets/list-posts.sql"));
        ResultSet rows = statement.executeQuery()) {
      List<TradingPostBlock> posts = new ArrayList<>();
      while (rows.next()) {
        posts.add(readPost(sql, rows));
      }
      return List.copyOf(posts);
    }
  }

  public void deletePostByEntity(Connection connection, UUID entityId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("markets/delete-post-by-entity.sql"))) {
      sql.setUuid(statement, 1, entityId);
      statement.executeUpdate();
    }
  }

  private String statement(String name) {
    return SqlStatements.load(name, sql);
  }

  private static TradingPostBlock readPost(SqlDialect sql, ResultSet rows) throws SQLException {
    return new TradingPostBlock(
        sql.getUuid(rows, 1),
        sql.getUuid(rows, 2),
        rows.getString(3),
        rows.getString(4),
        rows.getInt(5),
        rows.getInt(6),
        rows.getInt(7));
  }
}
