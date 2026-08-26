package dev.mintychochip.tradingpost.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Asserts every action id declared on handwritten screens is registered by the host, and that the
 * shipped click router maps list / sell-now / place-buy / buy-now / cancel / claim / filter /
 * pagination / tab actions to the real domain handlers.
 */
class TradingPostActionWiringTest {

  @Test
  void hostRegistersEveryDeclaredViewActionId() {
    Set<String> declared = TradingPostViews.declaredActionIds();
    Set<String> registered = TradingPostUi.hostActionIds();
    for (String actionId : declared) {
      assertTrue(
          registered.contains(actionId), "host missing action id declared by a view: " + actionId);
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

  @Test
  void routerMapsMutationsToShippedDomainHandlers() {
    assertEquals(
        TradingPostClickRouter.DomainHandler.LIST,
        TradingPostClickRouter.handler(
            TradingPostClickRouter.intent(TradingPostViews.ACTION_SELL_LIST),
            TradingPostSession.Screen.SELL));
    assertEquals(
        TradingPostClickRouter.DomainHandler.SELL_NOW,
        TradingPostClickRouter.handler(
            TradingPostClickRouter.intent(TradingPostViews.ACTION_SELL_NOW),
            TradingPostSession.Screen.SELL));
    assertEquals(
        TradingPostClickRouter.DomainHandler.PLACE_BUY,
        TradingPostClickRouter.handler(
            TradingPostClickRouter.intent(TradingPostViews.ACTION_BUY_PLACE),
            TradingPostSession.Screen.BUY_ORDERS));
    assertEquals(
        TradingPostClickRouter.DomainHandler.BUY_NOW,
        TradingPostClickRouter.handler(
            TradingPostClickRouter.intent(TradingPostViews.ACTION_DETAIL_BUY),
            TradingPostSession.Screen.DETAIL));
    assertEquals(
        TradingPostClickRouter.DomainHandler.BUY_NOW,
        TradingPostClickRouter.handler(
            TradingPostClickRouter.intent(TradingPostViews.ACTION_DETAIL_BUY_ALL),
            TradingPostSession.Screen.DETAIL));
    assertEquals(
        TradingPostClickRouter.DomainHandler.CANCEL,
        TradingPostClickRouter.handler(
            TradingPostClickRouter.intent(TradingPostViews.ACTION_LISTING_PREFIX + "0"),
            TradingPostSession.Screen.MY_ORDERS));
    assertEquals(
        TradingPostClickRouter.DomainHandler.CLAIM,
        TradingPostClickRouter.handler(
            TradingPostClickRouter.intent(TradingPostViews.ACTION_LISTING_PREFIX + "3"),
            TradingPostSession.Screen.MAILBOX));
    assertEquals(
        TradingPostClickRouter.DomainHandler.FILTER,
        TradingPostClickRouter.handler(
            TradingPostClickRouter.intent(TradingPostViews.ACTION_FILTER_HELD),
            TradingPostSession.Screen.BROWSE));
    assertEquals(
        TradingPostClickRouter.DomainHandler.PAGE,
        TradingPostClickRouter.handler(
            TradingPostClickRouter.intent(TradingPostViews.ACTION_PAGE_NEXT),
            TradingPostSession.Screen.BROWSE));
    assertEquals(
        TradingPostClickRouter.DomainHandler.TAB,
        TradingPostClickRouter.handler(
            TradingPostClickRouter.intent(TradingPostViews.ACTION_TAB_MAILBOX),
            TradingPostSession.Screen.BROWSE));
  }

  @Test
  void listingClickOnBrowseDoesNotCancelOrClaim() {
    TradingPostClickRouter.DomainHandler handler =
        TradingPostClickRouter.handler(
            TradingPostClickRouter.intent(TradingPostViews.ACTION_LISTING_PREFIX + "1"),
            TradingPostSession.Screen.BROWSE);
    assertNotEquals(TradingPostClickRouter.DomainHandler.CANCEL, handler);
    assertNotEquals(TradingPostClickRouter.DomainHandler.CLAIM, handler);
  }
}
