package dev.mintychochip.tradingpost.test;

import dev.mintychochip.tradingpost.api.Territory;

public final class DemoTerritories {
  public static final Territory SPAWN =
      new Territory("spawn", "spawn", "world", -64, -64, -64, 64, 320, 64);
  public static final Territory RIVERSIDE =
      new Territory("riverside", "riverside", "world", 128, -64, 128, 256, 320, 256);

  private DemoTerritories() {}
}
