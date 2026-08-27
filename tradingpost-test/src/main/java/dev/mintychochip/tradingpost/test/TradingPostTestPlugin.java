package dev.mintychochip.tradingpost.test;

import dev.mintychochip.tradingpost.api.ItemDeliveryHandler;
import dev.mintychochip.tradingpost.api.TerritoryRegistry;
import dev.mintychochip.tradingpost.territory.InMemoryTerritoryRegistry;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradingPostTestPlugin extends JavaPlugin {
  @Override
  public void onEnable() {
    InMemoryTerritoryRegistry territories = new InMemoryTerritoryRegistry();
    territories.register(DemoTerritories.SPAWN);
    territories.register(DemoTerritories.RIVERSIDE);
    Bukkit.getServicesManager()
        .register(TerritoryRegistry.class, territories, this, ServicePriority.Normal);
    Bukkit.getServicesManager()
        .register(
            ItemDeliveryHandler.class,
            delivery -> {
              getLogger()
                  .info(
                      "test delivery "
                          + delivery.reason()
                          + " owner="
                          + delivery.owner()
                          + " id="
                          + delivery.deliveryId());
              return CompletableFuture.completedFuture(null);
            },
            this,
            ServicePriority.Normal);
    getLogger()
        .info(
            "Registered territories: "
                + territories.all().stream()
                    .map(
                        territory ->
                            territory.id()
                                + " market="
                                + territory.marketName()
                                + " world="
                                + territory.world()
                                + " ["
                                + territory.minX()
                                + ","
                                + territory.minY()
                                + ","
                                + territory.minZ()
                                + ".."
                                + territory.maxX()
                                + ","
                                + territory.maxY()
                                + ","
                                + territory.maxZ()
                                + "]")
                    .toList());
  }

  @Override
  public void onDisable() {
    Bukkit.getServicesManager().unregisterAll(this);
  }
}
