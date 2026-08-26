package dev.mintychochip.tradingpost.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TradingPostConfigEngineTest {

  @ParameterizedTest
  @CsvSource({
    "postgresql,jdbc:postgresql://localhost:5432/mint",
    "mysql,jdbc:mysql://localhost:3306/mint",
    "mariadb,jdbc:mariadb://localhost:3306/mint",
    "sqlite,jdbc:sqlite:plugins/TradingPost/tradingpost.db"
  })
  void loadAcceptsEachEngineAndKeepsConnectionTarget(String engine, String jdbcUrl) {
    TradingPostConfig loaded = TradingPostConfigLoader.load(yaml(engine, jdbcUrl));
    assertEquals(DatabaseEngine.parse(engine), loaded.engine());
    assertEquals(jdbcUrl, loaded.jdbcUrl());
    assertEquals("tradingpost", loaded.schema());
    assertEquals(engine, loaded.engine().configName());
  }

  @Test
  void blankEngineIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> TradingPostConfigLoader.load(yaml("", "jdbc:x")));
  }

  @Test
  void unknownEngineIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TradingPostConfigLoader.load(yaml("oracle", "jdbc:x")));
  }

  private static YamlConfiguration yaml(String engine, String jdbcUrl) {
    YamlConfiguration config = new YamlConfiguration();
    config.set("database.engine", engine);
    config.set("database.jdbc-url", jdbcUrl);
    config.set("database.username", "mint");
    config.set("database.password", "change-me");
    config.set("database.schema", "tradingpost");
    config.set("database.maximum-pool-size", 8);
    config.set("mint.client-id", "tradingpost:plugin");
    config.set("mint.currency-id", "mint:credits");
    config.set("mint.escrow-account", "tradingpost:escrow");
    config.set("mint.fee-account", "tradingpost:fees");
    config.set("mint.tax-account", "tradingpost:tax");
    config.set("market.fee-bps", 100);
    config.set("market.tax-bps", 500);
    config.set("market.post-radius", 4);
    config.set("market.max-sell-orders", 30);
    config.set("market.max-buy-orders", 30);
    config.set("market.max-buy-quantity", 576);
    config.set("market.durations", java.util.List.of("1h"));
    config.set("market.expiry-sweep-seconds", 60);
    config.set("market.recovery-sweep-seconds", 15);
    config.set("market.reconciliation-seconds", 600);
    config.set("shutdown-grace-seconds", 10);
    return config;
  }
}
