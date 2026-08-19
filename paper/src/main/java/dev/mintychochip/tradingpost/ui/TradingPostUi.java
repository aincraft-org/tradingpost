package dev.mintychochip.tradingpost.ui;

import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.db.Database;
import dev.mintychochip.tradingpost.db.MailboxRepository;
import dev.mintychochip.tradingpost.db.OrderRepository;
import dev.mintychochip.tradingpost.domain.BuyOrder;
import dev.mintychochip.tradingpost.domain.MailboxItem;
import dev.mintychochip.tradingpost.domain.OrderStatus;
import dev.mintychochip.tradingpost.domain.SellOrder;
import dev.mintychochip.tradingpost.items.ItemCodec;
import dev.mintychochip.tradingpost.lifecycle.AsyncExecutor;
import dev.mintychochip.tradingpost.mailbox.MailboxService;
import dev.mintychochip.tradingpost.market.OrderService;
import dev.mintychochip.tradingpost.money.MoneyMath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * Native Paper inventory host for Trading Post screens.
 *
 * <p>Layouts stay pure; this class owns sessions, item painting, click dispatch, navigation, and
 * domain side effects.
 */
public final class TradingPostUi implements Listener {
  private final Plugin plugin;
  private final Database database;
  private final OrderRepository orders;
  private final MailboxRepository mailbox;
  private final TradingPostConfig config;
  private final AsyncExecutor executor;
  private final OrderService orderService;
  private final MailboxService mailboxService;

  private final Map<UUID, TradingPostSession> sessions = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Object>> providerSnapshots = new ConcurrentHashMap<>();
  private final Map<UUID, Inventory> inventories = new ConcurrentHashMap<>();
  private final Set<UUID> switching = ConcurrentHashMap.newKeySet();

