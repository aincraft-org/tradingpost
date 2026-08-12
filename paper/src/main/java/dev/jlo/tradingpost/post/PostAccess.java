package dev.jlo.tradingpost.post;

import dev.jlo.tradingpost.domain.TradingPostBlock;
import java.util.Collection;
import java.util.Optional;

/** Pure radius/access helpers for villager trading posts. */
public final class PostAccess {
    private PostAccess() {
    }

    public static boolean withinRadius(int px, int py, int pz, TradingPostBlock post, int radius) {
        if (post == null || radius < 0) return false;
        return Math.abs(post.x() - px) <= radius
                && Math.abs(post.y() - py) <= radius
                && Math.abs(post.z() - pz) <= radius;
    }

    public static boolean canAccess(Collection<TradingPostBlock> posts, String world, int px, int py, int pz,
                                    String marketName, int radius) {
        if (posts == null || world == null || marketName == null || marketName.isBlank()) return false;
        for (TradingPostBlock post : posts) {
            if (post.entityId() == null || !post.world().equals(world) || !post.marketName().equals(marketName)) continue;
            if (withinRadius(px, py, pz, post, radius)) return true;
        }
        return false;
    }

    public static Optional<TradingPostBlock> nearestWithin(Collection<TradingPostBlock> posts, String world,
                                                           int px, int py, int pz, int radius) {
        if (posts == null || world == null || radius < 0) return Optional.empty();
        TradingPostBlock best = null;
        long bestDistance = Long.MAX_VALUE;
        for (TradingPostBlock post : posts) {
            if (post.entityId() == null || !post.world().equals(world) || !withinRadius(px, py, pz, post, radius)) continue;
            long distance = distanceSquared(px, py, pz, post);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = post;
            }
        }
        return Optional.ofNullable(best);
    }

    private static long distanceSquared(int px, int py, int pz, TradingPostBlock post) {
        long dx = post.x() - px;
        long dy = post.y() - py;
        long dz = post.z() - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
