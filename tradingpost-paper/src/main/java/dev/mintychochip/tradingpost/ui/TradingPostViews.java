package dev.mintychochip.tradingpost.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Handwritten Paper chest layouts for every Trading Post screen.
 *
 * <p>Layouts declare fixed slots and action ids only. Host code owns data, navigation, and side
 * effects.
 */
public final class TradingPostViews {
  public static final String VIEW_BROWSE = "tp_browse";
  public static final String VIEW_SELL = "tp_sell";
  public static final String VIEW_BUY_ORDERS = "tp_buy_orders";
  public static final String VIEW_MY_ORDERS = "tp_my_orders";
  public static final String VIEW_MAILBOX = "tp_mailbox";
  public static final String VIEW_DETAIL = "tp_detail";

  public static final int LISTING_SLOTS = 36;
  public static final int MY_ORDER_SLOTS = 44;
  public static final int MAILBOX_SLOTS = 45;
  public static final int CHEST_SIZE = 54;
  public static final int DETAIL_SIZE = 27;

  public static final String ACTION_TAB_BROWSE = "tp.tab.browse";
  public static final String ACTION_TAB_SELL = "tp.tab.sell";
  public static final String ACTION_TAB_BUY_ORDERS = "tp.tab.buy_orders";
  public static final String ACTION_TAB_MY_ORDERS = "tp.tab.my_orders";
  public static final String ACTION_TAB_MAILBOX = "tp.tab.mailbox";
  public static final String ACTION_PAGE_PREV = "tp.page.prev";
  public static final String ACTION_PAGE_NEXT = "tp.page.next";

  public static final String ACTION_FILTER_HELD = "tp.browse.filter_held";
  public static final String ACTION_FILTER_CLEAR = "tp.browse.filter_clear";
  public static final String ACTION_LISTING_PREFIX = "tp.listing.";

  public static final String ACTION_SELL_PRICE_DEC = "tp.sell.price_dec";
  public static final String ACTION_SELL_PRICE_INC = "tp.sell.price_inc";
  public static final String ACTION_SELL_QTY_DEC = "tp.sell.qty_dec";
  public static final String ACTION_SELL_QTY_INC = "tp.sell.qty_inc";
  public static final String ACTION_SELL_DURATION = "tp.sell.duration";
  public static final String ACTION_SELL_LIST = "tp.sell.list";
  public static final String ACTION_SELL_NOW = "tp.sell.now";

  public static final String ACTION_BUY_PRICE_DEC = "tp.buy.price_dec";
  public static final String ACTION_BUY_PRICE_INC = "tp.buy.price_inc";
  public static final String ACTION_BUY_QTY_DEC = "tp.buy.qty_dec";
  public static final String ACTION_BUY_QTY_INC = "tp.buy.qty_inc";
  public static final String ACTION_BUY_DURATION = "tp.buy.duration";
  public static final String ACTION_BUY_EXACT = "tp.buy.exact";
  public static final String ACTION_BUY_PLACE = "tp.buy.place";

  public static final String ACTION_DETAIL_QTY_DEC = "tp.detail.qty_dec";
  public static final String ACTION_DETAIL_QTY_INC = "tp.detail.qty_inc";
  public static final String ACTION_DETAIL_QTY_ALL = "tp.detail.qty_all";
  public static final String ACTION_DETAIL_BUY = "tp.detail.buy";
  public static final String ACTION_DETAIL_BUY_ALL = "tp.detail.buy_all";
  public static final String ACTION_DETAIL_BACK = "tp.detail.back";

  private TradingPostViews() {}

  public record Slot(int index, String material, String label, String actionId, String bindPath) {
    public Slot {
      if (index < 0) {
        throw new IllegalArgumentException("slot index must not be negative");
      }
      Objects.requireNonNull(material, "material");
      Objects.requireNonNull(label, "label");
    }
  }

  public record ScreenLayout(String name, int size, String title, List<Slot> slots) {
    public ScreenLayout {
      Objects.requireNonNull(name, "name");
      if (size < 9) {
        throw new IllegalArgumentException("inventory size must be at least one row");
      }
      Objects.requireNonNull(title, "title");
      slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
    }

    public Slot slotAt(int index) {
      for (Slot slot : slots) {
        if (slot.index() == index) {
          return slot;
        }
      }
      return null;
    }
  }

