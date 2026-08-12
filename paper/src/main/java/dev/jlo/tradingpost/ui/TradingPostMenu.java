package dev.jlo.tradingpost.ui;

import dev.jlo.tradingpost.config.TradingPostConfig;
import dev.jlo.tradingpost.db.Database;
import dev.jlo.tradingpost.lifecycle.AsyncExecutor;
import dev.jlo.tradingpost.mailbox.MailboxService;
import dev.jlo.tradingpost.market.OrderService;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry façade for Trading Post UI. All screens are CraftUX inventory views
 * owned by {@link TradingPostUi}; this class preserves the historical open API
 * used by commands and post access.
 */
public final class TradingPostMenu implements Listener {
    private final TradingPostUi ui;

    public TradingPostMenu(
            JavaPlugin plugin,
            Database database,
            TradingPostConfig config,
            OrderService orderService,
            MailboxService mailboxService,
            AsyncExecutor executor) {
        this.ui = new TradingPostUi(
                Objects.requireNonNull(plugin, "plugin"),
                Objects.requireNonNull(database, "database"),
                Objects.requireNonNull(config, "config"),
                Objects.requireNonNull(orderService, "orderService"),
                Objects.requireNonNull(mailboxService, "mailboxService"),
                Objects.requireNonNull(executor, "executor"));
    }

    /** Package-visible for tests and plugin wiring. */
    public TradingPostUi ui() {
        return ui;
    }

    public void open(Player player, String marketName) {
        ui.open(player, marketName);
    }

    public void open(Player player, String marketName, TradingPostSession.Screen screen, int page) {
        ui.open(player, marketName, screen, page);
    }

    public void shutdown() {
        ui.shutdown();
    }

    /** Registers quit cleanup on the same plugin as inventory listeners. */
    public Listener asListener() {
        return ui;
    }

    public Plugin pluginOf(Plugin plugin) {
        return plugin;
    }
}
