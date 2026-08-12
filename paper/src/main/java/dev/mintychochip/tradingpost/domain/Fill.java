package dev.mintychochip.tradingpost.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public record Fill(
        UUID id,
        String marketName,
        UUID sellOrderId,
        UUID buyOrderId,
        int quantity,
        BigDecimal unitPrice,
        byte[] itemBlob,
        byte[] remainingItemBlob,
        SettlementState status,
        Instant createdAt) {
    public Fill {
        itemBlob = itemBlob.clone();
        remainingItemBlob = remainingItemBlob.clone();
    }

    @Override
    public byte[] itemBlob() {
        return itemBlob.clone();
    }

    @Override
    public byte[] remainingItemBlob() {
        return remainingItemBlob.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Fill that)) return false;
        return id.equals(that.id) && Arrays.equals(itemBlob, that.itemBlob)
                && Arrays.equals(remainingItemBlob, that.remainingItemBlob);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + Arrays.hashCode(itemBlob) + Arrays.hashCode(remainingItemBlob);
    }
}
