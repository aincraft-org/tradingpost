package dev.jlo.tradingpost.post;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class TradingPostListener implements Listener {
    private final TradingPostRegistry registry;
    private final BooleanSupplier ready;
    private final BiConsumer<Player, TradingPostRegistry.MarketContext> open;

    public TradingPostListener(TradingPostRegistry registry, BooleanSupplier ready,
                               BiConsumer<Player, TradingPostRegistry.MarketContext> open) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.ready = Objects.requireNonNull(ready, "ready");
        this.open = Objects.requireNonNull(open, "open");
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Villager villager && registry.marketAt(villager).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!ready.getAsBoolean() || !event.getPlayer().hasPermission("tradingpost.use")) return;
        registry.marketAt(event.getRightClicked()).ifPresent(context -> {
            event.setCancelled(true);
            open.accept(event.getPlayer(), context);
        });
    }
}
