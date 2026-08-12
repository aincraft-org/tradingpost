package dev.mintychochip.tradingpost.ui.craftux;

import static java.util.Objects.requireNonNull;

import dev.craftux.api.inventory.ClickKind;
import dev.craftux.api.inventory.InventoryClick;
import dev.craftux.api.inventory.InventoryClickKind;
import dev.craftux.api.inventory.InventoryView;
import dev.craftux.common.inventory.InventoryRuntime;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;

/**
 * Bukkit inventory event bridge for CraftUX {@link InventoryRuntime}.
 *
 * <p>Same contract as craftux-paper's renderer; kept in-tree for Java 21 hosts.
 */
public final class PaperInventoryRenderer implements Listener {

  private final BukkitInventoryPort port;
  private final InventoryRuntime runtime;

  public PaperInventoryRenderer(Plugin plugin, BukkitInventoryPort port, InventoryRuntime runtime) {
    this.port = requireNonNull(port, "port");
    this.runtime = requireNonNull(runtime, "runtime");
    requireNonNull(plugin, "plugin").getServer().getPluginManager().registerEvents(this, plugin);
  }

  public InventoryRuntime runtime() {
    return runtime;
  }

  public void close(UUID audience) {
    runtime.close(audience);
  }

  public void closeAll() {
    runtime.closeAll();
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    UUID audience = player.getUniqueId();
    var session = runtime.session(audience);
    if (session.isEmpty() || !port.owns(audience, event.getInventory())) {
      return;
    }
    int slot = event.getRawSlot();
    int inventorySize = event.getInventory().getSize();
    if (slot < 0 || slot >= inventorySize) {
      if (shouldCancelOutsideClick(session.get().view(), slot, inventorySize)) {
        event.setCancelled(true);
      }
      return;
    }
    event.setCancelled(true);
    try {
      runtime.handleClick(
          new InventoryClick(
              audience, slot, normalize(event.getClick()), policyKind(event.getClick())));
    } catch (IllegalArgumentException | IllegalStateException ignored) {
      // Runtime rejected the click; cancellation protects the inventory.
    }
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    UUID audience = player.getUniqueId();
    var session = runtime.session(audience);
    if (session.isEmpty()
        || !port.owns(audience, event.getInventory())
        || !shouldCancelDrag(session.get().view())) {
      return;
    }
    event.setCancelled(true);
    int slot = event.getRawSlots().stream().filter(index -> index >= 0).findFirst().orElse(-1);
    if (slot < 0) {
      return;
    }
    try {
      runtime.handleClick(new InventoryClick(audience, slot, InventoryClickKind.DRAG));
    } catch (IllegalArgumentException | IllegalStateException ignored) {
      // Cancellation is the safe result.
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player player)) {
      return;
    }
    UUID audience = player.getUniqueId();
    if (port.owns(audience, event.getInventory())) {
      runtime.close(audience);
    }
  }

  public static boolean shouldCancelOutsideClick(
      InventoryView view, int rawSlot, int inventorySize) {
    requireNonNull(view, "view");
    if (inventorySize < 0) {
      throw new IllegalArgumentException("inventory size must not be negative");
    }
    return (rawSlot < 0 || rawSlot >= inventorySize)
        && view.interactionPolicy().cancelOutsideClicks();
  }

  public static boolean shouldCancelDrag(InventoryView view) {
    return requireNonNull(view, "view").interactionPolicy().cancelDrags();
  }

  public static ClickKind policyKind(ClickType click) {
    requireNonNull(click, "click");
    return switch (click) {
      case RIGHT -> ClickKind.RIGHT;
      case SHIFT_LEFT -> ClickKind.SHIFT_LEFT;
      case SHIFT_RIGHT -> ClickKind.SHIFT_RIGHT;
      case NUMBER_KEY, SWAP_OFFHAND -> ClickKind.NUMBER_KEY;
      case DOUBLE_CLICK -> ClickKind.LEFT;
      case DROP, CONTROL_DROP -> ClickKind.DROP;
      case CREATIVE, MIDDLE, WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT, UNKNOWN, LEFT ->
          ClickKind.LEFT;
    };
  }

  public static InventoryClickKind normalize(ClickType click) {
    requireNonNull(click, "click");
    return switch (click) {
      case RIGHT -> InventoryClickKind.PLACE;
      case SHIFT_LEFT, SHIFT_RIGHT -> InventoryClickKind.SHIFT_CLICK;
      case NUMBER_KEY, SWAP_OFFHAND -> InventoryClickKind.SWAP;
      case DOUBLE_CLICK -> InventoryClickKind.DOUBLE_CLICK;
      case DROP, CONTROL_DROP -> InventoryClickKind.DROP;
      case CREATIVE, MIDDLE, WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT, UNKNOWN, LEFT ->
          InventoryClickKind.PICKUP;
    };
  }
}
