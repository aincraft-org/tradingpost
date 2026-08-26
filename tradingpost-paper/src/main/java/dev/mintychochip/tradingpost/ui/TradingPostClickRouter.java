package dev.mintychochip.tradingpost.ui;

import java.util.Objects;

/**
 * Maps handwritten inventory action ids onto host intents and domain handlers.
 *
 * <p>{@link TradingPostUi} dispatches clicks through this router so tests can assert wiring without
 * a live Paper server.
 */
public final class TradingPostClickRouter {
  private TradingPostClickRouter() {}

  public enum Intent {
    TAB_BROWSE,
    TAB_SELL,
    TAB_BUY_ORDERS,
    TAB_MY_ORDERS,
    PAGE_PREV,
    PAGE_NEXT,
    FILTER_HELD,
    FILTER_CLEAR,
    LISTING,
    SELL_PRICE_DEC,
    SELL_PRICE_INC,
    SELL_QTY_DEC,
    SELL_QTY_INC,
    SELL_DURATION,
    SELL_LIST,
    SELL_NOW,
    BUY_PRICE_DEC,
    BUY_PRICE_INC,
    BUY_QTY_DEC,
    BUY_QTY_INC,
    BUY_DURATION,
    BUY_EXACT,
    BUY_PLACE,
    DETAIL_QTY_DEC,
    DETAIL_QTY_INC,
    DETAIL_QTY_ALL,
    DETAIL_BUY,
    DETAIL_BUY_ALL,
    DETAIL_BACK,
    UNKNOWN
  }

  public enum DomainHandler {
    LIST,
    SELL_NOW,
    PLACE_BUY,
    BUY_NOW,
    CANCEL,
    FILTER,
    PAGE,
    TAB,
    NONE
  }

  public static Intent intent(String actionId) {
    if (actionId == null || actionId.isBlank()) {
      return Intent.UNKNOWN;
    }
    if (actionId.startsWith(TradingPostViews.ACTION_LISTING_PREFIX)) {
      return Intent.LISTING;
    }
    return switch (actionId) {
      case TradingPostViews.ACTION_TAB_BROWSE -> Intent.TAB_BROWSE;
      case TradingPostViews.ACTION_TAB_SELL -> Intent.TAB_SELL;
      case TradingPostViews.ACTION_TAB_BUY_ORDERS -> Intent.TAB_BUY_ORDERS;
      case TradingPostViews.ACTION_TAB_MY_ORDERS -> Intent.TAB_MY_ORDERS;
      case TradingPostViews.ACTION_PAGE_PREV -> Intent.PAGE_PREV;
      case TradingPostViews.ACTION_PAGE_NEXT -> Intent.PAGE_NEXT;
      case TradingPostViews.ACTION_FILTER_HELD -> Intent.FILTER_HELD;
      case TradingPostViews.ACTION_FILTER_CLEAR -> Intent.FILTER_CLEAR;
      case TradingPostViews.ACTION_SELL_PRICE_DEC -> Intent.SELL_PRICE_DEC;
      case TradingPostViews.ACTION_SELL_PRICE_INC -> Intent.SELL_PRICE_INC;
      case TradingPostViews.ACTION_SELL_QTY_DEC -> Intent.SELL_QTY_DEC;
      case TradingPostViews.ACTION_SELL_QTY_INC -> Intent.SELL_QTY_INC;
      case TradingPostViews.ACTION_SELL_DURATION -> Intent.SELL_DURATION;
      case TradingPostViews.ACTION_SELL_LIST -> Intent.SELL_LIST;
      case TradingPostViews.ACTION_SELL_NOW -> Intent.SELL_NOW;
      case TradingPostViews.ACTION_BUY_PRICE_DEC -> Intent.BUY_PRICE_DEC;
      case TradingPostViews.ACTION_BUY_PRICE_INC -> Intent.BUY_PRICE_INC;
      case TradingPostViews.ACTION_BUY_QTY_DEC -> Intent.BUY_QTY_DEC;
      case TradingPostViews.ACTION_BUY_QTY_INC -> Intent.BUY_QTY_INC;
      case TradingPostViews.ACTION_BUY_DURATION -> Intent.BUY_DURATION;
      case TradingPostViews.ACTION_BUY_EXACT -> Intent.BUY_EXACT;
      case TradingPostViews.ACTION_BUY_PLACE -> Intent.BUY_PLACE;
      case TradingPostViews.ACTION_DETAIL_QTY_DEC -> Intent.DETAIL_QTY_DEC;
      case TradingPostViews.ACTION_DETAIL_QTY_INC -> Intent.DETAIL_QTY_INC;
      case TradingPostViews.ACTION_DETAIL_QTY_ALL -> Intent.DETAIL_QTY_ALL;
      case TradingPostViews.ACTION_DETAIL_BUY -> Intent.DETAIL_BUY;
      case TradingPostViews.ACTION_DETAIL_BUY_ALL -> Intent.DETAIL_BUY_ALL;
      case TradingPostViews.ACTION_DETAIL_BACK -> Intent.DETAIL_BACK;
      default -> Intent.UNKNOWN;
    };
  }

  public static DomainHandler handler(Intent intent, TradingPostSession.Screen screen) {
    Objects.requireNonNull(intent, "intent");
    Objects.requireNonNull(screen, "screen");
    return switch (intent) {
      case SELL_LIST -> DomainHandler.LIST;
      case SELL_NOW -> DomainHandler.SELL_NOW;
      case BUY_PLACE -> DomainHandler.PLACE_BUY;
      case DETAIL_BUY, DETAIL_BUY_ALL -> DomainHandler.BUY_NOW;
      case LISTING ->
          switch (screen) {
            case MY_ORDERS -> DomainHandler.CANCEL;
            default -> DomainHandler.NONE;
          };
      case FILTER_HELD, FILTER_CLEAR -> DomainHandler.FILTER;
      case PAGE_PREV, PAGE_NEXT -> DomainHandler.PAGE;
      case TAB_BROWSE, TAB_SELL, TAB_BUY_ORDERS, TAB_MY_ORDERS, DETAIL_BACK -> DomainHandler.TAB;
      default -> DomainHandler.NONE;
    };
  }

  public static int listingIndex(String actionId) {
    if (actionId == null || !actionId.startsWith(TradingPostViews.ACTION_LISTING_PREFIX)) {
      return -1;
    }
    try {
      return Integer.parseInt(actionId.substring(TradingPostViews.ACTION_LISTING_PREFIX.length()));
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }
}
