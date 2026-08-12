package dev.mintychochip.tradingpost.ui;

import dev.craftux.api.authoring.Expr;
import dev.craftux.api.authoring.Ui;
import dev.craftux.api.inventory.InventoryType;
import dev.craftux.api.model.InventoryTemplate.ItemTemplate;
import dev.craftux.api.model.UiView;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure CraftUX inventory views for every Trading Post screen.
 *
 * <p>Views declare fixed slots and {@code run_action} / inventory action ids only.
 * Host code owns data ({@code provide}/{@code changed}), navigation, and side effects.
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

    // Navigation (shared bottom row on 6-row chest)
    public static final String ACTION_TAB_BROWSE = "tp.tab.browse";
    public static final String ACTION_TAB_SELL = "tp.tab.sell";
    public static final String ACTION_TAB_BUY_ORDERS = "tp.tab.buy_orders";
    public static final String ACTION_TAB_MY_ORDERS = "tp.tab.my_orders";
    public static final String ACTION_TAB_MAILBOX = "tp.tab.mailbox";
    public static final String ACTION_PAGE_PREV = "tp.page.prev";
    public static final String ACTION_PAGE_NEXT = "tp.page.next";

    // Browse
    public static final String ACTION_FILTER_HELD = "tp.browse.filter_held";
    public static final String ACTION_FILTER_CLEAR = "tp.browse.filter_clear";
    public static final String ACTION_LISTING_PREFIX = "tp.listing.";

    // Sell
    public static final String ACTION_SELL_PRICE_DEC = "tp.sell.price_dec";
    public static final String ACTION_SELL_PRICE_INC = "tp.sell.price_inc";
    public static final String ACTION_SELL_QTY_DEC = "tp.sell.qty_dec";
    public static final String ACTION_SELL_QTY_INC = "tp.sell.qty_inc";
    public static final String ACTION_SELL_DURATION = "tp.sell.duration";
    public static final String ACTION_SELL_LIST = "tp.sell.list";
    public static final String ACTION_SELL_NOW = "tp.sell.now";

    // Buy orders draft
    public static final String ACTION_BUY_PRICE_DEC = "tp.buy.price_dec";
    public static final String ACTION_BUY_PRICE_INC = "tp.buy.price_inc";
    public static final String ACTION_BUY_QTY_DEC = "tp.buy.qty_dec";
    public static final String ACTION_BUY_QTY_INC = "tp.buy.qty_inc";
    public static final String ACTION_BUY_DURATION = "tp.buy.duration";
    public static final String ACTION_BUY_EXACT = "tp.buy.exact";
    public static final String ACTION_BUY_PLACE = "tp.buy.place";

    // My orders / mailbox listing clicks use ACTION_LISTING_PREFIX + index

    // Detail / Buy Now
    public static final String ACTION_DETAIL_QTY_DEC = "tp.detail.qty_dec";
    public static final String ACTION_DETAIL_QTY_INC = "tp.detail.qty_inc";
    public static final String ACTION_DETAIL_QTY_ALL = "tp.detail.qty_all";
    public static final String ACTION_DETAIL_BUY = "tp.detail.buy";
    public static final String ACTION_DETAIL_BUY_ALL = "tp.detail.buy_all";
    public static final String ACTION_DETAIL_BACK = "tp.detail.back";

    private TradingPostViews() {
    }

    /** All six screens keyed by view name. */
    public static Map<String, UiView> all() {
        Map<String, UiView> views = new LinkedHashMap<>();
        for (UiView view : new UiView[] {
            browse(), sell(), buyOrders(), myOrders(), mailbox(), detail()
        }) {
            views.put(view.name(), view);
        }
        return Map.copyOf(views);
    }

    public static UiView browse() {
        return Ui.view(VIEW_BROWSE)
                .inventory(inv -> {
                    inv.type(InventoryType.CHEST).rows(6).title(Expr.path("tp.title"));
                    for (int i = 0; i < LISTING_SLOTS; i++) {
                        inv.slot(i, boundListing("tp.listings.s" + i),
                                "listing_" + i, ACTION_LISTING_PREFIX + i);
                    }
                    inv.slot(36, boundSimple("tp.filter_label"), "filter", ACTION_FILTER_HELD);
                    inv.slot(37, item("minecraft:barrier", "Clear material filter"),
                            "filter_clear", ACTION_FILTER_CLEAR);
                    fillNav(inv);
                })
                .build();
    }

    public static UiView sell() {
        return Ui.view(VIEW_SELL)
                .inventory(inv -> {
                    inv.type(InventoryType.CHEST).rows(6).title(Expr.path("tp.title"));
                    inv.slot(13, boundListing("tp.sell.preview"));
                    inv.slot(19, item("minecraft:red_stained_glass_pane", "Price -1"),
                            "price_dec", ACTION_SELL_PRICE_DEC);
                    inv.slot(20, item("minecraft:orange_stained_glass_pane", "Price +1"),
                            "price_inc", ACTION_SELL_PRICE_INC);
                    inv.slot(21, item("minecraft:yellow_stained_glass_pane", "Qty -1"),
                            "qty_dec", ACTION_SELL_QTY_DEC);
                    inv.slot(22, item("minecraft:lime_stained_glass_pane", "Qty +1"),
                            "qty_inc", ACTION_SELL_QTY_INC);
                    inv.slot(23, boundSimple("tp.sell.duration_label"),
                            "duration", ACTION_SELL_DURATION);
                    inv.slot(24, boundSimple("tp.sell.bid_label"));
                    inv.slot(29, item("minecraft:emerald", "List for sale (pays fee, sits on book)"),
                            "list", ACTION_SELL_LIST);
                    inv.slot(31, boundSimple("tp.sell.draft_label"));
                    inv.slot(33, item("minecraft:gold_ingot", "Sell Now (fee waived, match bids)"),
                            "sell_now", ACTION_SELL_NOW);
                    fillNav(inv);
                })
                .build();
    }

    public static UiView buyOrders() {
        return Ui.view(VIEW_BUY_ORDERS)
                .inventory(inv -> {
                    inv.type(InventoryType.CHEST).rows(6).title(Expr.path("tp.title"));
                    for (int i = 0; i < LISTING_SLOTS; i++) {
                        inv.slot(i, boundListing("tp.listings.s" + i),
                                "listing_" + i, ACTION_LISTING_PREFIX + i);
                    }
                    inv.slot(37, boundSimple("tp.buy.escrow_label"));
                    inv.slot(38, item("minecraft:red_stained_glass_pane", "Max price -1"),
                            "price_dec", ACTION_BUY_PRICE_DEC);
                    inv.slot(39, item("minecraft:lime_stained_glass_pane", "Max price +1"),
                            "price_inc", ACTION_BUY_PRICE_INC);
                    inv.slot(40, boundSimple("tp.buy.place_label"),
                            "place", ACTION_BUY_PLACE);
                    inv.slot(41, item("minecraft:orange_stained_glass_pane", "Qty -1"),
                            "qty_dec", ACTION_BUY_QTY_DEC);
                    inv.slot(42, item("minecraft:yellow_stained_glass_pane", "Qty +1"),
                            "qty_inc", ACTION_BUY_QTY_INC);
                    inv.slot(43, boundSimple("tp.buy.duration_label"),
                            "duration", ACTION_BUY_DURATION);
                    inv.slot(44, boundSimple("tp.buy.exact_label"),
                            "exact", ACTION_BUY_EXACT);
                    fillNav(inv);
                })
                .build();
    }

    public static UiView myOrders() {
        return Ui.view(VIEW_MY_ORDERS)
                .inventory(inv -> {
                    inv.type(InventoryType.CHEST).rows(6).title(Expr.path("tp.title"));
                    for (int i = 0; i < MY_ORDER_SLOTS; i++) {
                        inv.slot(i, boundListing("tp.listings.s" + i),
                                "listing_" + i, ACTION_LISTING_PREFIX + i);
                    }
                    fillNav(inv);
                })
                .build();
    }

    public static UiView mailbox() {
        return Ui.view(VIEW_MAILBOX)
                .inventory(inv -> {
                    inv.type(InventoryType.CHEST).rows(6).title(Expr.path("tp.title"));
                    for (int i = 0; i < MAILBOX_SLOTS; i++) {
                        inv.slot(i, boundListing("tp.listings.s" + i),
                                "listing_" + i, ACTION_LISTING_PREFIX + i);
                    }
                    fillNav(inv);
                })
                .build();
    }

    public static UiView detail() {
        return Ui.view(VIEW_DETAIL)
                .inventory(inv -> {
                    inv.type(InventoryType.CHEST).rows(3).title(Expr.literal("Buy Now"));
                    inv.slot(11, boundListing("tp.detail.preview"));
                    inv.slot(12, item("minecraft:red_stained_glass_pane", "Qty -1"),
                            "qty_dec", ACTION_DETAIL_QTY_DEC);
                    inv.slot(13, boundSimple("tp.detail.all_label"),
                            "qty_all", ACTION_DETAIL_QTY_ALL);
                    inv.slot(14, item("minecraft:lime_stained_glass_pane", "Qty +1"),
                            "qty_inc", ACTION_DETAIL_QTY_INC);
                    inv.slot(15, boundSimple("tp.detail.buy_label"),
                            "buy", ACTION_DETAIL_BUY);
                    inv.slot(16, boundSimple("tp.detail.buy_all_label"),
                            "buy_all", ACTION_DETAIL_BUY_ALL);
                    inv.slot(22, item("minecraft:arrow", "Back to Browse"),
                            "back", ACTION_DETAIL_BACK);
                })
                .build();
    }

    /** Every inventory action id declared across all views (for host registration checks). */
    public static Set<String> declaredActionIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (UiView view : all().values()) {
            if (view.inventory() == null) {
                continue;
            }
            for (var slot : view.inventory().slots()) {
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

    private static void fillNav(Ui.InventoryBuilder inv) {
        inv.slot(45, item("minecraft:compass", "Browse & Buy"), "tab_browse", ACTION_TAB_BROWSE);
        inv.slot(46, item("minecraft:chest", "Sell"), "tab_sell", ACTION_TAB_SELL);
        inv.slot(47, item("minecraft:paper", "Buy Orders"), "tab_buy", ACTION_TAB_BUY_ORDERS);
        inv.slot(48, item("minecraft:writable_book", "My Orders"), "tab_my", ACTION_TAB_MY_ORDERS);
        inv.slot(49, item("minecraft:ender_chest", "Mailbox"), "tab_mail", ACTION_TAB_MAILBOX);
        inv.slot(50, item("minecraft:arrow", "Previous page"), "page_prev", ACTION_PAGE_PREV);
        inv.slot(51, item("minecraft:spectral_arrow", "Next page"), "page_next", ACTION_PAGE_NEXT);
    }

    private static ItemTemplate item(String material, String label) {
        return Ui.InventoryBuilder.item(material, label);
    }

    /** Bound material/label/amount/lore0-2 from a provider path prefix. */
    private static ItemTemplate boundListing(String path) {
        return new ItemTemplate(
                Expr.path(path + ".material").toNode(),
                Expr.path(path + ".amount").toNode(),
                Expr.path(path + ".label").toNode(),
                List.of(
                        Expr.path(path + ".lore0").toNode(),
                        Expr.path(path + ".lore1").toNode(),
                        Expr.path(path + ".lore2").toNode()));
    }

    /** Bound material + label only (amount 1, empty lore). */
    private static ItemTemplate boundSimple(String path) {
        return new ItemTemplate(
                Expr.path(path + ".material").toNode(),
                Expr.of("1").toNode(),
                Expr.path(path + ".label").toNode(),
                List.of());
    }
}
