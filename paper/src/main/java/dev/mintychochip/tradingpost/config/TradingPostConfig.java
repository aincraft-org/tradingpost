package dev.mintychochip.tradingpost.config;

import dev.jlo.mint.api.id.AccountId;
import dev.jlo.mint.api.id.ClientId;
import dev.jlo.mint.api.id.CurrencyId;
import dev.jlo.mint.api.id.NamespaceId;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;

public record TradingPostConfig(
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

  public static TradingPostConfig load(FileConfiguration config) {
    String jdbcUrl = required(config, "database.jdbc-url");
    String username = required(config, "database.username");
    String password = config.getString("database.password", "");
    String schema = required(config, "database.schema");
    int poolSize =
        positive(config.getInt("database.maximum-pool-size", 8), "database.maximum-pool-size");

    ClientId clientId = ClientId.of(NamespaceId.parse(required(config, "mint.client-id")));
    CurrencyId currencyId = CurrencyId.parse(required(config, "mint.currency-id"));
    AccountId escrow = AccountId.of(NamespaceId.parse(required(config, "mint.escrow-account")));
    AccountId fee = AccountId.of(NamespaceId.parse(required(config, "mint.fee-account")));
    AccountId tax = AccountId.of(NamespaceId.parse(required(config, "mint.tax-account")));

    int feeBps = bounded(config.getInt("market.fee-bps", 100), "market.fee-bps");
    int taxBps = bounded(config.getInt("market.tax-bps", 500), "market.tax-bps");
    int radius = positive(config.getInt("market.post-radius", 4), "market.post-radius");
    int maxSell = positive(config.getInt("market.max-sell-orders", 30), "market.max-sell-orders");
    int maxBuy = positive(config.getInt("market.max-buy-orders", 30), "market.max-buy-orders");
    int maxQuantity =
        positive(config.getInt("market.max-buy-quantity", 576), "market.max-buy-quantity");

    List<Duration> durations =
        config.getStringList("market.durations").stream()
            .map(TradingPostConfig::parseDuration)
            .toList();
    if (durations.isEmpty()) {
      throw new IllegalArgumentException("market.durations must not be empty");
    }

    return new TradingPostConfig(
        jdbcUrl,
        username,
        password,
        schema,
        poolSize,
        clientId,
        currencyId,
        escrow,
        fee,
        tax,
        feeBps,
        taxBps,
        radius,
        maxSell,
        maxBuy,
        maxQuantity,
        durations,
        Duration.ofSeconds(
            positive(
                config.getInt("market.expiry-sweep-seconds", 60), "market.expiry-sweep-seconds")),
        Duration.ofSeconds(
            positive(
                config.getInt("market.recovery-sweep-seconds", 15),
                "market.recovery-sweep-seconds")),
        Duration.ofSeconds(
            positive(
                config.getInt("market.reconciliation-seconds", 600),
                "market.reconciliation-seconds")),
        Duration.ofSeconds(
            positive(config.getInt("shutdown-grace-seconds", 10), "shutdown-grace-seconds")));
  }

  private static String required(FileConfiguration config, String path) {
    String value = config.getString(path);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(path + " must not be blank");
    }
    return value;
  }

  private static int positive(int value, String path) {
    if (value < 1) {
      throw new IllegalArgumentException(path + " must be positive");
    }
    return value;
  }

  private static int bounded(int value, String path) {
    if (value < 0 || value > 10_000) {
      throw new IllegalArgumentException(path + " must be between 0 and 10000");
    }
    return value;
  }

  private static Duration parseDuration(String value) {
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
