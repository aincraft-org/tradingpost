package dev.mintychochip.tradingpost.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BuyOrder(
        UUID id,
        String marketName,
        UUID buyer,
        String material,
        String templateFingerprint,
        int quantity,
        int quantityRemaining,
        BigDecimal unitPrice,
        BigDecimal escrowReserved,
        OrderStatus status,
        Instant expiresAt,
        Instant createdAt) {
}
