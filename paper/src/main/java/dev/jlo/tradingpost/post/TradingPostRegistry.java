package dev.jlo.tradingpost.post;

import dev.jlo.tradingpost.db.Database;
import dev.jlo.tradingpost.db.MarketRepository;
import dev.jlo.tradingpost.domain.Market;
import dev.jlo.tradingpost.domain.TradingPostBlock;
import dev.jlo.tradingpost.lifecycle.AsyncExecutor;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradingPostRegistry {
    private final Database database;
    private final MarketRepository markets;
    private final AsyncExecutor executor;
    private final JavaPlugin plugin;
    private final Map<UUID, TradingPostBlock> posts = new ConcurrentHashMap<>();

    public TradingPostRegistry(Database database, String schema, AsyncExecutor executor, JavaPlugin plugin) {
        this.database = Objects.requireNonNull(database, "database");
        this.markets = new MarketRepository(schema);
        this.executor = Objects.requireNonNull(executor, "executor");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public CompletionStage<Integer> load() {
        return executor.submit(() -> {
            var loaded = database.transaction(markets::listPosts);
            posts.clear();
            loaded.stream()
                    .filter(post -> post.entityId() != null)
                    .forEach(post -> posts.put(post.entityId(), post));
            Bukkit.getScheduler().runTask(plugin, () -> loaded.forEach(post -> {
                if (post.entityId() == null) return;
                Entity entity = Bukkit.getEntity(post.entityId());
                if (entity instanceof Villager villager) protect(villager);
            }));
            return posts.size();
        });
    }

    public Optional<MarketContext> marketAt(Entity entity) {
        if (!(entity instanceof Villager)) return Optional.empty();
        TradingPostBlock post = posts.get(entity.getUniqueId());
        return post == null ? Optional.empty() : Optional.of(new MarketContext(post.marketName(), post.id()));
    }

    /**
     * Finds the nearest registered villager in the same world within {@code radius} blocks.
     */
    public Optional<MarketContext> nearestWithin(Location location, int radius) {
        if (location == null || location.getWorld() == null) return Optional.empty();
        return PostAccess.nearestWithin(posts.values(), location.getWorld().getName(),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ(), radius)
                .map(post -> new MarketContext(post.marketName(), post.id()));
    }

    /**
     * True when the player is within {@code radius} of the registered villager for {@code marketName}.
     */
    public boolean canAccess(Location location, String marketName, int radius) {
        if (location == null || location.getWorld() == null) return false;
        return PostAccess.canAccess(posts.values(), location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), marketName, radius);
    }

    public CompletionStage<Void> registerPost(String market, Villager villager) {
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(villager, "villager");
        if (villager.getWorld() == null) throw new IllegalArgumentException("villager world is required");
        TradingPostBlock post = new TradingPostBlock(UUID.randomUUID(), villager.getUniqueId(), market,
                villager.getWorld().getName(), villager.getLocation().getBlockX(),
                villager.getLocation().getBlockY(), villager.getLocation().getBlockZ());
        return executor.submit(() -> {
            database.transaction(connection -> {
                markets.find(connection, market)
                        .orElseThrow(() -> new IllegalArgumentException("market does not exist: " + market));
                if (markets.hasPostForMarket(connection, market)) {
                    throw new IllegalArgumentException("market is already assigned to a Trading Post: " + market);
                }
                markets.insertPost(connection, post);
                return null;
            });
            posts.put(post.entityId(), post);
            Bukkit.getScheduler().runTask(plugin, () -> protect(villager));
            return null;
        });
    }

    public CompletionStage<Void> createMarket(String name, String displayName, int feeBps, int taxBps) {
        Market market = new Market(name, displayName, feeBps, taxBps);
        return executor.submit(() -> database.transaction(connection -> {
            markets.insert(connection, market);
            return null;
        }));
    }

    public CompletionStage<Void> removePost(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        UUID entityId = entity.getUniqueId();
        return executor.submit(() -> {
            database.transaction(connection -> {
                markets.deletePostByEntity(connection, entityId);
                return null;
            });
            posts.remove(entityId);
            return null;
        });
    }

    public void protect(Villager villager) {
        if (villager == null) return;
        villager.setAI(false);
        villager.setInvulnerable(true);
    }

    public record MarketContext(String marketName, UUID postId) {
    }
}
