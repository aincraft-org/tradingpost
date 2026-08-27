package dev.mintychochip.tradingpost.command;

import dev.mintychochip.tradingpost.api.Territory;
import dev.mintychochip.tradingpost.api.TerritoryRegistry;
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
  private final TerritoryRegistry territories;
  private final TradingPostMenu menu;
  private final TradingPostConfig config;
  private final boolean ready;

  public TradingPostCommands(
      JavaPlugin plugin,
      TradingPostRegistry registry,
      TerritoryRegistry territories,
      TradingPostMenu menu,
      TradingPostConfig config,
      boolean ready) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.territories = Objects.requireNonNull(territories, "territories");
    this.menu = Objects.requireNonNull(menu, "menu");
    this.config = Objects.requireNonNull(config, "config");
    this.ready = ready;
  }

  public boolean runPost(CommandSender sender, String[] args) {
    return handleAh(sender, args);
  }

  public boolean runAdmin(CommandSender sender, String[] args) {
    return handleAdmin(sender, args);
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
    Optional<Territory> territory =
        territories.findAt(
            player.getWorld().getName(),
            player.getLocation().getBlockX(),
            player.getLocation().getBlockY(),
            player.getLocation().getBlockZ());
    if (args.length == 0) {
      if (territory.isPresent()) {
        menu.open(player, territory.get().marketName());
        return true;
      }
      Optional<TradingPostRegistry.MarketContext> nearest =
          registry.nearestWithin(player.getLocation(), radius);
      if (nearest.isEmpty()) {
        sender.sendMessage(
            "You must be in a registered territory or near a Trading Post villager.");
        return true;
      }
      menu.open(player, nearest.get().marketName());
      return true;
    }
    // /post search <query> — hybrid search (portable LIKE on material, keeps exact filter)
    if (args[0].equalsIgnoreCase("search")) {
      String query =
          args.length == 1
              ? null
              : String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
      if (query != null
          && (query.isEmpty() || query.equals("*") || query.equalsIgnoreCase("clear")))
        query = null;
      String marketName;
      if (territory.isPresent()) {
        marketName = territory.get().marketName();
      } else {
        Optional<TradingPostRegistry.MarketContext> nearest =
            registry.nearestWithin(player.getLocation(), radius);
        if (nearest.isEmpty()) {
          sender.sendMessage(
              "You must be in a registered territory or near a Trading Post villager.");
          return true;
        }
        marketName = nearest.get().marketName();
      }
      menu.open(player, marketName);
      if (query == null) {
        menu.clearSearch(player);
      } else {
        if (query.length() > 64) query = query.substring(0, 64);
        menu.setSearchQuery(player, query);
      }
      return true;
    }
    if (args[0].equalsIgnoreCase("clear")) {
      String marketName;
      if (territory.isPresent()) {
        marketName = territory.get().marketName();
      } else {
        Optional<TradingPostRegistry.MarketContext> nearest =
            registry.nearestWithin(player.getLocation(), radius);
        if (nearest.isEmpty()) {
          sender.sendMessage(
              "You must be in a registered territory or near a Trading Post villager.");
          return true;
        }
        marketName = nearest.get().marketName();
      }
      menu.open(player, marketName);
      menu.clearSearch(player);
      return true;
    }
    String market = args[0];
    boolean inTerritory = territory.map(value -> value.marketName().equals(market)).orElse(false);
    if (!inTerritory
        && !registry.canAccess(player.getLocation(), market, radius)
        && !player.hasPermission("tradingpost.admin")) {
      sender.sendMessage(
          "You must be in territory '"
              + market
              + "' or within "
              + radius
              + " blocks of its villager.");
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
    if (args.length >= 3
        && args[0].equalsIgnoreCase("market")
        && args[1].equalsIgnoreCase("create")) {
      int fee;
      int tax;
      try {
        fee = args.length > 3 ? Integer.parseInt(args[3]) : 100;
        tax = args.length > 4 ? Integer.parseInt(args[4]) : 500;
      } catch (NumberFormatException invalid) {
        sender.sendMessage("Fee and tax must be integers in basis points.");
        return true;
      }
      registry
          .createMarket(args[2], args[2], fee, tax)
          .whenComplete(
              (ignored, failure) ->
                  Bukkit.getScheduler()
                      .runTask(
                          plugin,
                          () ->
                              sender.sendMessage(
                                  failure == null
                                      ? "Market created."
                                      : "Market creation failed: " + failure.getMessage())));
      return true;
    }
    if (args.length >= 2
        && args[0].equalsIgnoreCase("post")
        && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove"))) {
      if (!(sender instanceof Player player)) {
        sender.sendMessage("Only players can target a Trading Post villager.");
        return true;
      }
      RayTraceResult hit =
          player
              .getWorld()
              .rayTraceEntities(
                  player.getEyeLocation(),
                  player.getEyeLocation().getDirection(),
                  8,
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
      var operation =
          args[1].equalsIgnoreCase("set")
              ? args.length < 3 ? null : registry.registerPost(args[2], villager)
              : registry.removePost(villager);
      if (operation == null) {
        sender.sendMessage("Usage: /postadmin post set <market> | post remove");
        return true;
      }
      operation.whenComplete(
          (ignored, failure) ->
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () ->
                          sender.sendMessage(
                              failure == null
                                  ? "Villager Trading Post updated."
                                  : "Trading Post update failed: " + failure.getMessage())));
      return true;
    }
    sender.sendMessage(
        "Usage: /postadmin market create <name> [feeBps] [taxBps] | post set <market> | post remove");
    return true;
  }
}
