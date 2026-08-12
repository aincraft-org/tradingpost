package dev.jlo.tradingpost.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jlo.tradingpost.domain.BuyOrder;
import dev.jlo.tradingpost.domain.OrderStatus;
import dev.jlo.tradingpost.domain.SellOrder;
import dev.jlo.tradingpost.domain.SellOrderMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private final MatchingEngine engine = new MatchingEngine();

    @Test
    void buyTakerConsumesCheapestAskFirstAndStopsAboveLimit() {
        BuyOrder buy = buy("DIAMOND", 64, "10.00", null, NOW);
        SellOrder expensive = sell("DIAMOND", 64, "11.00", "plain", NOW.plusSeconds(2));
        SellOrder cheap = sell("DIAMOND", 20, "8.00", "plain", NOW);

        List<MatchingEngine.MatchDecision> decisions = engine.matchBuyAgainstSells(buy, List.of(expensive, cheap));

        assertEquals(1, decisions.size());
        assertEquals(20, decisions.getFirst().quantity());
        assertEquals(new BigDecimal("8.00"), decisions.getFirst().executionPrice());
    }

    @Test
    void sellNowUsesHighestBidAndExactTemplate() {
        SellOrder sell = sell("DIAMOND", 64, "5.00", "enchanted", NOW);
        BuyOrder lower = buy("DIAMOND", 32, "5.00", "plain", NOW);
        BuyOrder higher = buy("DIAMOND", 16, "9.00", "enchanted", NOW.plusSeconds(1));

        List<MatchingEngine.MatchDecision> decisions = engine.matchSellNowAgainstBuys(sell, List.of(lower, higher));

        assertEquals(1, decisions.size());
        assertEquals(higher.id(), decisions.getFirst().buyOrderId());
        assertEquals(16, decisions.getFirst().quantity());
        assertEquals(new BigDecimal("9.00"), decisions.getFirst().executionPrice());
    }

    @Test
    void sellNowIgnoresBidsBelowSellerMinimumPrice() {
        SellOrder sell = sell("DIAMOND", 10, "10.00", "plain", NOW);
        BuyOrder below = buy("DIAMOND", 10, "9.00", null, NOW);
        BuyOrder atMin = buy("DIAMOND", 4, "10.00", null, NOW.plusSeconds(1));
        BuyOrder above = buy("DIAMOND", 4, "12.00", null, NOW.plusSeconds(2));

        List<MatchingEngine.MatchDecision> decisions =
                engine.matchSellNowAgainstBuys(sell, List.of(below, atMin, above));

        assertEquals(2, decisions.size());
        assertEquals(above.id(), decisions.get(0).buyOrderId());
        assertEquals(new BigDecimal("12.00"), decisions.get(0).executionPrice());
        assertEquals(atMin.id(), decisions.get(1).buyOrderId());
        assertEquals(new BigDecimal("10.00"), decisions.get(1).executionPrice());
    }

    private static BuyOrder buy(String material, int quantity, String price, String template, Instant created) {
        return new BuyOrder(UUID.randomUUID(), "spawn", UUID.randomUUID(), material, template,
                quantity, quantity, new BigDecimal(price), new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)),
                OrderStatus.OPEN, created.plusSeconds(3600), created);
    }

    private static SellOrder sell(String material, int quantity, String price, String fingerprint, Instant created) {
        return new SellOrder(UUID.randomUUID(), "spawn", UUID.randomUUID(), material, new byte[] {1}, fingerprint,
                quantity, quantity, new BigDecimal(price), SellOrderMode.NORMAL, OrderStatus.ACTIVE,
                created.plusSeconds(3600), created);
    }
}
