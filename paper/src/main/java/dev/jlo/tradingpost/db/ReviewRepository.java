package dev.jlo.tradingpost.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class ReviewRepository {
    private final String schema;

    public ReviewRepository(String schema) {
        this.schema = schema;
    }

    public void insert(Connection connection, UUID id, UUID player, String fingerprint, String detail) throws SQLException {
        String sql = "INSERT INTO " + schema + ".review_queue(id,player,fingerprint,detail) VALUES(?,?,?,?::jsonb)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, player);
            statement.setString(3, fingerprint);
            statement.setString(4, detail);
            statement.executeUpdate();
        }
    }
}
