package dev.mintychochip.tradingpost.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Asserts every action id declared on CraftUX views is registered by the host
 * wiring helper (real {@link TradingPostUi#hostActionIds()} + real view factories).
 */
class TradingPostActionWiringTest {

    @Test
    void hostRegistersEveryDeclaredViewActionId() {
        Set<String> declared = TradingPostViews.declaredActionIds();
        Set<String> registered = TradingPostUi.hostActionIds();
        for (String actionId : declared) {
            assertTrue(registered.contains(actionId),
                    "host missing action id declared by a view: " + actionId);
        }
    }

    @Test
    void hostRegistersDomainMutationActions() {
        Set<String> registered = TradingPostUi.hostActionIds();
        assertTrue(registered.contains(TradingPostViews.ACTION_SELL_LIST));
        assertTrue(registered.contains(TradingPostViews.ACTION_SELL_NOW));
        assertTrue(registered.contains(TradingPostViews.ACTION_BUY_PLACE));
        assertTrue(registered.contains(TradingPostViews.ACTION_DETAIL_BUY));
        assertTrue(registered.contains(TradingPostViews.ACTION_DETAIL_BUY_ALL));
        assertTrue(registered.contains(TradingPostViews.ACTION_FILTER_HELD));
        assertTrue(registered.contains(TradingPostViews.ACTION_PAGE_NEXT));
    }
}
