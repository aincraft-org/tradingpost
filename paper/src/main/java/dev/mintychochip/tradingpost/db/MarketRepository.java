package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.domain.Market;
import dev.mintychochip.tradingpost.domain.TradingPostBlock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MarketRepository {
  private final String schema;

  public MarketRepository(String schema) {
    this.schema = schema;
  }

  public void insert(Connection connection, Market market) throws SQLException {
    String sql =
        "INSERT INTO " + schema + ".markets(name,display_name,fee_bps,tax_bps) VALUES(?,?,?,?)";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, market.name());
      statement.setString(2, market.displayName());
      statement.setInt(3, market.feeBps());
      statement.setInt(4, market.taxBps());
      statement.executeUpdate();
    }
  }

  public Optional<Market> find(Connection connection, String name) throws SQLException {
    String sql =
        "SELECT name,display_name,fee_bps,tax_bps FROM " + schema + ".markets WHERE name=?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
    String sql =
        "INSERT INTO "
            + schema
            + ".trading_posts(id,entity_uuid,market_name,world,x,y,z) VALUES(?,?,?,?,?,?,?)";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, post.id());
      statement.setObject(2, post.entityId());
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
        connection.prepareStatement(
            "SELECT 1 FROM " + schema + ".trading_posts WHERE market_name=? LIMIT 1")) {
      statement.setString(1, marketName);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next();
      }
    }
  }

  public Optional<TradingPostBlock> findPostByEntity(Connection connection, UUID entityId)
      throws SQLException {
    String sql =
        "SELECT id,entity_uuid,market_name,world,x,y,z FROM "
            + schema
            + ".trading_posts WHERE entity_uuid=?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, entityId);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? Optional.of(readPost(rows)) : Optional.empty();
      }
    }
  }

  public List<TradingPostBlock> listPosts(Connection connection) throws SQLException {
    String sql = "SELECT id,entity_uuid,market_name,world,x,y,z FROM " + schema + ".trading_posts";
    try (PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rows = statement.executeQuery()) {
      List<TradingPostBlock> posts = new ArrayList<>();
      while (rows.next()) {
        posts.add(readPost(rows));
      }
      return List.copyOf(posts);
    }
  }

  public void deletePostByEntity(Connection connection, UUID entityId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "DELETE FROM " + schema + ".trading_posts WHERE entity_uuid=?")) {
      statement.setObject(1, entityId);
      statement.executeUpdate();
    }
  }

  private static TradingPostBlock readPost(ResultSet rows) throws SQLException {
    return new TradingPostBlock(
        (UUID) rows.getObject(1),
        (UUID) rows.getObject(2),
        rows.getString(3),
        rows.getString(4),
        rows.getInt(5),
        rows.getInt(6),
        rows.getInt(7));
  }
}
