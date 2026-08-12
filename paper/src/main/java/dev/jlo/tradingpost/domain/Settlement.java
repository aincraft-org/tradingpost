package dev.jlo.tradingpost.domain;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

public record Settlement(
        UUID id,
        SettlementKind kind,
        String idempotencyKey,
        UUID fillId,
        UUID orderId,
        BigDecimal amount,
        SettlementState state,
        int attempts,
        String lastError,
        String leaseOwner,
        Instant leaseUntil,
        Instant createdAt,
        Instant updatedAt) {
}
