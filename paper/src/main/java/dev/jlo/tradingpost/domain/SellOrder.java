package dev.jlo.tradingpost.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public record SellOrder(
        UUID id,
        String marketName,
        UUID seller,
        String material,
        byte[] itemBlob,
        String fingerprint,
        int quantity,
        int quantityRemaining,
        BigDecimal unitPrice,
        SellOrderMode mode,
        OrderStatus status,
        Instant expiresAt,
        Instant createdAt) {
    public SellOrder {
        itemBlob = itemBlob.clone();
    }

    @Override
    public byte[] itemBlob() {
        return itemBlob.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SellOrder that)) return false;
        return id.equals(that.id) && Arrays.equals(itemBlob, that.itemBlob);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + Arrays.hashCode(itemBlob);
    }
}
