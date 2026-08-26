package dev.mintychochip.tradingpost.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Host-owned per-player Trading Post UI session state.
 *
 * <p>Handwritten inventory layouts are pure presentation; drafts, pagination, and slot→id maps live
 * here.
 */
public final class TradingPostSession {
  public static final BigDecimal MIN_PRICE = new BigDecimal("0.01");

  private final UUID playerId;
  private String marketName;
  private Screen screen = Screen.BROWSE;
  private int page;
  private BigDecimal draftPrice = new BigDecimal("1.00");
  private int draftQuantity = 1;
  private int durationIndex;
  private boolean exactMatch;
  private String materialFilter;
  private final List<UUID> slotIds = new ArrayList<>();
  private UUID detailOrderId;
  private int detailMaxQuantity = 1;
  private int detailBuyQuantity = 1;

  public TradingPostSession(UUID playerId, String marketName) {
    this.playerId = Objects.requireNonNull(playerId, "playerId");
    this.marketName = Objects.requireNonNull(marketName, "marketName");
  }

  public UUID playerId() {
    return playerId;
  }

  public String marketName() {
    return marketName;
  }

  public void marketName(String marketName) {
    this.marketName = Objects.requireNonNull(marketName, "marketName");
  }

  public Screen screen() {
    return screen;
  }

  public void screen(Screen screen) {
    this.screen = Objects.requireNonNull(screen, "screen");
  }

  public int page() {
    return page;
  }

  public void page(int page) {
    this.page = Math.max(0, page);
  }

  public BigDecimal draftPrice() {
    return draftPrice;
  }

  public void draftPrice(BigDecimal draftPrice) {
    this.draftPrice = Objects.requireNonNull(draftPrice, "draftPrice");
  }

  /** Decrements draft unit price by 1, floored at {@link #MIN_PRICE}. */
  public void adjustPrice(BigDecimal delta) {
    draftPrice = draftPrice.add(delta).max(MIN_PRICE);
  }

  public int draftQuantity() {
    return draftQuantity;
  }

  public void draftQuantity(int draftQuantity) {
    this.draftQuantity = Math.max(1, draftQuantity);
  }

  public void adjustQuantity(int delta) {
    draftQuantity = Math.max(1, draftQuantity + delta);
  }

  public int durationIndex() {
    return durationIndex;
  }

  public void durationIndex(int durationIndex) {
    this.durationIndex = Math.max(0, durationIndex);
  }

  public void cycleDuration(int durationCount) {
    if (durationCount <= 0) {
      durationIndex = 0;
      return;
    }
    durationIndex = (durationIndex + 1) % durationCount;
  }

  public boolean exactMatch() {
    return exactMatch;
  }

  public void exactMatch(boolean exactMatch) {
    this.exactMatch = exactMatch;
  }

  public void toggleExactMatch() {
    exactMatch = !exactMatch;
  }

  public String materialFilter() {
    return materialFilter;
  }

  public void materialFilter(String materialFilter) {
    this.materialFilter = materialFilter;
  }

  public List<UUID> slotIds() {
    return List.copyOf(slotIds);
  }

  public void setSlotIds(List<UUID> ids) {
    slotIds.clear();
    if (ids != null) {
      slotIds.addAll(ids);
    }
  }

  public UUID slotIdAt(int index) {
    if (index < 0 || index >= slotIds.size()) {
      return null;
    }
    return slotIds.get(index);
  }

  public UUID detailOrderId() {
    return detailOrderId;
  }

  public void detailOrderId(UUID detailOrderId) {
    this.detailOrderId = detailOrderId;
  }

  public int detailMaxQuantity() {
    return detailMaxQuantity;
  }

  public void detailMaxQuantity(int detailMaxQuantity) {
    this.detailMaxQuantity = Math.max(1, detailMaxQuantity);
  }

  public int detailBuyQuantity() {
    return detailBuyQuantity;
  }

  public void detailBuyQuantity(int detailBuyQuantity) {
    this.detailBuyQuantity = Math.max(1, detailBuyQuantity);
  }

  /** Clamps buy-now quantity into {@code [1, max]}. */
  public static int clampBuyQuantity(int requested, int available) {
    int max = Math.max(1, available);
    return Math.min(Math.max(1, requested), max);
  }

  public void adjustDetailQuantity(int delta) {
    detailBuyQuantity = clampBuyQuantity(detailBuyQuantity + delta, detailMaxQuantity);
  }

  public void detailQuantityAll() {
    detailBuyQuantity = detailMaxQuantity;
  }

  public enum Screen {
    BROWSE,
    SELL,
    BUY_ORDERS,
    MY_ORDERS,
    DETAIL
  }
}
