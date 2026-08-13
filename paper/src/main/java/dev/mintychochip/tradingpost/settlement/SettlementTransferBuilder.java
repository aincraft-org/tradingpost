package dev.mintychochip.tradingpost.settlement;

import dev.mintychochip.mint.api.id.AccountId;
import dev.mintychochip.mint.api.id.IdempotencyKey;
import dev.mintychochip.mint.api.ledger.Posting;
import dev.mintychochip.mint.api.money.Money;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.money.MoneyMath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SettlementTransferBuilder {
  private final TradingPostConfig config;

  public SettlementTransferBuilder(TradingPostConfig config) {
    this.config = Objects.requireNonNull(config, "config");
  }

  public TransferPlan buyEscrow(UUID orderId, UUID buyer, BigDecimal amount) {
    return plan(
        "ah/buy-escrow/" + orderId,
        "buy escrow",
        List.of(
            posting(AccountId.player(buyer), amount.negate()),
            posting(config.escrowAccount(), amount)));
  }

  public TransferPlan listingFee(UUID orderId, UUID seller, BigDecimal amount) {
    return plan(
        "ah/listing-fee/" + orderId,
        "listing fee",
        List.of(
            posting(AccountId.player(seller), amount.negate()),
            posting(config.feeAccount(), amount)));
  }

  public TransferPlan match(UUID fillId, UUID buyer, UUID seller, BigDecimal gross) {
    BigDecimal tax = MoneyMath.basisPoints(gross, config.taxBps(), gross.scale());
    BigDecimal sellerNet = MoneyMath.sellerNet(gross, tax, gross.scale());
    return plan(
        "ah/match/" + fillId,
        "order match",
        List.of(
            posting(config.escrowAccount(), gross.negate()),
            posting(AccountId.player(seller), sellerNet),
            posting(config.taxAccount(), tax)));
  }

  public TransferPlan refund(UUID orderId, UUID buyer, BigDecimal amount) {
    return plan(
        "ah/refund/" + orderId,
        "order refund",
        List.of(
            posting(config.escrowAccount(), amount.negate()),
            posting(AccountId.player(buyer), amount)));
  }

  public TransferPlan feeRefund(UUID orderId, UUID seller, BigDecimal amount) {
    return plan(
        "ah/fee-refund/" + orderId,
        "listing fee refund",
        List.of(
            posting(config.feeAccount(), amount.negate()),
            posting(AccountId.player(seller), amount)));
  }

  private TransferPlan plan(String key, String reason, List<Posting> postings) {
    return new TransferPlan(
        new IdempotencyKey(key),
        postings,
        reason,
        Map.of("plugin", "tradingpost", "settlement_key", key));
  }

  private Posting posting(AccountId account, BigDecimal amount) {
    return new Posting(account, new Money(config.currencyId(), amount));
  }

  public record TransferPlan(
      IdempotencyKey key, List<Posting> postings, String reason, Map<String, String> metadata) {
    public TransferPlan {
      Objects.requireNonNull(key, "key");
      postings = List.copyOf(Objects.requireNonNull(postings, "postings"));
      Objects.requireNonNull(reason, "reason");
      metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
  }
}
