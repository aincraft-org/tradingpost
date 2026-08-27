package dev.mintychochip.tradingpost.territory;

import dev.mintychochip.tradingpost.api.Territory;
import dev.mintychochip.tradingpost.api.TerritoryRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryTerritoryRegistry implements TerritoryRegistry {
  private final CopyOnWriteArrayList<Territory> territories = new CopyOnWriteArrayList<>();

  @Override
  public void register(Territory territory) {
    Objects.requireNonNull(territory, "territory");
    territories.removeIf(existing -> existing.id().equals(territory.id()));
    territories.add(territory);
  }

  @Override
  public Optional<Territory> findAt(String world, int x, int y, int z) {
    for (Territory territory : territories) {
      if (territory.contains(world, x, y, z)) {
        return Optional.of(territory);
      }
    }
    return Optional.empty();
  }

  @Override
  public List<Territory> all() {
    return List.copyOf(new ArrayList<>(territories));
  }
}
