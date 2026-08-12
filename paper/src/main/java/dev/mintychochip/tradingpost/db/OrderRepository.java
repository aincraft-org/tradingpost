package dev.mintychochip.tradingpost.db;

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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class OrderRepository {
    private final String schema;

    public OrderRepository(String schema) {
        this.schema = schema;
    }

    public void insertSell(Connection connection, SellOrder order) throws SQLException {
        String sql = "INSERT INTO " + schema + ".sell_orders "
                + "(id,market_name,seller,material,item_blob,fingerprint,quantity,quantity_remaining,unit_price,mode,status,expires_at,created_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, order.id());
            statement.setString(2, order.marketName());
            statement.setObject(3, order.seller());
            statement.setString(4, order.material());
            statement.setBytes(5, order.itemBlob());
            statement.setString(6, order.fingerprint());
            statement.setInt(7, order.quantity());
            statement.setInt(8, order.quantityRemaining());
            statement.setBigDecimal(9, order.unitPrice());
            statement.setString(10, order.mode().name());
            statement.setString(11, order.status().name());
            statement.setObject(12, sqlTime(order.expiresAt()));
            statement.setObject(13, sqlTime(order.createdAt()));
            statement.executeUpdate();
        }
    }

    public void insertBuy(Connection connection, BuyOrder order) throws SQLException {
        String sql = "INSERT INTO " + schema + ".buy_orders "
                + "(id,market_name,buyer,material,template_fingerprint,quantity,quantity_remaining,unit_price,escrow_reserved,status,expires_at,created_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, order.id());
            statement.setString(2, order.marketName());
            statement.setObject(3, order.buyer());
            statement.setString(4, order.material());
            statement.setString(5, order.templateFingerprint());
            statement.setInt(6, order.quantity());
            statement.setInt(7, order.quantityRemaining());
            statement.setBigDecimal(8, order.unitPrice());
            statement.setBigDecimal(9, order.escrowReserved());
            statement.setString(10, order.status().name());
            statement.setObject(11, sqlTime(order.expiresAt()));
            statement.setObject(12, sqlTime(order.createdAt()));
            statement.executeUpdate();
        }
    }

    public Optional<SellOrder> findSell(Connection connection, UUID id, boolean lock) throws SQLException {
        String sql = "SELECT id,market_name,seller,material,item_blob,fingerprint,quantity,quantity_remaining,unit_price,mode,status,expires_at,created_at "
                + "FROM " + schema + ".sell_orders WHERE id=?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readSell(rows)) : Optional.empty();
            }
        }
    }

    public Optional<BuyOrder> findBuy(Connection connection, UUID id, boolean lock) throws SQLException {
        String sql = "SELECT id,market_name,buyer,material,template_fingerprint,quantity,quantity_remaining,unit_price,escrow_reserved,status,expires_at,created_at "
                + "FROM " + schema + ".buy_orders WHERE id=?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readBuy(rows)) : Optional.empty();
            }
        }
    }

    public List<SellOrder> bestAsks(Connection connection, String market, String material, int limit) throws SQLException {
        String sql = "SELECT id,market_name,seller,material,item_blob,fingerprint,quantity,quantity_remaining,unit_price,mode,status,expires_at,created_at "
                + "FROM " + schema + ".sell_orders WHERE market_name=? AND material=? AND status='ACTIVE' AND quantity_remaining>0 "
                + "ORDER BY unit_price ASC, created_at ASC, id ASC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(SUM(escrow_reserved), 0) FROM " + schema
                        + ".buy_orders WHERE status IN ('CREATING','OPEN')")) {
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

    public List<SellOrder> listPlayerSells(Connection connection, String market, UUID player, int offset, int limit)
            throws SQLException {
        String sql = "SELECT id,market_name,seller,material,item_blob,fingerprint,quantity,quantity_remaining,unit_price,mode,status,expires_at,created_at "
                + "FROM " + schema + ".sell_orders WHERE market_name=? AND seller=? AND status IN ('ACTIVE','CREATING') "
                + "ORDER BY created_at DESC, id DESC OFFSET ? LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, market);
            statement.setObject(2, player);
            statement.setInt(3, offset);
            statement.setInt(4, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<SellOrder> result = new ArrayList<>();
                while (rows.next()) result.add(readSell(rows));
                return List.copyOf(result);
            }
        }
    }

    public List<BuyOrder> listPlayerBuys(Connection connection, String market, UUID player, int offset, int limit)
            throws SQLException {
        String sql = "SELECT id,market_name,buyer,material,template_fingerprint,quantity,quantity_remaining,unit_price,escrow_reserved,status,expires_at,created_at "
                + "FROM " + schema + ".buy_orders WHERE market_name=? AND buyer=? AND status IN ('OPEN','CREATING') "
                + "ORDER BY created_at DESC, id DESC OFFSET ? LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, market);
            statement.setObject(2, player);
            statement.setInt(3, offset);
            statement.setInt(4, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<BuyOrder> result = new ArrayList<>();
                while (rows.next()) result.add(readBuy(rows));
                return List.copyOf(result);
            }
        }
    }

    public List<BuyOrder> browseBids(Connection connection, String market, int offset, int limit) throws SQLException {
        String sql = "SELECT id,market_name,buyer,material,template_fingerprint,quantity,quantity_remaining,unit_price,escrow_reserved,status,expires_at,created_at "
                + "FROM " + schema + ".buy_orders WHERE market_name=? AND status='OPEN' AND quantity_remaining>0 "
                + "ORDER BY unit_price DESC, created_at ASC, id ASC OFFSET ? LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, market);
            statement.setInt(2, offset);
            statement.setInt(3, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<BuyOrder> result = new ArrayList<>();
                while (rows.next()) result.add(readBuy(rows));
                return List.copyOf(result);
            }
        }
    }

    private void transition(Connection connection, String table, UUID id, String from, String to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + schema + "." + table + " SET status=? WHERE id=? AND status=?")) {
            statement.setString(1, to);
            statement.setObject(2, id);
            statement.setString(3, from);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("order state transition lost race: " + id);
            }
        }
    }
    public boolean cancelSell(Connection connection, UUID id, UUID owner) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + schema + ".sell_orders SET status='CANCELED' WHERE id=? AND seller=? AND status='ACTIVE'")) {
            statement.setObject(1, id);
            statement.setObject(2, owner);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean cancelBuy(Connection connection, UUID id, UUID owner) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + schema + ".buy_orders SET status='CANCELED' WHERE id=? AND buyer=? AND status='OPEN'")) {
            statement.setObject(1, id);
            statement.setObject(2, owner);
            return statement.executeUpdate() == 1;
        }
    }


    public List<SellOrder> expiredSells(Connection connection, java.time.Instant now, int limit) throws SQLException {
        String sql = "SELECT id,market_name,seller,material,item_blob,fingerprint,quantity,quantity_remaining,unit_price,mode,status,expires_at,created_at "
                + "FROM " + schema + ".sell_orders WHERE status='ACTIVE' AND expires_at<? ORDER BY expires_at ASC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sqlTime(now));
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<SellOrder> result = new ArrayList<>();
                while (rows.next()) result.add(readSell(rows));
                return List.copyOf(result);
            }
        }
    }

    public List<BuyOrder> expiredBuys(Connection connection, java.time.Instant now, int limit) throws SQLException {
        String sql = "SELECT id,market_name,buyer,material,template_fingerprint,quantity,quantity_remaining,unit_price,escrow_reserved,status,expires_at,created_at "
                + "FROM " + schema + ".buy_orders WHERE status='OPEN' AND expires_at<? ORDER BY expires_at ASC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sqlTime(now));
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


    public List<BuyOrder> bestBids(Connection connection, String market, String material, int limit) throws SQLException {
        String sql = "SELECT id,market_name,buyer,material,template_fingerprint,quantity,quantity_remaining,unit_price,escrow_reserved,status,expires_at,created_at "
                + "FROM " + schema + ".buy_orders WHERE market_name=? AND material=? AND status='OPEN' AND quantity_remaining>0 "
                + "ORDER BY unit_price DESC, created_at ASC, id ASC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
    public List<SellOrder> browseAsks(Connection connection, String market, int offset, int limit) throws SQLException {
        return browseAsks(connection, market, null, offset, limit);
    }

    public List<SellOrder> browseAsks(Connection connection, String market, String materialFilter, int offset, int limit)
            throws SQLException {
        boolean filter = materialFilter != null && !materialFilter.isBlank();
        String sql = "SELECT id,market_name,seller,material,item_blob,fingerprint,quantity,quantity_remaining,unit_price,mode,status,expires_at,created_at "
                + "FROM " + schema + ".sell_orders WHERE market_name=? AND status='ACTIVE' AND quantity_remaining>0 "
                + (filter ? "AND material=? " : "")
                + "ORDER BY unit_price ASC, created_at ASC, id ASC OFFSET ? LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            statement.setString(i++, market);
            if (filter) {
                statement.setString(i++, materialFilter);
            }
            statement.setInt(i++, offset);
            statement.setInt(i, limit);
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


    public Optional<FillContext> findFillContext(Connection connection, UUID fillId, boolean lock) throws SQLException {
        String sql = "SELECT f.fill_id,f.market_name,f.sell_order_id,f.buy_order_id,f.quantity,f.unit_price,"
                + "f.item_blob,f.remaining_item_blob,f.status,f.created_at,s.seller,b.buyer,s.fingerprint "
                + "FROM " + schema + ".fills f JOIN " + schema + ".sell_orders s ON s.id=f.sell_order_id "
                + "JOIN " + schema + ".buy_orders b ON b.id=f.buy_order_id WHERE f.fill_id=?"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, fillId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                Fill fill = new Fill((UUID) rows.getObject(1), rows.getString(2), (UUID) rows.getObject(3),
                        (UUID) rows.getObject(4), rows.getInt(5), rows.getBigDecimal(6), rows.getBytes(7),
                        rows.getBytes(8), SettlementState.valueOf(rows.getString(9)),
                        rows.getObject(10, java.time.OffsetDateTime.class).toInstant());
                return Optional.of(new FillContext(fill, (UUID) rows.getObject(11), (UUID) rows.getObject(12),
                        rows.getString(13)));
            }
        }
    }

    public void markFillDelivered(Connection connection, UUID fillId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + schema + ".fills SET status='DELIVERED' WHERE fill_id=? AND status='MONEY_SETTLED'")) {
            statement.setObject(1, fillId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("fill delivery state lost race: " + fillId);
            }
        }
    }
    public void markFillMoneySettled(Connection connection, UUID fillId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + schema + ".fills SET status='MONEY_SETTLED' WHERE fill_id=? AND status='RESERVED'")) {
            statement.setObject(1, fillId);
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
        FillContext context = findFillContext(connection, fillId, true)
                .orElseThrow(() -> new IllegalStateException("fill missing: " + fillId));
        // remaining_item_blob holds the pre-match sell stack so restore is exact without re-merge.
        return compensateFill(connection, fillId, context.fill().remainingItemBlob());
    }

    public Compensation compensateFill(Connection connection, UUID fillId, byte[] restoredSellBlob) throws SQLException {
        FillContext context = findFillContext(connection, fillId, true)
                .orElseThrow(() -> new IllegalStateException("fill missing: " + fillId));
        if (context.fill().status() != SettlementState.RESERVED) {
            throw new IllegalStateException("fill is not compensable: " + fillId);
        }
        SellOrder sell = findSell(connection, context.fill().sellOrderId(), true)
                .orElseThrow(() -> new IllegalStateException("sell order missing: " + context.fill().sellOrderId()));
        BuyOrder buy = findBuy(connection, context.fill().buyOrderId(), true)
                .orElseThrow(() -> new IllegalStateException("buy order missing: " + context.fill().buyOrderId()));
        int sellRemaining = sell.quantityRemaining() + context.fill().quantity();
        // After a zeroing fill, status is FILLED; restoring any remaining must reopen the book
        // even when remaining is still below the original order quantity.
        String sellStatus = sellRemaining > 0 ? OrderStatus.ACTIVE.name() : OrderStatus.FILLED.name();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + schema + ".sell_orders SET quantity_remaining=?,item_blob=?,status=? WHERE id=?")) {
            update.setInt(1, sellRemaining);
            update.setBytes(2, restoredSellBlob);
            update.setString(3, sellStatus);
            update.setObject(4, sell.id());
            update.executeUpdate();
        }
        int buyRemaining = buy.quantityRemaining() + context.fill().quantity();
        String buyStatus = buyRemaining > 0 ? OrderStatus.OPEN.name() : OrderStatus.FILLED.name();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + schema + ".buy_orders SET quantity_remaining=?,status=? WHERE id=?")) {
            update.setInt(1, buyRemaining);
            update.setString(2, buyStatus);
            update.setObject(3, buy.id());
            update.executeUpdate();
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + schema + ".buy_orders SET escrow_reserved=escrow_reserved+? WHERE id=?")) {
            update.setBigDecimal(1, context.fill().unitPrice().multiply(BigDecimal.valueOf(context.fill().quantity())));
            update.setObject(2, buy.id());
            update.executeUpdate();
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + schema + ".fills SET status='VOIDED' WHERE fill_id=? AND status='RESERVED'")) {
            update.setObject(1, fillId);
            if (update.executeUpdate() != 1) {
                throw new IllegalStateException("fill compensation lost race: " + fillId);
            }
        }
        return new Compensation(context.fill().marketName(), context.seller(), context.fingerprint(),
                context.fill().itemBlob(), context.fill().remainingItemBlob());
    }

    public FillReservation reserveMatch(Connection connection, MatchingEngine.MatchDecision decision,
                                        SettlementDraft settlement, byte[] filledItemBlob,
                                        byte[] remainingItemBlob) throws SQLException {
        SellOrder sell;
        BuyOrder buy;
        if (decision.sellOrderId().compareTo(decision.buyOrderId()) <= 0) {
            sell = findSell(connection, decision.sellOrderId(), true)
                    .orElseThrow(() -> new IllegalStateException("sell order missing"));
            buy = findBuy(connection, decision.buyOrderId(), true)
                    .orElseThrow(() -> new IllegalStateException("buy order missing"));
        } else {
            buy = findBuy(connection, decision.buyOrderId(), true)
                    .orElseThrow(() -> new IllegalStateException("buy order missing"));
            sell = findSell(connection, decision.sellOrderId(), true)
                    .orElseThrow(() -> new IllegalStateException("sell order missing"));
        }
        if (sell.status() != OrderStatus.ACTIVE || buy.status() != OrderStatus.OPEN) {
            throw new IllegalStateException("order is not matchable");
        }
        int quantity = Math.min(decision.quantity(), Math.min(sell.quantityRemaining(), buy.quantityRemaining()));
        if (quantity < 1) {
            throw new IllegalStateException("order has no remaining quantity");
        }
        UUID fillId = UUID.randomUUID();
        // remaining_item_blob stores the pre-match sell stack for exact compensation restore.
        // The post-match remainder is written only to sell_orders.item_blob.
        byte[] preMatchSellBlob = sell.itemBlob();
        String fillSql = "INSERT INTO " + schema + ".fills(fill_id,market_name,sell_order_id,buy_order_id,quantity,unit_price,item_blob,remaining_item_blob,status) VALUES(?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(fillSql)) {
            statement.setObject(1, fillId);
            statement.setString(2, sell.marketName());
            statement.setObject(3, sell.id());
            statement.setObject(4, buy.id());
            statement.setInt(5, quantity);
            statement.setBigDecimal(6, decision.executionPrice());
            statement.setBytes(7, filledItemBlob);
            statement.setBytes(8, preMatchSellBlob);
            statement.setString(9, SettlementState.RESERVED.name());
            statement.executeUpdate();
        }
        updateRemaining(connection, "sell_orders", sell.id(), sell.quantityRemaining() - quantity,
                sell.quantityRemaining() == quantity ? "FILLED" : sell.status().name(), remainingItemBlob);
        updateRemaining(connection, "buy_orders", buy.id(), buy.quantityRemaining() - quantity,
                buy.quantityRemaining() == quantity ? "FILLED" : buy.status().name(), null);
        decrementEscrow(connection, buy.id(), decision.executionPrice().multiply(BigDecimal.valueOf(quantity)));
        insertSettlement(connection, new SettlementDraft(settlement.id(), SettlementKind.MATCH_SETTLEMENT,
                "ah/match/" + fillId, fillId, null,
                decision.executionPrice().multiply(BigDecimal.valueOf(quantity))));
        return new FillReservation(new Fill(fillId, sell.marketName(), sell.id(), buy.id(), quantity,
                decision.executionPrice(), filledItemBlob, remainingItemBlob, SettlementState.RESERVED, Instant.now()), sell, buy);
    }

    private void insertSettlement(Connection connection, SettlementDraft settlement) throws SQLException {
        String sql = "INSERT INTO " + schema + ".settlements(id,kind,idempotency_key,fill_id,order_id,amount,state) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, settlement.id());
            statement.setString(2, settlement.kind().name());
            statement.setString(3, settlement.idempotencyKey());
            statement.setObject(4, settlement.fillId());
            statement.setObject(5, settlement.orderId());
            statement.setBigDecimal(6, settlement.amount());
            statement.setString(7, SettlementState.RESERVED.name());
            statement.executeUpdate();
        }
    }

    private void updateRemaining(Connection connection, String table, UUID id, int remaining, String status,
                                 byte[] remainingBlob) throws SQLException {
        boolean sell = table.equals("sell_orders");
        String sql = "UPDATE " + schema + "." + table
                + (sell ? " SET quantity_remaining=?,status=?,item_blob=? WHERE id=?"
                        : " SET quantity_remaining=?,status=? WHERE id=?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, remaining);
            statement.setString(2, status);
            if (sell) {
                statement.setBytes(3, remainingBlob);
                statement.setObject(4, id);
            } else {
                statement.setObject(3, id);
            }
            statement.executeUpdate();
        }
    }
    private void decrementEscrow(Connection connection, UUID id, BigDecimal amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + schema + ".buy_orders SET escrow_reserved=escrow_reserved-? WHERE id=?")) {
            statement.setBigDecimal(1, amount);
            statement.setObject(2, id);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("buy escrow update lost race");
        }
    }
    private static java.time.OffsetDateTime sqlTime(java.time.Instant value) {
        return java.time.OffsetDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
    }



    private static SellOrder readSell(ResultSet rows) throws SQLException {
        return new SellOrder((UUID) rows.getObject(1), rows.getString(2), (UUID) rows.getObject(3), rows.getString(4),
                rows.getBytes(5), rows.getString(6), rows.getInt(7), rows.getInt(8), rows.getBigDecimal(9),
                SellOrderMode.valueOf(rows.getString(10)), OrderStatus.valueOf(rows.getString(11)),
                rows.getObject(12, java.time.OffsetDateTime.class).toInstant(), rows.getObject(13, java.time.OffsetDateTime.class).toInstant());
    }

    private static BuyOrder readBuy(ResultSet rows) throws SQLException {
        return new BuyOrder((UUID) rows.getObject(1), rows.getString(2), (UUID) rows.getObject(3), rows.getString(4),
                rows.getString(5), rows.getInt(6), rows.getInt(7), rows.getBigDecimal(8), rows.getBigDecimal(9),
                OrderStatus.valueOf(rows.getString(10)), rows.getObject(11, java.time.OffsetDateTime.class).toInstant(),
                rows.getObject(12, java.time.OffsetDateTime.class).toInstant());
    }

    public record SettlementDraft(UUID id, SettlementKind kind, String idempotencyKey, UUID fillId, UUID orderId,
                                  BigDecimal amount) {
    }

    public record FillContext(Fill fill, UUID seller, UUID buyer, String fingerprint) {
    }

    public record Compensation(String marketName, UUID seller, String fingerprint, byte[] itemBlob,
                               byte[] remainingItemBlob) {
    }

    public record FillReservation(Fill fill, SellOrder sellOrder, BuyOrder buyOrder) {
    }
}
