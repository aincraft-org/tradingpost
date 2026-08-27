package dev.mintychochip.tradingpost.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerritoryTest {
  @Test
  void containsInclusiveCuboidAndNormalizesBounds() {
    Territory spawn = new Territory("spawn", "spawn", "world", 64, 80, 10, -64, 0, -10);
    assertEquals(-64, spawn.minX());
    assertEquals(64, spawn.maxX());
    assertEquals(0, spawn.minY());
    assertEquals(80, spawn.maxY());
    assertEquals(-10, spawn.minZ());
    assertEquals(10, spawn.maxZ());
    assertTrue(spawn.contains("world", 0, 64, 0));
    assertTrue(spawn.contains("world", -64, 0, -10));
    assertTrue(spawn.contains("world", 64, 80, 10));
    assertFalse(spawn.contains("world", 65, 64, 0));
    assertFalse(spawn.contains("nether", 0, 64, 0));
  }
}
