package dev.mintychochip.tradingpost.api;

import java.util.Objects;

/** Axis-aligned cuboid in one world. Coordinates are inclusive block positions. */
public record Territory(
    String id,
    String marketName,
    String world,
    int minX,
    int minY,
    int minZ,
    int maxX,
    int maxY,
    int maxZ) {
  public Territory {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(marketName, "marketName");
    Objects.requireNonNull(world, "world");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (marketName.isBlank()) {
      throw new IllegalArgumentException("marketName must not be blank");
    }
    if (world.isBlank()) {
      throw new IllegalArgumentException("world must not be blank");
    }
    int x0 = Math.min(minX, maxX);
    int x1 = Math.max(minX, maxX);
    int y0 = Math.min(minY, maxY);
    int y1 = Math.max(minY, maxY);
    int z0 = Math.min(minZ, maxZ);
    int z1 = Math.max(minZ, maxZ);
    minX = x0;
    maxX = x1;
    minY = y0;
    maxY = y1;
    minZ = z0;
    maxZ = z1;
  }

  public boolean contains(String worldName, int x, int y, int z) {
    if (worldName == null || !world.equals(worldName)) {
      return false;
    }
    return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
  }
}
