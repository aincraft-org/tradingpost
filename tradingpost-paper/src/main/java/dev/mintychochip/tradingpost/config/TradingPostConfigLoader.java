package dev.mintychochip.tradingpost.config;

import dev.mintychochip.mint.api.id.AccountId;
import dev.mintychochip.mint.api.id.ClientId;
import dev.mintychochip.mint.api.id.CurrencyId;
import dev.mintychochip.mint.api.id.NamespaceId;
import java.time.Duration;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/** Loads {@link TradingPostConfig} from a Bukkit YAML configuration. */
public final class TradingPostConfigLoader {
  private TradingPostConfigLoader() {}

  public static TradingPostConfig load(FileConfiguration config) {
    DatabaseEngine engine =
        DatabaseEngine.parse(
            TradingPostConfig.required("database.engine", config.getString("database.engine")));
    String jdbcUrl =
        TradingPostConfig.required("database.jdbc-url", config.getString("database.jdbc-url"));
    String username =
        TradingPostConfig.required("database.username", config.getString("database.username"));
    String password = config.getString("database.password", "");
    String schema =
        TradingPostConfig.required("database.schema", config.getString("database.schema"));
    int poolSize =
        TradingPostConfig.positive(
            config.getInt("database.maximum-pool-size", 8), "database.maximum-pool-size");

    ClientId clientId =
        ClientId.of(
            NamespaceId.parse(
                TradingPostConfig.required("mint.client-id", config.getString("mint.client-id"))));
    CurrencyId currencyId =
        CurrencyId.parse(
            TradingPostConfig.required("mint.currency-id", config.getString("mint.currency-id")));
    AccountId escrow =
        AccountId.of(
            NamespaceId.parse(
                TradingPostConfig.required(
                    "mint.escrow-account", config.getString("mint.escrow-account"))));
    AccountId fee =
        AccountId.of(
            NamespaceId.parse(
                TradingPostConfig.required(
                    "mint.fee-account", config.getString("mint.fee-account"))));
    AccountId tax =
        AccountId.of(
            NamespaceId.parse(
                TradingPostConfig.required(
                    "mint.tax-account", config.getString("mint.tax-account"))));

    int feeBps = TradingPostConfig.bounded(config.getInt("market.fee-bps", 100), "market.fee-bps");
    int taxBps = TradingPostConfig.bounded(config.getInt("market.tax-bps", 500), "market.tax-bps");
    int radius =
        TradingPostConfig.positive(config.getInt("market.post-radius", 4), "market.post-radius");
    int maxSell =
        TradingPostConfig.positive(
            config.getInt("market.max-sell-orders", 30), "market.max-sell-orders");
    int maxBuy =
        TradingPostConfig.positive(
            config.getInt("market.max-buy-orders", 30), "market.max-buy-orders");
    int maxQuantity =
        TradingPostConfig.positive(
            config.getInt("market.max-buy-quantity", 576), "market.max-buy-quantity");

    List<Duration> durations =
        config.getStringList("market.durations").stream()
            .map(TradingPostConfig::parseDuration)
            .toList();
    if (durations.isEmpty()) {
      throw new IllegalArgumentException("market.durations must not be empty");
    }

    return new TradingPostConfig(
        engine,
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
            TradingPostConfig.positive(
                config.getInt("market.expiry-sweep-seconds", 60), "market.expiry-sweep-seconds")),
        Duration.ofSeconds(
            TradingPostConfig.positive(
                config.getInt("market.recovery-sweep-seconds", 15),
                "market.recovery-sweep-seconds")),
        Duration.ofSeconds(
            TradingPostConfig.positive(
                config.getInt("market.reconciliation-seconds", 600),
                "market.reconciliation-seconds")),
        Duration.ofSeconds(
            TradingPostConfig.positive(
                config.getInt("shutdown-grace-seconds", 10), "shutdown-grace-seconds")));
  }
}
