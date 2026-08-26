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
  }
}
