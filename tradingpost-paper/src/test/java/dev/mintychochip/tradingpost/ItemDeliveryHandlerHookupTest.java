package dev.mintychochip.tradingpost;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ItemDeliveryHandlerHookupTest {
  @Test
  void pluginRequiresRegisteredHandlerBeforeReady() throws Exception {
    Path source = Path.of("src/main/java/dev/mintychochip/tradingpost/TradingPostPlugin.java");
    if (!Files.isRegularFile(source)) {
      source =
          Path.of(
              "tradingpost-paper/src/main/java/dev/mintychochip/tradingpost/TradingPostPlugin.java");
    }
    String text = Files.readString(source);
    assertTrue(
        text.contains("Bukkit.getServicesManager().load(ItemDeliveryHandler.class)"),
        "TradingPost must bind ItemDeliveryHandler from ServicesManager");
    assertTrue(
        text.contains("deliveries == null") || text.contains("bound == null"),
        "READY must wait until a handler is registered");
    assertTrue(
        text.contains("Bukkit.getServicesManager().load(TerritoryRegistry.class)"),
        "TradingPost must bind TerritoryRegistry from ServicesManager");
    assertTrue(
        text.contains("territories == null") || text.contains("boundTerritories == null"),
        "READY must wait until a territory registry is registered");
  }
}