  public TradingPostUi(
      Plugin plugin,
      Database database,
      TradingPostConfig config,
      OrderService orderService,
      MailboxService mailboxService,
      AsyncExecutor executor) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.database = Objects.requireNonNull(database, "database");
    this.config = Objects.requireNonNull(config, "config");
    this.orderService = Objects.requireNonNull(orderService, "orderService");
    this.mailboxService = Objects.requireNonNull(mailboxService, "mailboxService");
    this.executor = Objects.requireNonNull(executor, "executor");
    this.orders = new OrderRepository(config);
    this.mailbox = new MailboxRepository(config);
  }

  /**
   * Action ids the host always registers — pure, no Bukkit. Used by unit tests to assert wiring
   * without constructing a live plugin host.
   */
  public static Set<String> hostActionIds() {
    Map<String, Boolean> ids = new LinkedHashMap<>();
    ids.put(TradingPostViews.ACTION_TAB_BROWSE, true);
    ids.put(TradingPostViews.ACTION_TAB_SELL, true);
    ids.put(TradingPostViews.ACTION_TAB_BUY_ORDERS, true);
    ids.put(TradingPostViews.ACTION_TAB_MY_ORDERS, true);
    ids.put(TradingPostViews.ACTION_TAB_MAILBOX, true);
    ids.put(TradingPostViews.ACTION_PAGE_PREV, true);
    ids.put(TradingPostViews.ACTION_PAGE_NEXT, true);
    ids.put(TradingPostViews.ACTION_FILTER_HELD, true);
    ids.put(TradingPostViews.ACTION_FILTER_CLEAR, true);
    for (int i = 0; i < TradingPostViews.MAILBOX_SLOTS; i++) {
      ids.put(TradingPostViews.ACTION_LISTING_PREFIX + i, true);
    }
    ids.put(TradingPostViews.ACTION_SELL_PRICE_DEC, true);
    ids.put(TradingPostViews.ACTION_SELL_PRICE_INC, true);
    ids.put(TradingPostViews.ACTION_SELL_QTY_DEC, true);
    ids.put(TradingPostViews.ACTION_SELL_QTY_INC, true);
    ids.put(TradingPostViews.ACTION_SELL_DURATION, true);
    ids.put(TradingPostViews.ACTION_SELL_LIST, true);
    ids.put(TradingPostViews.ACTION_SELL_NOW, true);
    ids.put(TradingPostViews.ACTION_BUY_PRICE_DEC, true);
    ids.put(TradingPostViews.ACTION_BUY_PRICE_INC, true);
    ids.put(TradingPostViews.ACTION_BUY_QTY_DEC, true);
    ids.put(TradingPostViews.ACTION_BUY_QTY_INC, true);
    ids.put(TradingPostViews.ACTION_BUY_DURATION, true);
    ids.put(TradingPostViews.ACTION_BUY_EXACT, true);
    ids.put(TradingPostViews.ACTION_BUY_PLACE, true);
    ids.put(TradingPostViews.ACTION_DETAIL_QTY_DEC, true);
    ids.put(TradingPostViews.ACTION_DETAIL_QTY_INC, true);
    ids.put(TradingPostViews.ACTION_DETAIL_QTY_ALL, true);
    ids.put(TradingPostViews.ACTION_DETAIL_BUY, true);
    ids.put(TradingPostViews.ACTION_DETAIL_BUY_ALL, true);
    ids.put(TradingPostViews.ACTION_DETAIL_BACK, true);
    return Set.copyOf(ids.keySet());
  }

  public Set<String> registeredActionIds() {
    return hostActionIds();
  }

  public void open(Player player, String marketName) {
    open(player, marketName, TradingPostSession.Screen.BROWSE, 0);
  }

  public void open(Player player, String marketName, TradingPostSession.Screen screen, int page) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(marketName, "marketName");
    UUID id = player.getUniqueId();
    TradingPostSession session =
        sessions.compute(
            id,
            (uuid, existing) -> {
              if (existing == null) {
                return new TradingPostSession(uuid, marketName);
              }
              existing.marketName(marketName);
              return existing;
            });
    session.screen(screen);
    session.page(page);
    if (screen != TradingPostSession.Screen.DETAIL) {
      session.detailOrderId(null);
    }
    seedLoadingSnapshot(session);
    show(player, session);
    loadScreenData(player, session);
  }

  public void close(Player player) {
    UUID id = player.getUniqueId();
    Inventory open = inventories.remove(id);
    sessions.remove(id);
    providerSnapshots.remove(id);
    if (open != null && player.getOpenInventory().getTopInventory().equals(open)) {
      player.closeInventory();
    }
  }

  public void shutdown() {
    for (UUID id : List.copyOf(sessions.keySet())) {
      Player player = Bukkit.getPlayer(id);
      if (player != null) {
        close(player);
      } else {
        inventories.remove(id);
        sessions.remove(id);
        providerSnapshots.remove(id);
      }
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    close(event.getPlayer());
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    Inventory top = event.getView().getTopInventory();
    if (!owns(player.getUniqueId(), top)) {
      return;
    }
    int raw = event.getRawSlot();
    if (raw < 0 || raw >= top.getSize()) {
      if (event.isShiftClick()) {
        event.setCancelled(true);
      }
      return;
    }
    event.setCancelled(true);
    TradingPostSession session = sessions.get(player.getUniqueId());
    if (session == null) {
      return;
    }
    TradingPostViews.Slot slot = TradingPostViews.layoutFor(session.screen()).slotAt(raw);
    if (slot == null || slot.actionId() == null) {
      return;
    }
    dispatch(player.getUniqueId(), slot.actionId());
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    Inventory top = event.getView().getTopInventory();
    if (!owns(player.getUniqueId(), top)) {
      return;
    }
    int size = top.getSize();
    for (int slot : event.getRawSlots()) {
      if (slot >= 0 && slot < size) {
        event.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player player)) {
      return;
    }
    UUID id = player.getUniqueId();
    if (switching.contains(id)) {
      return;
    }
    Inventory open = inventories.get(id);
    if (open != null && open.equals(event.getInventory())) {
      inventories.remove(id);
      sessions.remove(id);
      providerSnapshots.remove(id);
    }
  }

  /** Dispatches a declared action id through the shipped click router. */
  void dispatch(UUID audience, String actionId) {
    TradingPostClickRouter.Intent intent = TradingPostClickRouter.intent(actionId);
    switch (intent) {
      case TAB_BROWSE -> switchTab(audience, TradingPostSession.Screen.BROWSE);
      case TAB_SELL -> switchTab(audience, TradingPostSession.Screen.SELL);
      case TAB_BUY_ORDERS -> switchTab(audience, TradingPostSession.Screen.BUY_ORDERS);
      case TAB_MY_ORDERS -> switchTab(audience, TradingPostSession.Screen.MY_ORDERS);
      case TAB_MAILBOX -> switchTab(audience, TradingPostSession.Screen.MAILBOX);
      case PAGE_PREV -> page(audience, -1);
      case PAGE_NEXT -> page(audience, +1);
      case FILTER_HELD -> filterHeld(audience);
      case FILTER_CLEAR -> filterClear(audience);
      case LISTING -> listingClick(audience, TradingPostClickRouter.listingIndex(actionId));
      case SELL_PRICE_DEC -> sellPrice(audience, -1);
      case SELL_PRICE_INC -> sellPrice(audience, +1);
      case SELL_QTY_DEC -> sellQty(audience, -1);
      case SELL_QTY_INC -> sellQty(audience, +1);
      case SELL_DURATION -> sellDuration(audience);
      case SELL_LIST -> placeFromHand(audience, false);
      case SELL_NOW -> placeFromHand(audience, true);
      case BUY_PRICE_DEC -> buyPrice(audience, -1);
      case BUY_PRICE_INC -> buyPrice(audience, +1);
      case BUY_QTY_DEC -> buyQty(audience, -1);
      case BUY_QTY_INC -> buyQty(audience, +1);
      case BUY_DURATION -> buyDuration(audience);
      case BUY_EXACT -> buyExact(audience);
      case BUY_PLACE -> placeBuy(audience);
      case DETAIL_QTY_DEC -> detailQty(audience, -1);
      case DETAIL_QTY_INC -> detailQty(audience, +1);
      case DETAIL_QTY_ALL -> detailQtyAll(audience);
      case DETAIL_BUY -> confirmBuyNow(audience, false);
      case DETAIL_BUY_ALL -> confirmBuyNow(audience, true);
      case DETAIL_BACK -> switchTab(audience, TradingPostSession.Screen.BROWSE);
      case UNKNOWN -> {
        // Unregistered action ids are ignored.
      }
    }
  }

  private boolean owns(UUID audience, Inventory inventory) {
    return inventory != null && inventories.get(audience) == inventory;
  }

  private void show(Player player, TradingPostSession session) {
    TradingPostViews.ScreenLayout layout = TradingPostViews.layoutFor(session.screen());
    UUID id = player.getUniqueId();
    Inventory existing = inventories.get(id);
    if (existing != null && existing.getSize() == layout.size()) {
      paint(existing, session);
      return;
    }
    switching.add(id);
    try {
      TradingPostHolder holder = new TradingPostHolder(id);
      Inventory inventory =
          Bukkit.createInventory(holder, layout.size(), Component.text(titleFor(session)));
      holder.inventory(inventory);
      inventories.put(id, inventory);
      paint(inventory, session);
      player.openInventory(inventory);
    } finally {
      switching.remove(id);
    }
  }

  private static String titleFor(TradingPostSession session) {
    if (session.screen() == TradingPostSession.Screen.DETAIL) {
      return "Buy Now";
    }
    return "Trading Post · " + session.marketName();
  }

  private void paint(Inventory inventory, TradingPostSession session) {
    TradingPostViews.ScreenLayout layout = TradingPostViews.layoutFor(session.screen());
    Map<String, Object> root = providerSnapshots.get(session.playerId());
    inventory.clear();
    for (TradingPostViews.Slot slot : layout.slots()) {
      if (slot.index() >= inventory.getSize()) {
        continue;
      }
      inventory.setItem(slot.index(), stackFor(slot, root));
    }
  }

  @SuppressWarnings("unchecked")
  private static ItemStack stackFor(TradingPostViews.Slot slot, Map<String, Object> root) {
    String materialKey = slot.material();
    String label = slot.label();
    int amount = 1;
    List<String> lore = List.of();
    Map<String, Object> bound = bind(root, slot.bindPath());
    if (bound != null) {
      Object material = bound.get("material");
      if (material instanceof String value && !value.isBlank()) {
        materialKey = value;
      }
      Object boundLabel = bound.get("label");
      if (boundLabel instanceof String value) {
        label = value;
      }
      Object boundAmount = bound.get("amount");
      if (boundAmount instanceof Number number) {
        amount = Math.max(1, number.intValue());
      }
      String lore0 = string(bound.get("lore0"));
      String lore1 = string(bound.get("lore1"));
      String lore2 = string(bound.get("lore2"));
      if (lore0 != null || lore1 != null || lore2 != null) {
        lore =
            List.of(
                lore0 == null ? " " : lore0,
                lore1 == null ? " " : lore1,
                lore2 == null ? " " : lore2);
      }
    }
    return itemStack(materialKey, amount, label, lore);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> bind(Map<String, Object> root, String path) {
    if (root == null || path == null || path.isBlank()) {
      return null;
    }
    Object current = root;
    for (String part : path.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(part);
    }
    if (current instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return null;
  }

  private static String string(Object value) {
    return value instanceof String text ? text : null;
  }

  private static ItemStack itemStack(
      String materialKey, int amount, String label, List<String> lore) {
    Material material = Material.matchMaterial(materialKey);
    if (material == null) {
      material = Material.STONE;
    }
    ItemStack stack = new ItemStack(material, Math.min(64, Math.max(1, amount)));
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return stack;
    }
    if (label != null && !label.isBlank()) {
      meta.displayName(Component.text(label).decoration(TextDecoration.ITALIC, false));
    }
    if (lore != null && !lore.isEmpty()) {
      meta.lore(
          lore.stream()
              .map(
                  line -> (Component) Component.text(line).decoration(TextDecoration.ITALIC, false))
              .toList());
    }
    stack.setItemMeta(meta);
    return stack;
  }

  private void seedLoadingSnapshot(TradingPostSession session) {
    Map<String, Object> root = baseRoot(session);
    Map<String, Object> listings = new LinkedHashMap<>();
    int count = listingCapacity(session.screen());
    for (int i = 0; i < count; i++) {
      listings.put("s" + i, emptySlot("Loading..."));
    }
    root.put("listings", listings);
    putSellDefaults(root, session);
    putBuyDefaults(root, session);
    putDetailDefaults(root);
    providerSnapshots.put(session.playerId(), root);
  }

  private static int listingCapacity(TradingPostSession.Screen screen) {
    return switch (screen) {
      case MAILBOX -> TradingPostViews.MAILBOX_SLOTS;
      case MY_ORDERS -> TradingPostViews.MY_ORDER_SLOTS;
      case DETAIL -> 0;
      default -> TradingPostViews.LISTING_SLOTS;
    };
  }

  private Map<String, Object> baseRoot(TradingPostSession session) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("title", "Trading Post · " + session.marketName());
    root.put("market", session.marketName());
    root.put("page", session.page());
    root.put(
        "filter_label",
        simpleItem(
            "minecraft:hopper",
            session.materialFilter() == null
                ? "Filter: all materials (click: held item)"
                : "Filter: " + session.materialFilter() + " (click: held item)"));
    return root;
  }

  private void putSellDefaults(Map<String, Object> root, TradingPostSession session) {
    root.put(
        "sell",
        Map.of(
            "preview", emptySlot("Hold an item to sell"),
            "duration_label",
                simpleItem(
                    "minecraft:clock",
                    "Duration: "
                        + formatDuration(durationAt(session.durationIndex()))
                        + " (cycle)"),
            "bid_label", simpleItem("minecraft:paper", "Sell Now: —"),
            "draft_label",
                simpleItem(
                    "minecraft:paper",
                    "Draft "
                        + session.draftPrice().toPlainString()
                        + " x "
                        + session.draftQuantity())));
  }

  private void putBuyDefaults(Map<String, Object> root, TradingPostSession session) {
    BigDecimal escrow =
        MoneyMath.total(
            session.draftPrice(), session.draftQuantity(), session.draftPrice().scale());
    root.put(
        "buy",
        Map.of(
            "escrow_label",
                simpleItem(
                    "minecraft:paper",
                    "Escrow "
                        + escrow.toPlainString()
                        + " · "
                        + session.draftPrice().toPlainString()
                        + " x "
                        + session.draftQuantity()),
            "place_label", simpleItem("minecraft:book", "Place buy order: Hold sample item"),
            "duration_label",
                simpleItem(
                    "minecraft:clock",
                    "Duration: "
                        + formatDuration(durationAt(session.durationIndex()))
                        + " (cycle)"),
            "exact_label",
                simpleItem(
                    session.exactMatch() ? "minecraft:enchanted_book" : "minecraft:writable_book",
                    session.exactMatch()
                        ? "Exact item match: ON (held NBT)"
                        : "Exact item match: OFF (any of material)")));
  }

  private void putDetailDefaults(Map<String, Object> root) {
    root.put(
        "detail",
        Map.of(
            "preview", emptySlot("—"),
            "all_label", simpleItem("minecraft:hopper", "Buy all"),
            "buy_label", simpleItem("minecraft:emerald", "Buy Now"),
            "buy_all_label", simpleItem("minecraft:gold_ingot", "Buy Now all")));
  }

  private void loadScreenData(Player player, TradingPostSession session) {
    switch (session.screen()) {
      case BROWSE -> loadBrowse(player, session);
      case SELL -> renderSell(player, session);
      case BUY_ORDERS -> loadBuyOrders(player, session);
      case MY_ORDERS -> loadMyOrders(player, session);
      case MAILBOX -> loadMailbox(player, session);
      case DETAIL -> loadDetail(player, session);
    }
  }

  private void loadBrowse(Player player, TradingPostSession session) {
    String filter = session.materialFilter();
    int page = session.page();
    executor
        .submit(
            () ->
                database.transaction(
                    connection ->
                        orders.browseAsks(
                            connection,
                            session.marketName(),
                            filter,
                            page * TradingPostViews.LISTING_SLOTS,
                            TradingPostViews.LISTING_SLOTS)))
        .whenComplete(
            (asks, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!stillOn(player, session, TradingPostSession.Screen.BROWSE)) {
                            return;
                          }
                          if (failure != null) {
                            player.sendMessage(
                                Component.text("Trading Post is temporarily unavailable."));
                            return;
                          }
                          List<UUID> ids = new ArrayList<>();
                          Map<String, Object> listings = new LinkedHashMap<>();
                          for (int i = 0; i < TradingPostViews.LISTING_SLOTS; i++) {
                            if (i < asks.size()) {
                              SellOrder ask = asks.get(i);
                              ids.add(ask.id());
                              listings.put("s" + i, listingFromAsk(ask));
                            } else {
                              listings.put("s" + i, emptySlot(" "));
                            }
                          }
                          session.setSlotIds(ids);
                          Map<String, Object> root = baseRoot(session);
                          root.put("listings", listings);
                          putSellDefaults(root, session);
                          putBuyDefaults(root, session);
                          putDetailDefaults(root);
                          providerSnapshots.put(session.playerId(), root);
                          refresh(session.playerId());
                        }));
  }

  private void loadBuyOrders(Player player, TradingPostSession session) {
    int page = session.page();
    int limit = Math.min(TradingPostViews.LISTING_SLOTS - 9, 36);
    executor
        .submit(
            () ->
                database.transaction(
                    connection ->
                        orders.browseBids(connection, session.marketName(), page * limit, limit)))
        .whenComplete(
            (bids, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!stillOn(player, session, TradingPostSession.Screen.BUY_ORDERS)) {
                            return;
                          }
                          if (failure != null) {
                            player.sendMessage(
                                Component.text("Trading Post is temporarily unavailable."));
                            return;
                          }
                          List<UUID> ids = new ArrayList<>();
                          Map<String, Object> listings = new LinkedHashMap<>();
                          for (int i = 0; i < TradingPostViews.LISTING_SLOTS; i++) {
                            if (i < bids.size()) {
                              BuyOrder bid = bids.get(i);
                              ids.add(bid.id());
                              listings.put("s" + i, listingFromBid(bid));
                            } else {
                              listings.put("s" + i, emptySlot(" "));
                            }
                          }
                          session.setSlotIds(ids);
                          Map<String, Object> root = baseRoot(session);
                          root.put("listings", listings);
                          putSellDefaults(root, session);
                          putBuyDefaults(root, session);
                          putDetailDefaults(root);
                          applyBuyDraftLabels(root, player, session);
                          providerSnapshots.put(session.playerId(), root);
                          refresh(session.playerId());
                        }));
  }

  private void loadMyOrders(Player player, TradingPostSession session) {
    executor
        .submit(
            () ->
                database.transaction(
                    connection -> {
                      List<SellOrder> sells =
                          orders.listPlayerSells(
                              connection, session.marketName(), player.getUniqueId(), 0, 22);
                      List<BuyOrder> buys =
                          orders.listPlayerBuys(
                              connection, session.marketName(), player.getUniqueId(), 0, 22);
                      return new MyOrders(sells, buys);
                    }))
        .whenComplete(
            (data, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!stillOn(player, session, TradingPostSession.Screen.MY_ORDERS)) {
                            return;
                          }
                          if (failure != null) {
                            player.sendMessage(
                                Component.text("Trading Post is temporarily unavailable."));
                            return;
                          }
                          List<UUID> ids = new ArrayList<>();
                          Map<String, Object> listings = new LinkedHashMap<>();
                          int slot = 0;
                          for (SellOrder sell : data.sells()) {
                            if (slot >= 22) {
                              break;
                            }
                            ids.add(sell.id());
                            listings.put("s" + slot, listingFromMySell(sell));
                            slot++;
                          }
                          for (BuyOrder buy : data.buys()) {
                            if (slot >= TradingPostViews.MY_ORDER_SLOTS) {
                              break;
                            }
                            ids.add(buy.id());
                            listings.put("s" + slot, listingFromMyBuy(buy));
                            slot++;
                          }
                          while (slot < TradingPostViews.MY_ORDER_SLOTS) {
                            listings.put("s" + slot, emptySlot(" "));
                            slot++;
                          }
                          session.setSlotIds(ids);
                          Map<String, Object> root = baseRoot(session);
                          root.put("listings", listings);
                          putSellDefaults(root, session);
                          putBuyDefaults(root, session);
                          putDetailDefaults(root);
                          providerSnapshots.put(session.playerId(), root);
                          refresh(session.playerId());
                        }));
  }

  private void loadMailbox(Player player, TradingPostSession session) {
    executor
        .submit(
            () ->
                database.transaction(
                    connection ->
                        mailbox.list(connection, player.getUniqueId(), session.marketName())))
        .whenComplete(
            (items, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!stillOn(player, session, TradingPostSession.Screen.MAILBOX)) {
                            return;
                          }
                          if (failure != null) {
                            player.sendMessage(
                                Component.text("Trading Post is temporarily unavailable."));
                            return;
                          }
                          List<UUID> ids = new ArrayList<>();
                          Map<String, Object> listings = new LinkedHashMap<>();
                          for (int i = 0; i < TradingPostViews.MAILBOX_SLOTS; i++) {
                            if (i < items.size()) {
                              MailboxItem row = items.get(i);
                              ids.add(row.id());
                              listings.put("s" + i, listingFromMailbox(row));
                            } else {
                              listings.put("s" + i, emptySlot(" "));
                            }
                          }
                          session.setSlotIds(ids);
                          Map<String, Object> root = baseRoot(session);
                          root.put("listings", listings);
                          putSellDefaults(root, session);
                          putBuyDefaults(root, session);
                          putDetailDefaults(root);
                          providerSnapshots.put(session.playerId(), root);
                          refresh(session.playerId());
                        }));
  }

  private void renderSell(Player player, TradingPostSession session) {
    ItemStack hand = player.getInventory().getItemInMainHand();
    int qty =
        hand == null || hand.getType().isAir()
            ? session.draftQuantity()
            : Math.min(session.draftQuantity(), hand.getAmount());
    Duration duration = durationAt(session.durationIndex());
    BigDecimal gross =
        MoneyMath.total(session.draftPrice(), Math.max(1, qty), session.draftPrice().scale());
    BigDecimal fee = MoneyMath.basisPoints(gross, config.feeBps(), gross.scale());
    BigDecimal tax = MoneyMath.basisPoints(gross, config.taxBps(), gross.scale());
    BigDecimal net = MoneyMath.sellerNet(gross, tax, gross.scale());

    Map<String, Object> root = baseRoot(session);
    root.put("listings", emptyListings(TradingPostViews.LISTING_SLOTS));
    Map<String, Object> sell = new LinkedHashMap<>();
    if (hand != null && !hand.getType().isAir()) {
      sell.put(
          "preview",
          itemMap(
              materialKey(hand.getType().getKey().toString()),
              Math.min(64, Math.max(1, qty)),
              hand.getType().getKey().getKey(),
              "Unit price (min for Sell Now): " + session.draftPrice().toPlainString(),
              "Quantity: " + qty + " · Duration: " + formatDuration(duration),
              "Fee "
                  + fee.toPlainString()
                  + " · tax "
                  + tax.toPlainString()
                  + " · net "
                  + net.toPlainString()));
      String material = hand.getType().getKey().toString();
      executor
          .submit(
              () ->
                  database.transaction(
                      connection -> orders.bestBid(connection, session.marketName(), material)))
          .whenComplete(
              (bid, failure) ->
                  Bukkit.getScheduler()
                      .runTask(
                          plugin,
                          () -> {
                            if (!stillOn(player, session, TradingPostSession.Screen.SELL)) {
                              return;
                            }
                            Map<String, Object> live = providerSnapshots.get(session.playerId());
                            if (live == null) {
                              return;
                            }
                            @SuppressWarnings("unchecked")
                            Map<String, Object> sellMap =
                                new LinkedHashMap<>(
                                    (Map<String, Object>) live.getOrDefault("sell", Map.of()));
                            if (failure != null || bid.isEmpty()) {
                              sellMap.put(
                                  "bid_label",
                                  simpleItem(
                                      "minecraft:paper", "Sell Now: no buy orders for this item"));
                            } else {
                              BuyOrder best = bid.get();
                              boolean meetsMin =
                                  best.unitPrice().compareTo(session.draftPrice()) >= 0;
                              sellMap.put(
                                  "bid_label",
                                  simpleItem(
                                      "minecraft:paper",
                                      meetsMin
                                          ? "Best bid: "
                                              + best.unitPrice().toPlainString()
                                              + " x"
                                              + best.quantityRemaining()
                                              + " (meets your min)"
                                          : "Best bid: "
                                              + best.unitPrice().toPlainString()
                                              + " (below your min "
                                              + session.draftPrice().toPlainString()
                                              + ")"));
                            }
                            live.put("sell", sellMap);
                            refresh(session.playerId());
                          }));
    } else {
      sell.put("preview", emptySlot("Hold an item to sell"));
      sell.put("bid_label", simpleItem("minecraft:paper", "Sell Now: —"));
    }
    sell.put(
        "duration_label",
        simpleItem("minecraft:clock", "Duration: " + formatDuration(duration) + " (cycle)"));
    sell.put(
        "draft_label",
        simpleItem(
            "minecraft:paper",
            "Draft "
                + session.draftPrice().toPlainString()
                + " x "
                + qty
                + " · fee "
                + fee.toPlainString()));
    if (!sell.containsKey("bid_label")) {
      sell.put("bid_label", simpleItem("minecraft:paper", "Sell Now: —"));
    }
    root.put("sell", sell);
    putBuyDefaults(root, session);
    putDetailDefaults(root);
    providerSnapshots.put(session.playerId(), root);
    refresh(session.playerId());
  }

  private void loadDetail(Player player, TradingPostSession session) {
    UUID orderId = session.detailOrderId();
    if (orderId == null) {
      open(player, session.marketName(), TradingPostSession.Screen.BROWSE, 0);
      return;
    }
    executor
        .submit(
            () -> database.transaction(connection -> orders.findSell(connection, orderId, false)))
        .whenComplete(
            (order, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!stillOn(player, session, TradingPostSession.Screen.DETAIL)) {
                            return;
                          }
                          if (failure != null || order.isEmpty()) {
                            player.sendMessage(
                                Component.text("That order is no longer available."));
                            open(player, session.marketName(), TradingPostSession.Screen.BROWSE, 0);
                            return;
                          }
                          SellOrder ask = order.get();
                          session.detailMaxQuantity(Math.max(1, ask.quantityRemaining()));
                          session.detailBuyQuantity(
                              TradingPostSession.clampBuyQuantity(
                                  session.detailBuyQuantity(), session.detailMaxQuantity()));
                          applyDetailSnapshot(session, ask);
                          refresh(session.playerId());
                        }));
  }

  private void applyDetailSnapshot(TradingPostSession session, SellOrder ask) {
    int qty =
        TradingPostSession.clampBuyQuantity(session.detailBuyQuantity(), ask.quantityRemaining());
    session.detailBuyQuantity(qty);
    session.detailMaxQuantity(Math.max(1, ask.quantityRemaining()));
    BigDecimal total = MoneyMath.total(ask.unitPrice(), qty, ask.unitPrice().scale());
    int allQty = ask.quantityRemaining();
    BigDecimal allTotal =
        MoneyMath.total(ask.unitPrice(), Math.max(1, allQty), ask.unitPrice().scale());

    Map<String, Object> root = baseRoot(session);
    root.put("listings", emptyListings(1));
    putSellDefaults(root, session);
    putBuyDefaults(root, session);
    root.put(
        "detail",
        Map.of(
            "preview",
                itemMap(
                    materialKey(ask.material()),
                    Math.min(64, Math.max(1, ask.quantityRemaining())),
                    shortMaterial(ask.material()),
                    "Unit price: " + ask.unitPrice().toPlainString(),
                    "Available: " + ask.quantityRemaining(),
                    "Exact item match (NBT / enchants)"),
            "all_label", simpleItem("minecraft:hopper", "Buy all (" + allQty + ")"),
            "buy_label",
                simpleItem(
                    "minecraft:emerald", "Buy Now x" + qty + " · escrow " + total.toPlainString()),
            "buy_all_label",
                simpleItem(
                    "minecraft:gold_ingot",
                    "Buy Now all x" + allQty + " · " + allTotal.toPlainString())));
    providerSnapshots.put(session.playerId(), root);
  }

  private void applyBuyDraftLabels(
      Map<String, Object> root, Player player, TradingPostSession session) {
    ItemStack hand = player.getInventory().getItemInMainHand();
    String material =
        hand == null || hand.getType().isAir()
            ? "Hold sample item"
            : hand.getType().getKey().toString();
    Duration duration = durationAt(session.durationIndex());
    int qty = session.draftQuantity();
    BigDecimal escrow = MoneyMath.total(session.draftPrice(), qty, session.draftPrice().scale());
    root.put(
        "buy",
        Map.of(
            "escrow_label",
                simpleItem(
                    "minecraft:paper",
                    "Escrow "
                        + escrow.toPlainString()
                        + " · "
                        + session.draftPrice().toPlainString()
                        + " x "
                        + qty),
            "place_label", simpleItem("minecraft:book", "Place buy order: " + material),
            "duration_label",
                simpleItem("minecraft:clock", "Duration: " + formatDuration(duration) + " (cycle)"),
            "exact_label",
                simpleItem(
                    session.exactMatch() ? "minecraft:enchanted_book" : "minecraft:writable_book",
                    session.exactMatch()
                        ? "Exact item match: ON (held NBT)"
                        : "Exact item match: OFF (any of material)")));
  }

  private void refreshBuyDraft(Player player, TradingPostSession session) {
    Map<String, Object> root = providerSnapshots.get(session.playerId());
    if (root == null) {
      root = baseRoot(session);
      root.put("listings", emptyListings(TradingPostViews.LISTING_SLOTS));
      putSellDefaults(root, session);
      putDetailDefaults(root);
    } else {
      root = new LinkedHashMap<>(root);
    }
    applyBuyDraftLabels(root, player, session);
    providerSnapshots.put(session.playerId(), root);
    refresh(session.playerId());
  }

  private boolean stillOn(
      Player player, TradingPostSession session, TradingPostSession.Screen screen) {
    if (!player.isOnline()) {
      return false;
    }
    TradingPostSession live = sessions.get(player.getUniqueId());
    return live == session && live.screen() == screen;
  }

  private void refresh(UUID audience) {
    Player player = Bukkit.getPlayer(audience);
    Inventory inventory = inventories.get(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || inventory == null || session == null) {
      return;
    }
    paint(inventory, session);
  }

  private void switchTab(UUID audience, TradingPostSession.Screen screen) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null) {
      return;
    }
    open(player, session.marketName(), screen, 0);
  }

  private void page(UUID audience, int delta) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null) {
      return;
    }
    if (session.screen() != TradingPostSession.Screen.BROWSE
        && session.screen() != TradingPostSession.Screen.BUY_ORDERS) {
      return;
    }
    int next = session.page() + delta;
    if (next < 0) {
      return;
    }
    open(player, session.marketName(), session.screen(), next);
  }

  private void filterHeld(UUID audience) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null) {
      return;
    }
    ItemStack hand = player.getInventory().getItemInMainHand();
    if (hand == null || hand.getType().isAir()) {
      player.sendMessage(Component.text("Hold an item to filter Browse & Buy by material."));
      return;
    }
    session.materialFilter(hand.getType().getKey().toString());
    open(player, session.marketName(), TradingPostSession.Screen.BROWSE, 0);
  }

  private void filterClear(UUID audience) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null) {
      return;
    }
    session.materialFilter(null);
    open(player, session.marketName(), TradingPostSession.Screen.BROWSE, 0);
  }

  private void listingClick(UUID audience, int index) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null || index < 0) {
      return;
    }
    UUID target = session.slotIdAt(index);
    if (target == null) {
      return;
    }
    switch (session.screen()) {
      case BROWSE -> openDetail(player, session, target);
      case MY_ORDERS -> cancelOrder(player, session, target);
      case MAILBOX -> claimMailbox(player, session, target);
      default -> {
        // Listing clicks on other screens have no domain effect.
      }
    }
  }

  private void openDetail(Player player, TradingPostSession session, UUID orderId) {
    session.detailOrderId(orderId);
    session.detailBuyQuantity(1);
    session.screen(TradingPostSession.Screen.DETAIL);
    seedLoadingSnapshot(session);
    show(player, session);
    loadDetail(player, session);
  }

  private void cancelOrder(Player player, TradingPostSession session, UUID orderId) {
    orderService
        .cancel(player, orderId)
        .whenComplete(
            (result, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (failure != null || !result.accepted()) {
                            player.sendMessage(
                                Component.text(
                                    "Cancel failed: "
                                        + (failure == null
                                            ? result.message()
                                            : failure.getMessage())));
                          } else {
                            player.sendMessage(Component.text(result.message()));
                          }
                          open(
                              player, session.marketName(), TradingPostSession.Screen.MY_ORDERS, 0);
                        }));
  }

  private void claimMailbox(Player player, TradingPostSession session, UUID mailboxId) {
    mailboxService
        .claim(player, mailboxId)
        .whenComplete(
            (result, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (failure != null || !result.claimed()) {
                            player.sendMessage(
                                Component.text(
                                    "Claim failed: "
                                        + (failure == null
                                            ? result.message()
                                            : failure.getMessage())));
                          } else {
                            player.sendMessage(Component.text(result.message()));
                          }
                          open(player, session.marketName(), TradingPostSession.Screen.MAILBOX, 0);
                        }));
  }

  private void sellPrice(UUID audience, int delta) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null || session.screen() != TradingPostSession.Screen.SELL) {
      return;
    }
    session.adjustPrice(BigDecimal.valueOf(delta));
    renderSell(player, session);
  }

  private void sellQty(UUID audience, int delta) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null || session.screen() != TradingPostSession.Screen.SELL) {
      return;
    }
    session.adjustQuantity(delta);
    renderSell(player, session);
  }

  private void sellDuration(UUID audience) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null || session.screen() != TradingPostSession.Screen.SELL) {
      return;
    }
    session.cycleDuration(config.durations().size());
    renderSell(player, session);
  }

  private void placeFromHand(UUID audience, boolean sellNow) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null) {
      return;
    }
    ItemStack hand = player.getInventory().getItemInMainHand();
    if (hand == null || hand.getType().isAir()) {
      player.sendMessage(Component.text("Hold the item you want to sell."));
      return;
    }
    int slot = player.getInventory().getHeldItemSlot();
    int quantity = Math.min(session.draftQuantity(), hand.getAmount());
    Instant expires = Instant.now().plus(durationAt(session.durationIndex()));
    OrderService.SellDraft draft =
        new OrderService.SellDraft(
            session.marketName(), slot, quantity, session.draftPrice(), expires);
    var stage =
        sellNow ? orderService.placeSellNow(player, draft) : orderService.placeSell(player, draft);
    stage.whenComplete(
        (result, failure) ->
            Bukkit.getScheduler()
                .runTask(
                    plugin,
                    () -> {
                      if (failure != null || !result.accepted()) {
                        player.sendMessage(
                            Component.text(
                                "Sell failed: "
                                    + (failure == null ? result.message() : failure.getMessage())));
                      } else {
                        player.sendMessage(
                            Component.text(
                                result.message()
                                    + (sellNow
                                        ? " Unmatched remainder goes to Mailbox."
                                        : " Listed on the market.")));
                        open(player, session.marketName(), TradingPostSession.Screen.MY_ORDERS, 0);
                      }
                    }));
  }

  private void buyPrice(UUID audience, int delta) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null
        || session == null
        || session.screen() != TradingPostSession.Screen.BUY_ORDERS) {
      return;
    }
    session.adjustPrice(BigDecimal.valueOf(delta));
    refreshBuyDraft(player, session);
  }

  private void buyQty(UUID audience, int delta) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null
        || session == null
        || session.screen() != TradingPostSession.Screen.BUY_ORDERS) {
      return;
    }
    session.adjustQuantity(delta);
    refreshBuyDraft(player, session);
  }

  private void buyDuration(UUID audience) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null
        || session == null
        || session.screen() != TradingPostSession.Screen.BUY_ORDERS) {
      return;
    }
    session.cycleDuration(config.durations().size());
    refreshBuyDraft(player, session);
  }

  private void buyExact(UUID audience) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null
        || session == null
        || session.screen() != TradingPostSession.Screen.BUY_ORDERS) {
      return;
    }
    session.toggleExactMatch();
    refreshBuyDraft(player, session);
  }

  private void placeBuy(UUID audience) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null) {
      return;
    }
    ItemStack hand = player.getInventory().getItemInMainHand();
    if (hand == null || hand.getType().isAir()) {
      player.sendMessage(
          Component.text("Hold a sample of the material (or exact item) to bid on."));
      return;
    }
    String fingerprint = null;
    if (session.exactMatch()) {
      fingerprint = ItemCodec.fingerprint(ItemCodec.encode(hand));
    }
    Instant expires = Instant.now().plus(durationAt(session.durationIndex()));
    OrderService.BuyDraft draft =
        new OrderService.BuyDraft(
            session.marketName(),
            hand.getType().getKey().toString(),
            fingerprint,
            session.draftQuantity(),
            session.draftPrice(),
            expires);
    orderService
        .placeBuy(player, draft)
        .whenComplete(
            (result, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (failure != null || !result.accepted()) {
                            player.sendMessage(
                                Component.text(
                                    "Buy order failed: "
                                        + (failure == null
                                            ? result.message()
                                            : failure.getMessage())));
                          } else {
                            player.sendMessage(
                                Component.text(
                                    result.message()
                                        + " Escrow reserved; crosses asks at or below your max."));
                            open(
                                player,
                                session.marketName(),
                                TradingPostSession.Screen.MY_ORDERS,
                                0);
                          }
                        }));
  }

  private void detailQty(UUID audience, int delta) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null || session.screen() != TradingPostSession.Screen.DETAIL) {
      return;
    }
    session.adjustDetailQuantity(delta);
    UUID orderId = session.detailOrderId();
    if (orderId == null) {
      return;
    }
    executor
        .submit(
            () -> database.transaction(connection -> orders.findSell(connection, orderId, false)))
        .whenComplete(
            (order, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!stillOn(player, session, TradingPostSession.Screen.DETAIL)
                              || failure != null
                              || order.isEmpty()) {
                            return;
                          }
                          applyDetailSnapshot(session, order.get());
                          refresh(session.playerId());
                        }));
  }

  private void detailQtyAll(UUID audience) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null || session.screen() != TradingPostSession.Screen.DETAIL) {
      return;
    }
    session.detailQuantityAll();
    detailQty(audience, 0);
  }

  private void confirmBuyNow(UUID audience, boolean all) {
    Player player = Bukkit.getPlayer(audience);
    TradingPostSession session = sessions.get(audience);
    if (player == null || session == null || session.detailOrderId() == null) {
      return;
    }
    UUID orderId = session.detailOrderId();
    int requested = all ? session.detailMaxQuantity() : session.detailBuyQuantity();
    executor
        .submit(
            () -> database.transaction(connection -> orders.findSell(connection, orderId, true)))
        .whenComplete(
            (order, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (failure != null
                              || order.isEmpty()
                              || order.get().status() != OrderStatus.ACTIVE
                              || order.get().quantityRemaining() < 1) {
                            player.sendMessage(
                                Component.text("That order was just filled or canceled."));
                            return;
                          }
                          SellOrder ask = order.get();
                          int qty = Math.min(requested, ask.quantityRemaining());
                          if (qty < 1) {
                            player.sendMessage(
                                Component.text("That order was just filled or canceled."));
                            return;
                          }
                          Instant expires = Instant.now().plus(config.durations().getFirst());
                          orderService
                              .placeBuy(
                                  player,
                                  new OrderService.BuyDraft(
                                      ask.marketName(),
                                      ask.material(),
                                      ask.fingerprint(),
                                      qty,
                                      ask.unitPrice(),
                                      expires))
                              .whenComplete(
                                  (result, buyFailure) ->
                                      Bukkit.getScheduler()
                                          .runTask(
                                              plugin,
                                              () -> {
                                                if (buyFailure != null || !result.accepted()) {
                                                  player.sendMessage(
                                                      Component.text(
                                                          "Buy Now failed: "
                                                              + (buyFailure == null
                                                                  ? result.message()
                                                                  : buyFailure.getMessage())));
                                                } else {
                                                  BigDecimal total =
                                                      MoneyMath.total(
                                                          ask.unitPrice(),
                                                          qty,
                                                          ask.unitPrice().scale());
                                                  player.sendMessage(
                                                      Component.text(
                                                          "Buy Now submitted: "
                                                              + qty
                                                              + " @ "
                                                              + ask.unitPrice().toPlainString()
                                                              + " (escrow "
                                                              + total.toPlainString()
                                                              + "). Check Mailbox for items."));
                                                  close(player);
                                                }
                                              }));
                        }));
  }

  private Duration durationAt(int index) {
    List<Duration> durations = config.durations();
    if (durations.isEmpty()) {
      return Duration.ofHours(24);
    }
    return durations.get(Math.floorMod(index, durations.size()));
  }

  private static String formatDuration(Duration duration) {
    long hours = duration.toHours();
    if (hours > 0 && duration.toMinutesPart() == 0) {
      return hours + "h";
    }
    long minutes = duration.toMinutes();
    if (minutes > 0) {
      return minutes + "m";
    }
    return duration.toSeconds() + "s";
  }

  private static Map<String, Object> listingFromAsk(SellOrder ask) {
    return itemMap(
        materialKey(ask.material()),
        Math.min(64, Math.max(1, ask.quantityRemaining())),
        shortMaterial(ask.material()),
        "Unit price: " + ask.unitPrice().toPlainString(),
        "Quantity: " + ask.quantityRemaining(),
        "Click → Buy Now details");
  }

  private static Map<String, Object> listingFromBid(BuyOrder bid) {
    return itemMap(
        "minecraft:paper",
        1,
        bid.material(),
        "Max unit price: " + bid.unitPrice().toPlainString(),
        "Qty left: " + bid.quantityRemaining(),
        bid.templateFingerprint() == null ? "Any of material" : "Exact item template");
  }

  private static Map<String, Object> listingFromMySell(SellOrder sell) {
    return itemMap(
        materialKey(sell.material()),
        Math.min(64, Math.max(1, sell.quantityRemaining())),
        shortMaterial(sell.material()),
        "SELL · " + sell.status().name() + " · " + sell.mode().name(),
        "Price: " + sell.unitPrice().toPlainString() + " · left " + sell.quantityRemaining(),
        "Click to cancel → mailbox remainder");
  }

  private static Map<String, Object> listingFromMyBuy(BuyOrder buy) {
    return itemMap(
        "minecraft:map",
        1,
        "BUY · " + buy.material(),
        buy.status().name(),
        "Max: " + buy.unitPrice().toPlainString() + " · left " + buy.quantityRemaining(),
        "Escrow left: " + buy.escrowReserved().toPlainString());
  }

  private static Map<String, Object> listingFromMailbox(MailboxItem row) {
    String material = "minecraft:chest";
    String label = "Mailbox item";
    try {
      ItemStack decoded = ItemCodec.decode(row.itemBlob());
      if (decoded != null && !decoded.getType().isAir()) {
        material = materialKey(decoded.getType().getKey().toString());
        label = decoded.getType().getKey().getKey();
      }
    } catch (RuntimeException ignored) {
      // Fall back to chest placeholder when blob cannot be decoded offline.
    }
    return itemMap(
        material, 1, label, "Reason: " + row.reason(), "Click to claim into inventory", " ");
  }

  private static Map<String, Object> emptyListings(int count) {
    Map<String, Object> listings = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) {
      listings.put("s" + i, emptySlot(" "));
    }
    return listings;
  }

  private static Map<String, Object> emptySlot(String label) {
    return itemMap("minecraft:gray_stained_glass_pane", 1, label, " ", " ", " ");
  }

  private static Map<String, Object> simpleItem(String material, String label) {
    return Map.of("material", material, "label", label);
  }

  private static Map<String, Object> itemMap(
      String material, int amount, String label, String lore0, String lore1, String lore2) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("material", material);
    map.put("amount", amount);
    map.put("label", label);
    map.put("lore0", lore0);
    map.put("lore1", lore1);
    map.put("lore2", lore2);
    return map;
  }

  private static String materialKey(String material) {
    if (material == null || material.isBlank()) {
      return "minecraft:stone";
    }
    if (material.indexOf(':') >= 0) {
      return material.toLowerCase();
    }
    return "minecraft:" + material.toLowerCase();
  }

  private static String shortMaterial(String material) {
    if (material == null) {
      return "item";
    }
    int colon = material.indexOf(':');
    return colon >= 0 ? material.substring(colon + 1) : material;
  }

  private record MyOrders(List<SellOrder> sells, List<BuyOrder> buys) {}
}
