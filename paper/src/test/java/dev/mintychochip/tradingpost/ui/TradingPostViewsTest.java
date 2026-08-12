package dev.mintychochip.tradingpost.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.craftux.api.model.UiView;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Drives real CraftUX view factories for every Trading Post screen. */
class TradingPostViewsTest {

    @Test
    void allSixScreensBuildAsCraftuxInventoryViews() {
        Map<String, UiView> views = TradingPostViews.all();
        assertEquals(6, views.size());
        assertTrue(views.containsKey(TradingPostViews.VIEW_BROWSE));
        assertTrue(views.containsKey(TradingPostViews.VIEW_SELL));
        assertTrue(views.containsKey(TradingPostViews.VIEW_BUY_ORDERS));
        assertTrue(views.containsKey(TradingPostViews.VIEW_MY_ORDERS));
        assertTrue(views.containsKey(TradingPostViews.VIEW_MAILBOX));
        assertTrue(views.containsKey(TradingPostViews.VIEW_DETAIL));

        for (UiView view : views.values()) {
            assertNotNull(view.inventory(), () -> view.name() + " must declare inventory");
            assertFalse(view.inventory().slots().isEmpty(),
                    () -> view.name() + " must declare slots");
            assertTrue(view.inventory().slots().stream().anyMatch(s -> s.actionId() != null),
                    () -> view.name() + " must declare at least one action slot");
        }
    }

    @Test
    void viewNameForMapsEverySessionScreen() {
        assertEquals(TradingPostViews.VIEW_BROWSE,
                TradingPostViews.viewNameFor(TradingPostSession.Screen.BROWSE));
        assertEquals(TradingPostViews.VIEW_SELL,
                TradingPostViews.viewNameFor(TradingPostSession.Screen.SELL));
        assertEquals(TradingPostViews.VIEW_BUY_ORDERS,
                TradingPostViews.viewNameFor(TradingPostSession.Screen.BUY_ORDERS));
        assertEquals(TradingPostViews.VIEW_MY_ORDERS,
                TradingPostViews.viewNameFor(TradingPostSession.Screen.MY_ORDERS));
        assertEquals(TradingPostViews.VIEW_MAILBOX,
                TradingPostViews.viewNameFor(TradingPostSession.Screen.MAILBOX));
        assertEquals(TradingPostViews.VIEW_DETAIL,
                TradingPostViews.viewNameFor(TradingPostSession.Screen.DETAIL));
    }

    @Test
    void declaredActionIdsAreNonEmptyAndIncludeNavigationAndDomainControls() {
        Set<String> declared = TradingPostViews.declaredActionIds();
        assertFalse(declared.isEmpty());
        assertTrue(declared.contains(TradingPostViews.ACTION_TAB_BROWSE));
        assertTrue(declared.contains(TradingPostViews.ACTION_TAB_SELL));
        assertTrue(declared.contains(TradingPostViews.ACTION_TAB_BUY_ORDERS));
        assertTrue(declared.contains(TradingPostViews.ACTION_TAB_MY_ORDERS));
        assertTrue(declared.contains(TradingPostViews.ACTION_TAB_MAILBOX));
        assertTrue(declared.contains(TradingPostViews.ACTION_SELL_LIST));
        assertTrue(declared.contains(TradingPostViews.ACTION_SELL_NOW));
        assertTrue(declared.contains(TradingPostViews.ACTION_BUY_PLACE));
        assertTrue(declared.contains(TradingPostViews.ACTION_DETAIL_BUY));
        assertTrue(declared.contains(TradingPostViews.ACTION_LISTING_PREFIX + "0"));
    }
}
