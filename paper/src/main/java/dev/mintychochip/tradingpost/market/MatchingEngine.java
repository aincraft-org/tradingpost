package dev.mintychochip.tradingpost.market;

import dev.mintychochip.tradingpost.domain.BuyOrder;
import dev.mintychochip.tradingpost.domain.SellOrder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MatchingEngine {
  private static final Comparator<SellOrder> ASK_PRIORITY =
      Comparator.comparing(SellOrder::unitPrice)
          .thenComparing(SellOrder::createdAt)
          .thenComparing(SellOrder::id);
  private static final Comparator<BuyOrder> BID_PRIORITY =
      Comparator.comparing(BuyOrder::unitPrice)
          .reversed()
          .thenComparing(BuyOrder::createdAt)
          .thenComparing(BuyOrder::id);

  public List<MatchDecision> matchBuyAgainstSells(BuyOrder taker, List<SellOrder> asks) {
    Objects.requireNonNull(taker, "taker");
    List<SellOrder> ordered =
        asks.stream().filter(order -> compatible(taker, order)).sorted(ASK_PRIORITY).toList();
    List<MatchDecision> decisions = new ArrayList<>();
    int remaining = taker.quantityRemaining();
    for (SellOrder ask : ordered) {
      if (ask.unitPrice().compareTo(taker.unitPrice()) > 0 || remaining == 0) {
        break;
      }
      int quantity = Math.min(remaining, ask.quantityRemaining());
      if (quantity > 0) {
        decisions.add(new MatchDecision(ask.id(), taker.id(), quantity, ask.unitPrice()));
        remaining -= quantity;
      }
    }
    return List.copyOf(decisions);
  }

  /**
   * Sell Now (New World): consume open buy orders highest price first, only at or above the
   * seller's minimum unit price. Execution price is the resting bid.
   */
  public List<MatchDecision> matchSellNowAgainstBuys(
      SellOrder transientTaker, List<BuyOrder> bids) {
    Objects.requireNonNull(transientTaker, "transientTaker");
    List<BuyOrder> ordered =
        bids.stream()
            .filter(order -> compatible(order, transientTaker))
            .filter(order -> order.unitPrice().compareTo(transientTaker.unitPrice()) >= 0)
            .sorted(BID_PRIORITY)
            .toList();
    List<MatchDecision> decisions = new ArrayList<>();
    int remaining = transientTaker.quantityRemaining();
    for (BuyOrder bid : ordered) {
      if (remaining == 0) {
        break;
      }
      int quantity = Math.min(remaining, bid.quantityRemaining());
      if (quantity > 0) {
        decisions.add(new MatchDecision(transientTaker.id(), bid.id(), quantity, bid.unitPrice()));
        remaining -= quantity;
      }
    }
    return List.copyOf(decisions);
  }

  private static boolean compatible(BuyOrder buy, SellOrder sell) {
    if (!buy.material().equalsIgnoreCase(sell.material())) {
      return false;
    }
    return buy.templateFingerprint() == null
        || buy.templateFingerprint().equals(sell.fingerprint());
  }

  private static boolean compatible(SellOrder sell, BuyOrder buy) {
    return compatible(buy, sell);
  }

  public record MatchDecision(
      UUID sellOrderId, UUID buyOrderId, int quantity, BigDecimal executionPrice) {
    public MatchDecision {
      if (quantity < 1 || executionPrice.signum() <= 0) {
        throw new IllegalArgumentException("invalid match decision");
      }
    }
  }
}
