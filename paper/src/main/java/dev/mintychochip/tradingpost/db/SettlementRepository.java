package dev.mintychochip.tradingpost.db;

import dev.mintychochip.tradingpost.domain.Settlement;
import dev.mintychochip.tradingpost.domain.SettlementKind;
import dev.mintychochip.tradingpost.domain.SettlementState;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class SettlementRepository {
    private final String schema;

    public SettlementRepository(String schema) {
        this.schema = schema;
    }

    public void insertReserved(Connection connection, SettlementDraft draft) throws SQLException {
        String sql = "INSERT INTO " + schema + ".settlements(id,kind,idempotency_key,fill_id,order_id,amount,state) VALUES(?,?,?,?,?,?, 'RESERVED')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, draft.id());
            statement.setString(2, draft.kind().name());
            statement.setString(3, draft.idempotencyKey());
            statement.setObject(4, draft.fillId());
            statement.setObject(5, draft.orderId());
            statement.setBigDecimal(6, draft.amount());
            statement.executeUpdate();
        }
    }

    public Optional<Settlement> find(Connection connection, UUID id, boolean lock) throws SQLException {
        String sql = selectSql() + " WHERE id=?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        }
    }

    public Optional<Settlement> leaseNext(Connection connection, String nodeId, Instant now) throws SQLException {
        String sql = selectSql() + " WHERE state IN ('RESERVED','MONEY_SETTLED') "
                + "AND (lease_until IS NULL OR lease_until < ?) ORDER BY updated_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sqlTime(now));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                Settlement before = read(rows);
                Instant leaseUntil = now.plusSeconds(30);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE " + schema + ".settlements SET lease_owner=?,lease_until=?,attempts=attempts+1,updated_at=? WHERE id=?")) {
                    update.setString(1, nodeId);
                    update.setObject(2, sqlTime(leaseUntil));
                    update.setObject(3, sqlTime(now));
                    update.setObject(4, before.id());
                    update.executeUpdate();
                }
                return find(connection, before.id(), true);
            }
        }
    }

    public void advance(Connection connection, UUID id, SettlementState from, SettlementState to, String error)
            throws SQLException {
        String sql = "UPDATE " + schema + ".settlements SET state=?,last_error=?,lease_owner=NULL,lease_until=NULL,updated_at=clock_timestamp() "
                + "WHERE id=? AND state=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, to.name());
            statement.setString(2, error);
            statement.setObject(3, id);
            statement.setString(4, from.name());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("settlement state transition lost race: " + id);
            }
        }
    }

    private String selectSql() {
        return "SELECT id,kind,idempotency_key,fill_id,order_id,amount,state,attempts,last_error,lease_owner,lease_until,created_at,updated_at "
                + "FROM " + schema + ".settlements";
    }

    private static Settlement read(ResultSet rows) throws SQLException {
        return new Settlement((UUID) rows.getObject(1), SettlementKind.valueOf(rows.getString(2)), rows.getString(3),
                (UUID) rows.getObject(4), (UUID) rows.getObject(5), rows.getBigDecimal(6),
                SettlementState.valueOf(rows.getString(7)), rows.getInt(8), rows.getString(9), rows.getString(10),
                instant(rows, 11), instant(rows, 12), instant(rows, 13));
    }

    private static Instant instant(ResultSet rows, int index) throws SQLException {
        java.time.OffsetDateTime value = rows.getObject(index, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static java.time.OffsetDateTime sqlTime(Instant value) {
        return java.time.OffsetDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
    }

    public record SettlementDraft(UUID id, SettlementKind kind, String idempotencyKey, UUID fillId, UUID orderId,
                                  BigDecimal amount) {
    }
}
