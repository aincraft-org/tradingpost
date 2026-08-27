package dev.mintychochip.tradingpost.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.tradingpost.territory.InMemoryTerritoryRegistry;
import org.junit.jupiter.api.Test;

class TradingPostTestPluginTest {
  @Test
  void demoTerritoriesCoverDistinctRegionsOfWorld() {
    InMemoryTerritoryRegistry registry = new InMemoryTerritoryRegistry();
    registry.register(DemoTerritories.SPAWN);
    registry.register(DemoTerritories.RIVERSIDE);
    assertEquals(2, registry.all().size());
    assertEquals("spawn", registry.findAt("world", 0, 64, 0).orElseThrow().id());
    assertEquals("riverside", registry.findAt("world", 200, 64, 200).orElseThrow().id());
    assertTrue(registry.findAt("world", 80, 64, 80).isEmpty());
  }
}