  public static Map<String, ScreenLayout> all() {
    Map<String, ScreenLayout> views = new LinkedHashMap<>();
    for (ScreenLayout view :
        new ScreenLayout[] {browse(), sell(), buyOrders(), myOrders(), mailbox(), detail()}) {
      views.put(view.name(), view);
    }
    return Map.copyOf(views);
  }

  public static ScreenLayout browse() {
    List<Slot> slots = new ArrayList<>();
    for (int i = 0; i < LISTING_SLOTS; i++) {
      slots.add(
          new Slot(
              i,
              "minecraft:gray_stained_glass_pane",
              " ",
              ACTION_LISTING_PREFIX + i,
              "listings.s" + i));
    }
    slots.add(
        new Slot(
            36,
            "minecraft:hopper",
            "Filter: all materials (click: held item)",
            ACTION_FILTER_HELD,
            "filter_label"));
    slots.add(
        new Slot(37, "minecraft:barrier", "Clear material filter", ACTION_FILTER_CLEAR, null));
    fillNav(slots);
    return new ScreenLayout(VIEW_BROWSE, CHEST_SIZE, "Trading Post", slots);
  }

  public static ScreenLayout sell() {
    List<Slot> slots = new ArrayList<>();
    slots.add(
        new Slot(
            13, "minecraft:gray_stained_glass_pane", "Hold an item to sell", null, "sell.preview"));
    slots.add(
        new Slot(19, "minecraft:red_stained_glass_pane", "Price -1", ACTION_SELL_PRICE_DEC, null));
    slots.add(
        new Slot(
            20, "minecraft:orange_stained_glass_pane", "Price +1", ACTION_SELL_PRICE_INC, null));
    slots.add(
        new Slot(21, "minecraft:yellow_stained_glass_pane", "Qty -1", ACTION_SELL_QTY_DEC, null));
    slots.add(
        new Slot(22, "minecraft:lime_stained_glass_pane", "Qty +1", ACTION_SELL_QTY_INC, null));
    slots.add(
        new Slot(23, "minecraft:clock", "Duration", ACTION_SELL_DURATION, "sell.duration_label"));
    slots.add(new Slot(24, "minecraft:paper", "Sell Now: —", null, "sell.bid_label"));
    slots.add(
        new Slot(
            29,
            "minecraft:emerald",
            "List for sale (pays fee, sits on book)",
            ACTION_SELL_LIST,
            null));
    slots.add(new Slot(31, "minecraft:paper", "Draft", null, "sell.draft_label"));
    slots.add(
        new Slot(
            33,
            "minecraft:gold_ingot",
            "Sell Now (fee waived, match bids)",
            ACTION_SELL_NOW,
            null));
    fillNav(slots);
    return new ScreenLayout(VIEW_SELL, CHEST_SIZE, "Trading Post", slots);
  }

  public static ScreenLayout buyOrders() {
    List<Slot> slots = new ArrayList<>();
    for (int i = 0; i < LISTING_SLOTS; i++) {
      slots.add(
          new Slot(
              i,
              "minecraft:gray_stained_glass_pane",
              " ",
              ACTION_LISTING_PREFIX + i,
              "listings.s" + i));
    }
    slots.add(new Slot(37, "minecraft:paper", "Escrow", null, "buy.escrow_label"));
    slots.add(
        new Slot(
            38, "minecraft:red_stained_glass_pane", "Max price -1", ACTION_BUY_PRICE_DEC, null));
    slots.add(
        new Slot(
            39, "minecraft:lime_stained_glass_pane", "Max price +1", ACTION_BUY_PRICE_INC, null));
    slots.add(
        new Slot(
            40,
            "minecraft:book",
            "Place buy order: Hold sample item",
            ACTION_BUY_PLACE,
            "buy.place_label"));
    slots.add(
        new Slot(41, "minecraft:orange_stained_glass_pane", "Qty -1", ACTION_BUY_QTY_DEC, null));
    slots.add(
        new Slot(42, "minecraft:yellow_stained_glass_pane", "Qty +1", ACTION_BUY_QTY_INC, null));
    slots.add(
        new Slot(43, "minecraft:clock", "Duration", ACTION_BUY_DURATION, "buy.duration_label"));
    slots.add(
        new Slot(
            44,
            "minecraft:writable_book",
            "Exact item match",
            ACTION_BUY_EXACT,
            "buy.exact_label"));
    fillNav(slots);
    return new ScreenLayout(VIEW_BUY_ORDERS, CHEST_SIZE, "Trading Post", slots);
  }

