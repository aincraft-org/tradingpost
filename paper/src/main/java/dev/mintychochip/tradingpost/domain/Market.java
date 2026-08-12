package dev.mintychochip.tradingpost.domain;

public record Market(String name, String displayName, int feeBps, int taxBps) {
    public Market {
        if (name == null || name.isBlank() || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("market names must not be blank");
        }
        if (feeBps < 0 || feeBps > 10_000 || taxBps < 0 || taxBps > 10_000) {
            throw new IllegalArgumentException("market rates must be basis points");
        }
    }
}
