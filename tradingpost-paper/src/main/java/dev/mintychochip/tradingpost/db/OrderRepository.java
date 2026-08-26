package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.domain.BuyOrder;
import dev.mintychochip.tradingpost.domain.Fill;
import dev.mintychochip.tradingpost.domain.OrderStatus;
import dev.mintychochip.tradingpost.domain.SellOrder;
import dev.mintychochip.tradingpost.domain.SellOrderMode;
import dev.mintychochip.tradingpost.domain.SettlementKind;
import dev.mintychochip.tradingpost.domain.SettlementState;
import dev.mintychochip.tradingpost.market.MatchingEngine;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class OrderRepository {
  private final SqlDialect sql;

  public OrderRepository(SqlDialect sql) {
    this.sql = Objects.requireNonNull(sql, "sql");
  }

  public OrderRepository(TradingPostConfig config) {
    this(SqlDialect.from(config));
  }

  public void insertSell(Connection connection, SellOrder order) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/insert-sell.sql"))) {
      sql.setUuid(statement, 1, order.id());
      statement.setString(2, order.marketName());
      sql.setUuid(statement, 3, order.seller());
      statement.setString(4, order.material());
      statement.setBytes(5, order.itemBlob());
      statement.setString(6, order.fingerprint());
      statement.setInt(7, order.quantity());
      statement.setInt(8, order.quantityRemaining());
      statement.setBigDecimal(9, order.unitPrice());
      statement.setString(10, order.mode().name());
      statement.setString(11, order.status().name());
      sql.setInstant(statement, 12, order.expiresAt());
      sql.setInstant(statement, 13, order.createdAt());
      statement.executeUpdate();
    }
  }

  public void insertBuy(Connection connection, BuyOrder order) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/insert-buy.sql"))) {
      sql.setUuid(statement, 1, order.id());
      statement.setString(2, order.marketName());
      sql.setUuid(statement, 3, order.buyer());
      statement.setString(4, order.material());
      statement.setString(5, order.templateFingerprint());
      statement.setInt(6, order.quantity());
      statement.setInt(7, order.quantityRemaining());
      statement.setBigDecimal(8, order.unitPrice());
      statement.setBigDecimal(9, order.escrowReserved());
      statement.setString(10, order.status().name());
      sql.setInstant(statement, 11, order.expiresAt());
      sql.setInstant(statement, 12, order.createdAt());
      statement.executeUpdate();
    }
  }

  public Optional<SellOrder> findSell(Connection connection, UUID id, boolean lock)
      throws SQLException {
    String statementSql = statement("orders/find-sell.sql") + sql.forUpdate(lock);
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, id);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? Optional.of(readSell(rows)) : Optional.empty();
      }
    }
  }

  public Optional<BuyOrder> findBuy(Connection connection, UUID id, boolean lock)
      throws SQLException {
    String statementSql = statement("orders/find-buy.sql") + sql.forUpdate(lock);
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, id);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? Optional.of(readBuy(rows)) : Optional.empty();
      }
    }
  }

  public List<SellOrder> bestAsks(Connection connection, String market, String material, int limit)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/best-asks.sql"))) {
      statement.setString(1, market);
      statement.setString(2, material);
      statement.setInt(3, limit);
      try (ResultSet rows = statement.executeQuery()) {
        List<SellOrder> result = new ArrayList<>();
        while (rows.next()) result.add(readSell(rows));
        return List.copyOf(result);
      }
    }
  }

  public BigDecimal escrowObligation(Connection connection) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/escrow-obligation.sql"))) {
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getBigDecimal(1);
      }
    }
  }

  public void activateSell(Connection connection, UUID id) throws SQLException {
    transition(connection, "sell_orders", id, "CREATING", "ACTIVE");
  }

  public void activateBuy(Connection connection, UUID id) throws SQLException {
    transition(connection, "buy_orders", id, "CREATING", "OPEN");
  }

  public void failCreatingSell(Connection connection, UUID id) throws SQLException {
    transition(connection, "sell_orders", id, "CREATING", "CANCELED");
  }

  public void failCreatingBuy(Connection connection, UUID id) throws SQLException {
    transition(connection, "buy_orders", id, "CREATING", "CANCELED");
  }

  public List<SellOrder> listPlayerSells(
      Connection connection, String market, UUID player, int offset, int limit)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/list-player-sells.sql"))) {
      statement.setString(1, market);
      sql.setUuid(statement, 2, player);
      statement.setInt(3, limit);
      statement.setInt(4, offset);
      try (ResultSet rows = statement.executeQuery()) {
        List<SellOrder> result = new ArrayList<>();
        while (rows.next()) result.add(readSell(rows));
        return List.copyOf(result);
      }
    }
  }

  public List<BuyOrder> listPlayerBuys(
      Connection connection, String market, UUID player, int offset, int limit)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/list-player-buys.sql"))) {
      statement.setString(1, market);
      sql.setUuid(statement, 2, player);
      statement.setInt(3, limit);
      statement.setInt(4, offset);
      try (ResultSet rows = statement.executeQuery()) {
        List<BuyOrder> result = new ArrayList<>();
        while (rows.next()) result.add(readBuy(rows));
        return List.copyOf(result);
      }
    }
  }

  public List<BuyOrder> browseBids(Connection connection, String market, int offset, int limit)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/browse-bids.sql"))) {
      statement.setString(1, market);
      statement.setInt(2, limit);
      statement.setInt(3, offset);
      try (ResultSet rows = statement.executeQuery()) {
        List<BuyOrder> result = new ArrayList<>();
        while (rows.next()) result.add(readBuy(rows));
        return List.copyOf(result);
      }
    }
  }

  private void transition(Connection connection, String table, UUID id, String from, String to)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            statement("orders/transition.sql").replace("{qualifiedTable}", sql.table(table)))) {
      statement.setString(1, to);
      sql.setUuid(statement, 2, id);
      statement.setString(3, from);
      if (statement.executeUpdate() != 1) {
        throw new IllegalStateException("order state transition lost race: " + id);
      }
    }
  }

  public boolean cancelSell(Connection connection, UUID id, UUID owner) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/cancel-sell.sql"))) {
      sql.setUuid(statement, 1, id);
      sql.setUuid(statement, 2, owner);
      return statement.executeUpdate() == 1;
    }
  }

  public boolean cancelBuy(Connection connection, UUID id, UUID owner) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/cancel-buy.sql"))) {
      sql.setUuid(statement, 1, id);
      sql.setUuid(statement, 2, owner);
      return statement.executeUpdate() == 1;
    }
  }

  public List<SellOrder> expiredSells(Connection connection, java.time.Instant now, int limit)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/expired-sells.sql"))) {
      sql.setInstant(statement, 1, now);
      statement.setInt(2, limit);
      try (ResultSet rows = statement.executeQuery()) {
        List<SellOrder> result = new ArrayList<>();
        while (rows.next()) result.add(readSell(rows));
        return List.copyOf(result);
      }
    }
  }

  public List<BuyOrder> expiredBuys(Connection connection, java.time.Instant now, int limit)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/expired-buys.sql"))) {
      sql.setInstant(statement, 1, now);
      statement.setInt(2, limit);
      try (ResultSet rows = statement.executeQuery()) {
        List<BuyOrder> result = new ArrayList<>();
        while (rows.next()) result.add(readBuy(rows));
        return List.copyOf(result);
      }
    }
  }

  public void expireSell(Connection connection, UUID id) throws SQLException {
    transition(connection, "sell_orders", id, "ACTIVE", "EXPIRED");
  }

  public void expireBuy(Connection connection, UUID id) throws SQLException {
    transition(connection, "buy_orders", id, "OPEN", "EXPIRED");
  }

  public List<BuyOrder> bestBids(Connection connection, String market, String material, int limit)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/best-bids.sql"))) {
      statement.setString(1, market);
      statement.setString(2, material);
      statement.setInt(3, limit);
      try (ResultSet rows = statement.executeQuery()) {
        List<BuyOrder> result = new ArrayList<>();
        while (rows.next()) result.add(readBuy(rows));
        return List.copyOf(result);
      }
    }
  }

  public List<SellOrder> browseAsks(Connection connection, String market, int offset, int limit)
      throws SQLException {
    return browseAsks(connection, market, null, offset, limit);
  }

  public List<SellOrder> browseAsks(
      Connection connection, String market, String materialFilter, int offset, int limit)
      throws SQLException {
    boolean filter = materialFilter != null && !materialFilter.isBlank();
    String statementSql =
        statement(filter ? "orders/browse-asks-by-material.sql" : "orders/browse-asks.sql");
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      int i = 1;
      statement.setString(i++, market);
      if (filter) {
        statement.setString(i++, materialFilter);
      }
      statement.setInt(i++, limit);
      statement.setInt(i, offset);
      try (ResultSet rows = statement.executeQuery()) {
        List<SellOrder> result = new ArrayList<>();
        while (rows.next()) result.add(readSell(rows));
        return List.copyOf(result);
      }
    }
  }

  public java.util.Optional<BuyOrder> bestBid(Connection connection, String market, String material)
      throws SQLException {
    List<BuyOrder> bids = bestBids(connection, market, material, 1);
    return bids.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(bids.getFirst());
  }

  public Optional<FillContext> findFillContext(Connection connection, UUID fillId, boolean lock)
      throws SQLException {
    String statementSql = statement("orders/find-fill-context.sql") + sql.forUpdate(lock);
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      sql.setUuid(statement, 1, fillId);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) return Optional.empty();
        Fill fill =
            new Fill(
                sql.getUuid(rows, 1),
                rows.getString(2),
                sql.getUuid(rows, 3),
                sql.getUuid(rows, 4),
                rows.getInt(5),
                rows.getBigDecimal(6),
                rows.getBytes(7),
                rows.getBytes(8),
                SettlementState.valueOf(rows.getString(9)),
                sql.getInstant(rows, 10));
        return Optional.of(
            new FillContext(
                fill,
                sql.getUuid(rows, 11),
                sql.getUuid(rows, 12),
                rows.getString(13),
                sql.getUuid(rows, 14)));
      }
    }
  }

  public void markFillDelivered(Connection connection, UUID fillId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/mark-fill-delivered.sql"))) {
      sql.setUuid(statement, 1, fillId);
      if (statement.executeUpdate() != 1) {
        throw new IllegalStateException("fill delivery state lost race: " + fillId);
      }
    }
  }

  public void markFillMoneySettled(Connection connection, UUID fillId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/mark-fill-money-settled.sql"))) {
      sql.setUuid(statement, 1, fillId);
      if (statement.executeUpdate() != 1) {
        throw new IllegalStateException("fill settlement state lost race: " + fillId);
      }
    }
  }

  /**
   * Compensates a reserved fill by restoring both order remainders and the pre-match sell item blob
   * (stored on the fill as remaining_item_blob). No mailbox side effects.
   */
  public Compensation compensateFill(Connection connection, UUID fillId) throws SQLException {
    FillContext context =
        findFillContext(connection, fillId, true)
            .orElseThrow(() -> new IllegalStateException("fill missing: " + fillId));
    // remaining_item_blob holds the pre-match sell stack so restore is exact without re-merge.
    return compensateFill(connection, fillId, context.fill().remainingItemBlob());
  }

  public Compensation compensateFill(Connection connection, UUID fillId, byte[] restoredSellBlob)
      throws SQLException {
    FillContext context =
        findFillContext(connection, fillId, true)
            .orElseThrow(() -> new IllegalStateException("fill missing: " + fillId));
    if (context.fill().status() != SettlementState.RESERVED) {
      throw new IllegalStateException("fill is not compensable: " + fillId);
    }
    SellOrder sell =
        findSell(connection, context.fill().sellOrderId(), true)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "sell order missing: " + context.fill().sellOrderId()));
    BuyOrder buy =
        findBuy(connection, context.fill().buyOrderId(), true)
            .orElseThrow(
                () ->
                    new IllegalStateException("buy order missing: " + context.fill().buyOrderId()));
    int sellRemaining = sell.quantityRemaining() + context.fill().quantity();
    // After a zeroing fill, status is FILLED; restoring any remaining must reopen the book
    // even when remaining is still below the original order quantity.
    String sellStatus = sellRemaining > 0 ? OrderStatus.ACTIVE.name() : OrderStatus.FILLED.name();
    try (PreparedStatement update =
        connection.prepareStatement(statement("orders/compensate-sell.sql"))) {
      update.setInt(1, sellRemaining);
      update.setBytes(2, restoredSellBlob);
      update.setString(3, sellStatus);
      sql.setUuid(update, 4, sell.id());
      update.executeUpdate();
    }
    int buyRemaining = buy.quantityRemaining() + context.fill().quantity();
    String buyStatus = buyRemaining > 0 ? OrderStatus.OPEN.name() : OrderStatus.FILLED.name();
    try (PreparedStatement update =
        connection.prepareStatement(statement("orders/compensate-buy-remaining.sql"))) {
      update.setInt(1, buyRemaining);
      update.setString(2, buyStatus);
      sql.setUuid(update, 3, buy.id());
      update.executeUpdate();
    }
    try (PreparedStatement update =
        connection.prepareStatement(statement("orders/compensate-buy-escrow.sql"))) {
      update.setBigDecimal(
          1, context.fill().unitPrice().multiply(BigDecimal.valueOf(context.fill().quantity())));
      sql.setUuid(update, 2, buy.id());
      update.executeUpdate();
    }
    try (PreparedStatement update =
        connection.prepareStatement(statement("orders/void-fill.sql"))) {
      sql.setUuid(update, 1, fillId);
      if (update.executeUpdate() != 1) {
        throw new IllegalStateException("fill compensation lost race: " + fillId);
      }
    }
    return new Compensation(
        context.fill().marketName(),
        context.seller(),
        context.fingerprint(),
        context.fill().itemBlob(),
        context.fill().remainingItemBlob());
  }

  public FillReservation reserveMatch(
      Connection connection,
      MatchingEngine.MatchDecision decision,
      SettlementDraft settlement,
      byte[] filledItemBlob,
      byte[] remainingItemBlob)
      throws SQLException {
    SellOrder sell;
    BuyOrder buy;
    if (decision.sellOrderId().compareTo(decision.buyOrderId()) <= 0) {
      sell =
          findSell(connection, decision.sellOrderId(), true)
              .orElseThrow(() -> new IllegalStateException("sell order missing"));
      buy =
          findBuy(connection, decision.buyOrderId(), true)
              .orElseThrow(() -> new IllegalStateException("buy order missing"));
    } else {
      buy =
          findBuy(connection, decision.buyOrderId(), true)
              .orElseThrow(() -> new IllegalStateException("buy order missing"));
      sell =
          findSell(connection, decision.sellOrderId(), true)
              .orElseThrow(() -> new IllegalStateException("sell order missing"));
    }
    if (sell.status() != OrderStatus.ACTIVE || buy.status() != OrderStatus.OPEN) {
      throw new IllegalStateException("order is not matchable");
    }
    int quantity =
        Math.min(decision.quantity(), Math.min(sell.quantityRemaining(), buy.quantityRemaining()));
    if (quantity < 1) {
      throw new IllegalStateException("order has no remaining quantity");
    }
    UUID fillId = UUID.randomUUID();
    // remaining_item_blob stores the pre-match sell stack for exact compensation restore.
    // The post-match remainder is written only to sell_orders.item_blob.
    byte[] preMatchSellBlob = sell.itemBlob();
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/insert-fill.sql"))) {
      sql.setUuid(statement, 1, fillId);
      statement.setString(2, sell.marketName());
      sql.setUuid(statement, 3, sell.id());
      sql.setUuid(statement, 4, buy.id());
      statement.setInt(5, quantity);
      statement.setBigDecimal(6, decision.executionPrice());
      statement.setBytes(7, filledItemBlob);
      statement.setBytes(8, preMatchSellBlob);
      statement.setString(9, SettlementState.RESERVED.name());
      sql.setUuid(statement, 10, settlement.operationId());
      statement.executeUpdate();
    }
    updateRemaining(
        connection,
        "sell_orders",
        sell.id(),
        sell.quantityRemaining() - quantity,
        sell.quantityRemaining() == quantity ? "FILLED" : sell.status().name(),
        remainingItemBlob);
    updateRemaining(
        connection,
        "buy_orders",
        buy.id(),
        buy.quantityRemaining() - quantity,
        buy.quantityRemaining() == quantity ? "FILLED" : buy.status().name(),
        null);
    decrementEscrow(
        connection, buy.id(), decision.executionPrice().multiply(BigDecimal.valueOf(quantity)));
    insertSettlement(
        connection,
        new SettlementDraft(
            settlement.id(),
            SettlementKind.MATCH_SETTLEMENT,
            "ah/match/" + fillId,
            fillId,
            null,
            decision.executionPrice().multiply(BigDecimal.valueOf(quantity)),
            settlement.operationId()));
    return new FillReservation(
        new Fill(
            fillId,
            sell.marketName(),
            sell.id(),
            buy.id(),
            quantity,
            decision.executionPrice(),
            filledItemBlob,
            remainingItemBlob,
            SettlementState.RESERVED,
            Instant.now()),
        sell,
        buy);
  }

  private void insertSettlement(Connection connection, SettlementDraft settlement)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/insert-settlement.sql"))) {
      sql.setUuid(statement, 1, settlement.id());
      statement.setString(2, settlement.kind().name());
      statement.setString(3, settlement.idempotencyKey());
      sql.setUuid(statement, 4, settlement.fillId());
      sql.setUuid(statement, 5, settlement.orderId());
      statement.setBigDecimal(6, settlement.amount());
      statement.setString(7, SettlementState.RESERVED.name());
      sql.setUuid(statement, 8, settlement.operationId());
      statement.executeUpdate();
    }
  }

  private void updateRemaining(
      Connection connection,
      String table,
      UUID id,
      int remaining,
      String status,
      byte[] remainingBlob)
      throws SQLException {
    boolean sell = table.equals("sell_orders");
    String statementSql =
        statement(sell ? "orders/update-sell-remaining.sql" : "orders/update-buy-remaining.sql");
    try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
      statement.setInt(1, remaining);
      statement.setString(2, status);
      if (sell) {
        statement.setBytes(3, remainingBlob);
        sql.setUuid(statement, 4, id);
      } else {
        sql.setUuid(statement, 3, id);
      }
      statement.executeUpdate();
    }
  }

  private void decrementEscrow(Connection connection, UUID id, BigDecimal amount)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(statement("orders/decrement-escrow.sql"))) {
      statement.setBigDecimal(1, amount);
      sql.setUuid(statement, 2, id);
      if (statement.executeUpdate() != 1)
        throw new IllegalStateException("buy escrow update lost race");
    }
  }

  private String statement(String name) {
    return SqlStatements.load(name, sql);
  }

  private SellOrder readSell(ResultSet rows) throws SQLException {
    return new SellOrder(
        sql.getUuid(rows, 1),
        rows.getString(2),
        sql.getUuid(rows, 3),
        rows.getString(4),
        rows.getBytes(5),
        rows.getString(6),
        rows.getInt(7),
        rows.getInt(8),
        rows.getBigDecimal(9),
        SellOrderMode.valueOf(rows.getString(10)),
        OrderStatus.valueOf(rows.getString(11)),
        sql.getInstant(rows, 12),
        sql.getInstant(rows, 13));
  }

  private BuyOrder readBuy(ResultSet rows) throws SQLException {
    return new BuyOrder(
        sql.getUuid(rows, 1),
        rows.getString(2),
        sql.getUuid(rows, 3),
        rows.getString(4),
        rows.getString(5),
        rows.getInt(6),
        rows.getInt(7),
        rows.getBigDecimal(8),
        rows.getBigDecimal(9),
        OrderStatus.valueOf(rows.getString(10)),
        sql.getInstant(rows, 11),
        sql.getInstant(rows, 12));
  }

  public record SettlementDraft(
      UUID id,
      SettlementKind kind,
      String idempotencyKey,
      UUID fillId,
      UUID orderId,
      BigDecimal amount,
      UUID operationId) {
    public SettlementDraft(
        UUID id,
        SettlementKind kind,
        String idempotencyKey,
        UUID fillId,
        UUID orderId,
        BigDecimal amount) {
      this(id, kind, idempotencyKey, fillId, orderId, amount, null);
    }
  }

  public record FillContext(
      Fill fill, UUID seller, UUID buyer, String fingerprint, UUID operationId) {}

  public record Compensation(
      String marketName,
      UUID seller,
      String fingerprint,
      byte[] itemBlob,
      byte[] remainingItemBlob) {}

  public record FillReservation(Fill fill, SellOrder sellOrder, BuyOrder buyOrder) {}
}
