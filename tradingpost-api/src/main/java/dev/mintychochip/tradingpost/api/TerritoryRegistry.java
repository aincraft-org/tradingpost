package dev.mintychochip.tradingpost.api;

import java.util.List;
import java.util.Optional;

/**
 * Startup-bound territory lookup.
 *
 * <p>Implementations register on Bukkit's {@code ServicesManager} before TradingPost becomes READY.
 */
public interface TerritoryRegistry {
  void register(Territory territory);

  Optional<Territory> findAt(String world, int x, int y, int z);

  List<Territory> all();
}
