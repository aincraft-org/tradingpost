package dev.mintychochip.tradingpost.ui.craftux;

import static java.util.Objects.requireNonNull;

import dev.craftux.api.inventory.InventoryClick;
import dev.craftux.api.inventory.InventoryPort;
import dev.craftux.api.inventory.InventoryType;
import dev.craftux.api.inventory.InventoryView;
import dev.craftux.api.inventory.ItemSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Paper {@link InventoryPort} for CraftUX inventory sessions.
 *
 * <p>Mirrors craftux-paper's adapter so TradingPost can compile on Java 21 while
 * still using CraftUX api/common inventory runtime (craftux-paper targets JVM 25).
 */
public final class BukkitInventoryPort implements InventoryPort {

    private final Function<UUID, Player> players;
    private final Map<UUID, Inventory> inventories = new HashMap<>();

    public BukkitInventoryPort() {
        this(Bukkit::getPlayer);
    }

    public BukkitInventoryPort(Function<UUID, Player> players) {
        this.players = requireNonNull(players, "players");
    }

    @Override
    public void open(UUID audience, InventoryView view) {
        Player player = player(audience);
        Inventory inventory = createInventory(view);
        if (inventory == null) {
            throw new IllegalStateException("Bukkit returned no inventory");
        }
        inventories.put(audience, inventory);
        try {
            player.openInventory(inventory);
        } catch (RuntimeException failure) {
            inventories.remove(audience, inventory);
            throw failure;
        }
    }

    private static Inventory createInventory(InventoryView view) {
        Component title = Component.text(view.title());
        InventoryType type = view.grid().type();
        return switch (type) {
            case CHEST -> Bukkit.createInventory(null, view.grid().slotCount(), title);
            case HOPPER -> Bukkit.createInventory(
                    null, org.bukkit.event.inventory.InventoryType.HOPPER, title);
            case DROPPER -> Bukkit.createInventory(
                    null, org.bukkit.event.inventory.InventoryType.DROPPER, title);
            case DISPENSER -> Bukkit.createInventory(
                    null, org.bukkit.event.inventory.InventoryType.DISPENSER, title);
        };
    }

    @Override
    public void setItem(UUID audience, int slot, ItemSpec item) {
        Inventory inventory = inventory(audience);
        if (slot < 0 || slot >= inventory.getSize()) {
            throw new IllegalArgumentException("slot " + slot + " is outside the open inventory");
        }
        inventory.setItem(slot, itemStack(item));
    }

    @Override
    public void close(UUID audience) {
        Inventory inventory = inventories.remove(requireNonNull(audience, "audience"));
        if (inventory == null) {
            return;
        }
        player(audience).closeInventory();
    }

    public boolean owns(UUID audience, Inventory inventory) {
        return inventories.get(requireNonNull(audience, "audience")) == inventory;
    }

    @Override
    public void clicked(UUID audience, InventoryClick click) {
        requireNonNull(audience, "audience");
        requireNonNull(click, "click");
    }

    public static ItemStack itemStack(ItemSpec item) {
        requireNonNull(item, "item");
        Material material = Material.matchMaterial(item.material());
        if (material == null) {
            throw new IllegalArgumentException("unknown Bukkit material " + item.material());
        }
        ItemStack stack = new ItemStack(material, item.amount());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            if (item.label().isBlank() && item.lore().isEmpty()) {
                return stack;
            }
            throw new IllegalArgumentException("Bukkit returned no item meta for " + material);
        }
        if (!item.label().isBlank()) {
            meta.displayName(Component.text(item.label()));
        }
        if (!item.lore().isEmpty()) {
            List<Component> lore = item.lore().stream()
                    .map(value -> (Component) Component.text(value))
                    .toList();
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private Player player(UUID audience) {
        Player player = players.apply(requireNonNull(audience, "audience"));
        if (player == null) {
            throw new IllegalStateException("player " + audience + " is not online");
        }
        return player;
    }

    private Inventory inventory(UUID audience) {
        Inventory inventory = inventories.get(requireNonNull(audience, "audience"));
        if (inventory == null) {
            throw new IllegalStateException("audience " + audience + " has no open inventory");
        }
        return inventory;
    }
}
