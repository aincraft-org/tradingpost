package dev.mintychochip.tradingpost.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.tradingpost.api.Territory;
import org.junit.jupiter.api.Test;

class InMemoryTerritoryRegistryTest {
  @Test
  void findAtReturnsTheTerritoryCoveringTheBlock() {
    InMemoryTerritoryRegistry registry = new InMemoryTerritoryRegistry();
    Territory spawn = new Territory("spawn", "spawn", "world", -8, 0, -8, 8, 128, 8);
    Territory riverside =
        new Territory("riverside", "riverside", "world", 100, 0, 100, 120, 128, 120);
    registry.register(spawn);
    registry.register(riverside);
    assertEquals(2, registry.all().size());
    assertEquals("spawn", registry.findAt("world", 0, 64, 0).orElseThrow().id());
    assertEquals("riverside", registry.findAt("world", 110, 64, 110).orElseThrow().id());
    assertTrue(registry.findAt("world", 50, 64, 50).isEmpty());
    assertTrue(registry.findAt("nether", 0, 64, 0).isEmpty());
  }

  @Test
  void registerReplacesTheSameId() {
    InMemoryTerritoryRegistry registry = new InMemoryTerritoryRegistry();
    registry.register(new Territory("spawn", "spawn", "world", -1, 0, -1, 1, 1, 1));
    registry.register(new Territory("spawn", "hub", "world", -2, 0, -2, 2, 2, 2));
    assertEquals(1, registry.all().size());
    assertEquals("hub", registry.findAt("world", 0, 1, 0).orElseThrow().marketName());
  }
}
