package dev.mintychochip.tradingpost.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure draft/clamp helpers used by the CraftUX host session. */
class TradingPostSessionDraftTest {

    @Test
    void draftPriceAndQuantityMutationsPersistOnSameSession() {
        TradingPostSession session = new TradingPostSession(UUID.randomUUID(), "spawn");
        session.screen(TradingPostSession.Screen.BUY_ORDERS);
        assertEquals(new BigDecimal("1.00"), session.draftPrice());
        assertEquals(1, session.draftQuantity());

        session.adjustPrice(BigDecimal.ONE);
        session.adjustQuantity(2);

        assertEquals(new BigDecimal("2.00"), session.draftPrice());
        assertEquals(3, session.draftQuantity());

        // A brand-new session (wrong reopen path) would reset drafts.
        TradingPostSession reopened = new TradingPostSession(session.playerId(), session.marketName());
        assertEquals(new BigDecimal("1.00"), reopened.draftPrice());
        assertEquals(1, reopened.draftQuantity());
        // Live session retains mutated draft for placeBuy.
        assertEquals(new BigDecimal("2.00"), session.draftPrice());
        assertEquals(3, session.draftQuantity());
    }

    @Test
    void priceCannotGoBelowMinimum() {
        TradingPostSession session = new TradingPostSession(UUID.randomUUID(), "spawn");
        session.draftPrice(new BigDecimal("0.50"));
        session.adjustPrice(new BigDecimal("-1"));
        assertEquals(TradingPostSession.MIN_PRICE, session.draftPrice());
    }

    @Test
    void buyQuantityClampsToAvailable() {
        assertEquals(7, TradingPostSession.clampBuyQuantity(20, 7));
        assertEquals(1, TradingPostSession.clampBuyQuantity(0, 7));
        assertEquals(1, TradingPostSession.clampBuyQuantity(-3, 0));
    }

    @Test
    void slotIdMapSupportsListingClicks() {
        TradingPostSession session = new TradingPostSession(UUID.randomUUID(), "spawn");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        session.setSlotIds(java.util.List.of(first, second));
        assertEquals(first, session.slotIdAt(0));
        assertEquals(second, session.slotIdAt(1));
        assertNull(session.slotIdAt(2));
    }
}
