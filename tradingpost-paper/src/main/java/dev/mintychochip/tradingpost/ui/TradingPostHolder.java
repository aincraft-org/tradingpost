package dev.mintychochip.tradingpost.ui;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks a Bukkit inventory as an open Trading Post screen for a player. */
public final class TradingPostHolder implements InventoryHolder {
  private final UUID playerId;
  private Inventory inventory;

  public TradingPostHolder(UUID playerId) {
    this.playerId = Objects.requireNonNull(playerId, "playerId");
  }

  public UUID playerId() {
    return playerId;
  }

  void inventory(Inventory inventory) {
    this.inventory = Objects.requireNonNull(inventory, "inventory");
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }
}
