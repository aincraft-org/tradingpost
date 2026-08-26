package dev.mintychochip.tradingpost.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Drives the shipped handwritten screen layouts for every Trading Post screen. */
class TradingPostViewsTest {

  @Test
  void allSixScreensBuildAsNativeInventoryLayouts() {
    Map<String, TradingPostViews.ScreenLayout> views = TradingPostViews.all();
    assertEquals(5, views.size());
    assertTrue(views.containsKey(TradingPostViews.VIEW_BROWSE));
    assertTrue(views.containsKey(TradingPostViews.VIEW_SELL));
    assertTrue(views.containsKey(TradingPostViews.VIEW_BUY_ORDERS));
    assertTrue(views.containsKey(TradingPostViews.VIEW_MY_ORDERS));
    assertTrue(views.containsKey(TradingPostViews.VIEW_DETAIL));

    for (TradingPostViews.ScreenLayout view : views.values()) {
      assertNotNull(view.name(), () -> "layout must have a name");
      assertTrue(view.size() > 0, () -> view.name() + " must have a chest size");
      assertFalse(view.slots().isEmpty(), () -> view.name() + " must declare slots");
      assertTrue(
          view.slots().stream().anyMatch(s -> s.actionId() != null),
          () -> view.name() + " must declare at least one action slot");
    }
  }

  @Test
  void viewNameForMapsEverySessionScreen() {
    assertEquals(
        TradingPostViews.VIEW_BROWSE,
        TradingPostViews.viewNameFor(TradingPostSession.Screen.BROWSE));
    assertEquals(
        TradingPostViews.VIEW_SELL, TradingPostViews.viewNameFor(TradingPostSession.Screen.SELL));
    assertEquals(
        TradingPostViews.VIEW_BUY_ORDERS,
        TradingPostViews.viewNameFor(TradingPostSession.Screen.BUY_ORDERS));
    assertEquals(
        TradingPostViews.VIEW_MY_ORDERS,
        TradingPostViews.viewNameFor(TradingPostSession.Screen.MY_ORDERS));
    assertEquals(
        TradingPostViews.VIEW_DETAIL,
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
    assertTrue(declared.contains(TradingPostViews.ACTION_SELL_LIST));
    assertTrue(declared.contains(TradingPostViews.ACTION_SELL_NOW));
    assertTrue(declared.contains(TradingPostViews.ACTION_BUY_PLACE));
    assertTrue(declared.contains(TradingPostViews.ACTION_DETAIL_BUY));
    assertTrue(declared.contains(TradingPostViews.ACTION_LISTING_PREFIX + "0"));
  }

  @Test
  void sellScreenWiresListAndSellNowSlots() {
    TradingPostViews.ScreenLayout sell = TradingPostViews.sell();
    assertTrue(hasAction(sell, TradingPostViews.ACTION_SELL_LIST));
    assertTrue(hasAction(sell, TradingPostViews.ACTION_SELL_NOW));
  }

  @Test
  void buyOrdersScreenWiresPlaceBuy() {
    assertTrue(hasAction(TradingPostViews.buyOrders(), TradingPostViews.ACTION_BUY_PLACE));
  }

  @Test
  void detailScreenWiresBuyNow() {
    TradingPostViews.ScreenLayout detail = TradingPostViews.detail();
    assertTrue(hasAction(detail, TradingPostViews.ACTION_DETAIL_BUY));
    assertTrue(hasAction(detail, TradingPostViews.ACTION_DETAIL_BUY_ALL));
  }

  private static boolean hasAction(TradingPostViews.ScreenLayout layout, String actionId) {
    return layout.slots().stream().anyMatch(slot -> actionId.equals(slot.actionId()));
  }
}
