package dev.jlo.tradingpost.domain;

import java.util.UUID;

public record TradingPostBlock(UUID id, UUID entityId, String marketName, String world, int x, int y, int z) {
    public TradingPostBlock {
        if (id == null || marketName == null || marketName.isBlank() || world == null || world.isBlank()) {
            throw new IllegalArgumentException("trading post identity must not be blank");
        }
    }

    public TradingPostBlock(UUID id, String marketName, String world, int x, int y, int z) {
        this(id, null, marketName, world, x, y, z);
    }
}
