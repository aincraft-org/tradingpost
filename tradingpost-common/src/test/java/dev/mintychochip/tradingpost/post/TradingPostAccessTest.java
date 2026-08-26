package dev.mintychochip.tradingpost.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.tradingpost.domain.TradingPostBlock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradingPostAccessTest {
  @Test
  void radiusChecksUseChebyshevCubeAndPreferNearest() {
    TradingPostBlock near =
        new TradingPostBlock(UUID.randomUUID(), UUID.randomUUID(), "spawn", "world", 100, 64, 100);
    TradingPostBlock far =
        new TradingPostBlock(UUID.randomUUID(), UUID.randomUUID(), "spawn", "world", 120, 64, 100);
    TradingPostBlock otherWorld =
        new TradingPostBlock(UUID.randomUUID(), UUID.randomUUID(), "spawn", "nether", 100, 64, 100);
    TradingPostBlock otherMarket =
        new TradingPostBlock(UUID.randomUUID(), UUID.randomUUID(), "hub", "world", 101, 64, 100);
    TradingPostBlock legacyBlock =
        new TradingPostBlock(UUID.randomUUID(), "legacy", "world", 102, 64, 101);
    List<TradingPostBlock> posts = List.of(near, far, otherWorld, otherMarket, legacyBlock);

    assertTrue(PostAccess.canAccess(posts, "world", 102, 64, 101, "spawn", 4));
    // Outside both spawn posts when radius is 4 (near@100, far@120).
    assertFalse(PostAccess.canAccess(posts, "world", 110, 64, 100, "spawn", 4));
    assertFalse(PostAccess.canAccess(posts, "world", 102, 64, 101, "hub", 0));
    assertFalse(PostAccess.canAccess(posts, "world", 100, 64, 100, "legacy", 4));
    assertTrue(PostAccess.canAccess(posts, "world", 101, 64, 100, "hub", 0));

    // hub@101 is closer to (102,64,101) than spawn@100.
    Optional<TradingPostBlock> nearest = PostAccess.nearestWithin(posts, "world", 102, 64, 101, 4);
    assertTrue(nearest.isPresent());
    assertEquals(otherMarket.id(), nearest.get().id());
    assertEquals("hub", nearest.get().marketName());
    assertTrue(PostAccess.nearestWithin(posts, "world", 150, 64, 100, 4).isEmpty());
  }
}
