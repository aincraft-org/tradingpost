package dev.mintychochip.tradingpost.config;

import dev.mintychochip.mint.api.id.AccountId;
import dev.mintychochip.mint.api.id.ClientId;
import dev.mintychochip.mint.api.id.CurrencyId;
import dev.mintychochip.mint.api.id.NamespaceId;
import java.time.Duration;
import java.util.List;

public final class TestConfigs {
  private TestConfigs() {}

  public static TradingPostConfig jdbc(
      DatabaseEngine engine, String jdbcUrl, String username, String password, String schema) {
    return new TradingPostConfig(
        engine,
        jdbcUrl,
        username,
        password,
        schema,
        4,
        ClientId.of(NamespaceId.parse("tradingpost:plugin")),
        CurrencyId.parse("mint:credits"),
        AccountId.of(NamespaceId.parse("tradingpost:escrow")),
        AccountId.of(NamespaceId.parse("tradingpost:fees")),
        AccountId.of(NamespaceId.parse("tradingpost:tax")),
        100,
        500,
        4,
        30,
        30,
        576,
        List.of(Duration.ofHours(1)),
        Duration.ofMinutes(1),
        Duration.ofSeconds(15),
        Duration.ofMinutes(10),
        Duration.ofSeconds(1));
  }
}
