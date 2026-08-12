package dev.mintychochip.tradingpost.command;

import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.post.TradingPostRegistry;
import dev.mintychochip.tradingpost.ui.TradingPostMenu;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

public final class TradingPostCommands implements CommandExecutor {
    private final JavaPlugin plugin;
    private final TradingPostRegistry registry;
    private final TradingPostMenu menu;
    private final TradingPostConfig config;
    private final boolean ready;

    public TradingPostCommands(JavaPlugin plugin, TradingPostRegistry registry, TradingPostMenu menu,
                               TradingPostConfig config, boolean ready) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.menu = Objects.requireNonNull(menu, "menu");
        this.config = Objects.requireNonNull(config, "config");
        this.ready = ready;
    }

    public void register() {
        Objects.requireNonNull(plugin.getCommand("post"), "post command").setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("postadmin"), "postadmin command").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (route(command.getName())) {
            case PLAYER -> handleAh(sender, args);
            case ADMIN -> handleAdmin(sender, args);
            case UNKNOWN -> false;
        };
    }

    static CommandRoute route(String commandName) {
        if ("post".equalsIgnoreCase(commandName)) return CommandRoute.PLAYER;
        if ("postadmin".equalsIgnoreCase(commandName)) return CommandRoute.ADMIN;
        return CommandRoute.UNKNOWN;
    }

    enum CommandRoute {
        PLAYER,
        ADMIN,
        UNKNOWN
    }

    private boolean handleAh(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the Trading Post.");
            return true;
        }
        if (!ready || !player.hasPermission("tradingpost.use")) {
            sender.sendMessage("Trading Post is not available.");
            return true;
        }
        int radius = config.postRadius();
        if (args.length == 0) {
            Optional<TradingPostRegistry.MarketContext> nearest = registry.nearestWithin(player.getLocation(), radius);
            if (nearest.isEmpty()) {
                sender.sendMessage("You must be within " + radius + " blocks of a Trading Post villager.");
                return true;
            }
            menu.open(player, nearest.get().marketName());
            return true;
        }
        String market = args[0];
        if (!registry.canAccess(player.getLocation(), market, radius)
                && !player.hasPermission("tradingpost.admin")) {
            sender.sendMessage("You must be within " + radius + " blocks of the villager for market '" + market + "'.");
            return true;
        }
        menu.open(player, market);
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tradingpost.admin")) {
            sender.sendMessage("You do not have permission.");
            return true;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("market") && args[1].equalsIgnoreCase("create")) {
            int fee;
            int tax;
            try {
                fee = args.length > 3 ? Integer.parseInt(args[3]) : 100;
                tax = args.length > 4 ? Integer.parseInt(args[4]) : 500;
            } catch (NumberFormatException invalid) {
                sender.sendMessage("Fee and tax must be integers in basis points.");
                return true;
            }
            registry.createMarket(args[2], args[2], fee, tax).whenComplete((ignored, failure) ->
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(failure == null
                            ? "Market created." : "Market creation failed: " + failure.getMessage())));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("post")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove"))) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can target a Trading Post villager.");
                return true;
            }
            RayTraceResult hit = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(), player.getEyeLocation().getDirection(), 8,
                    entity -> entity instanceof Villager);
            Entity target = hit == null ? null : hit.getHitEntity();
            if (!(target instanceof Villager villager)) {
                sender.sendMessage("Look at a villager within 8 blocks.");
                return true;
            }
            if (args[1].equalsIgnoreCase("remove") && registry.marketAt(villager).isEmpty()) {
                sender.sendMessage("That villager is not a registered Trading Post.");
                return true;
            }
            var operation = args[1].equalsIgnoreCase("set")
                    ? args.length < 3
                    ? null
                    : registry.registerPost(args[2], villager)
                    : registry.removePost(villager);
            if (operation == null) {
                sender.sendMessage("Usage: /postadmin post set <market> | post remove");
                return true;
            }
            operation.whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(failure == null ? "Villager Trading Post updated."
                            : "Trading Post update failed: " + failure.getMessage())));
            return true;
        }
        sender.sendMessage("Usage: /postadmin market create <name> [feeBps] [taxBps] | post set <market> | post remove");
        return true;
    }
}