  public static ScreenLayout myOrders() {
    List<Slot> slots = new ArrayList<>();
    for (int i = 0; i < MY_ORDER_SLOTS; i++) {
      slots.add(
          new Slot(
              i,
              "minecraft:gray_stained_glass_pane",
              " ",
              ACTION_LISTING_PREFIX + i,
              "listings.s" + i));
    }
    fillNav(slots);
    return new ScreenLayout(VIEW_MY_ORDERS, CHEST_SIZE, "Trading Post", slots);
  }

  public static ScreenLayout mailbox() {
    List<Slot> slots = new ArrayList<>();
    for (int i = 0; i < MAILBOX_SLOTS; i++) {
      slots.add(
          new Slot(
              i,
              "minecraft:gray_stained_glass_pane",
              " ",
              ACTION_LISTING_PREFIX + i,
              "listings.s" + i));
    }
    fillNav(slots);
    return new ScreenLayout(VIEW_MAILBOX, CHEST_SIZE, "Trading Post", slots);
  }

  public static ScreenLayout detail() {
    List<Slot> slots = new ArrayList<>();
    slots.add(new Slot(11, "minecraft:gray_stained_glass_pane", "—", null, "detail.preview"));
    slots.add(
        new Slot(12, "minecraft:red_stained_glass_pane", "Qty -1", ACTION_DETAIL_QTY_DEC, null));
    slots.add(
        new Slot(13, "minecraft:hopper", "Buy all", ACTION_DETAIL_QTY_ALL, "detail.all_label"));
    slots.add(
        new Slot(14, "minecraft:lime_stained_glass_pane", "Qty +1", ACTION_DETAIL_QTY_INC, null));
    slots.add(new Slot(15, "minecraft:emerald", "Buy Now", ACTION_DETAIL_BUY, "detail.buy_label"));
    slots.add(
        new Slot(
            16,
            "minecraft:gold_ingot",
            "Buy Now all",
            ACTION_DETAIL_BUY_ALL,
            "detail.buy_all_label"));
    slots.add(new Slot(22, "minecraft:arrow", "Back to Browse", ACTION_DETAIL_BACK, null));
    return new ScreenLayout(VIEW_DETAIL, DETAIL_SIZE, "Buy Now", slots);
  }

  public static Set<String> declaredActionIds() {
    Set<String> ids = new LinkedHashSet<>();
    for (ScreenLayout view : all().values()) {
      for (Slot slot : view.slots()) {
        if (slot.actionId() != null) {
          ids.add(slot.actionId());
        }
      }
    }
    return Set.copyOf(ids);
  }

  public static String viewNameFor(TradingPostSession.Screen screen) {
    return switch (Objects.requireNonNull(screen, "screen")) {
      case BROWSE -> VIEW_BROWSE;
      case SELL -> VIEW_SELL;
      case BUY_ORDERS -> VIEW_BUY_ORDERS;
      case MY_ORDERS -> VIEW_MY_ORDERS;
      case MAILBOX -> VIEW_MAILBOX;
      case DETAIL -> VIEW_DETAIL;
    };
  }

  public static ScreenLayout layoutFor(TradingPostSession.Screen screen) {
    return switch (Objects.requireNonNull(screen, "screen")) {
      case BROWSE -> browse();
      case SELL -> sell();
      case BUY_ORDERS -> buyOrders();
      case MY_ORDERS -> myOrders();
      case MAILBOX -> mailbox();
      case DETAIL -> detail();
    };
  }

  private static void fillNav(List<Slot> slots) {
    slots.add(new Slot(45, "minecraft:compass", "Browse & Buy", ACTION_TAB_BROWSE, null));
    slots.add(new Slot(46, "minecraft:chest", "Sell", ACTION_TAB_SELL, null));
    slots.add(new Slot(47, "minecraft:paper", "Buy Orders", ACTION_TAB_BUY_ORDERS, null));
    slots.add(new Slot(48, "minecraft:writable_book", "My Orders", ACTION_TAB_MY_ORDERS, null));
    slots.add(new Slot(49, "minecraft:ender_chest", "Mailbox", ACTION_TAB_MAILBOX, null));
    slots.add(new Slot(50, "minecraft:arrow", "Previous page", ACTION_PAGE_PREV, null));
    slots.add(new Slot(51, "minecraft:spectral_arrow", "Next page", ACTION_PAGE_NEXT, null));
  }
}
