package dev.mintychochip.tradingpost.config;

import dev.mintychochip.mint.api.id.AccountId;
import dev.mintychochip.mint.api.id.ClientId;
import dev.mintychochip.mint.api.id.CurrencyId;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record TradingPostConfig(
    DatabaseEngine engine,
    String jdbcUrl,
    String username,
    String password,
    String schema,
    int maximumPoolSize,
    ClientId clientId,
    CurrencyId currencyId,
    AccountId escrowAccount,
    AccountId feeAccount,
    AccountId taxAccount,
    int feeBps,
    int taxBps,
    int postRadius,
    int maxSellOrders,
    int maxBuyOrders,
    int maxBuyQuantity,
    List<Duration> durations,
    Duration expirySweep,
    Duration recoverySweep,
    Duration reconciliation,
    Duration shutdownGrace) {

  public static String required(String path, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(path + " must not be blank");
    }
    return value;
  }

  public static int positive(int value, String path) {
    if (value < 1) {
      throw new IllegalArgumentException(path + " must be positive");
    }
    return value;
  }

  public static int bounded(int value, String path) {
    if (value < 0 || value > 10_000) {
      throw new IllegalArgumentException(path + " must be between 0 and 10000");
    }
    return value;
  }

  public static Duration parseDuration(String value) {
    Objects.requireNonNull(value, "duration");
    if (value.length() < 2) {
      throw new IllegalArgumentException("invalid duration: " + value);
    }
    char suffix = value.charAt(value.length() - 1);
    long amount;
    try {
      amount = Long.parseLong(value.substring(0, value.length() - 1));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("invalid duration: " + value, exception);
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("duration must be positive: " + value);
    }
    return switch (suffix) {
      case 'm' -> Duration.ofMinutes(amount);
      case 'h' -> Duration.ofHours(amount);
      case 'd' -> Duration.ofDays(amount);
      default -> throw new IllegalArgumentException("duration suffix must be m, h, or d: " + value);
    };
  }
}
