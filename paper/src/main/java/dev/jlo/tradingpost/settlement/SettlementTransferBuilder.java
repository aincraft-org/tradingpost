package dev.jlo.tradingpost.settlement;

import dev.jlo.mint.api.id.AccountId;
import dev.jlo.mint.api.id.ActorId;
import dev.jlo.mint.api.id.IdempotencyKey;
import dev.jlo.mint.api.ledger.Posting;
import dev.jlo.mint.api.money.Money;
import dev.jlo.tradingpost.config.TradingPostConfig;
import dev.jlo.tradingpost.money.MoneyMath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SettlementTransferBuilder {
    private final TradingPostConfig config;
    private final ActorId actor;

    public SettlementTransferBuilder(TradingPostConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.actor = ActorId.of(config.clientId().namespaceId());
    }

    public TransferPlan buyEscrow(UUID orderId, UUID buyer, BigDecimal amount) {
        return plan("ah/buy-escrow/" + orderId, "buy escrow", List.of(
                posting(AccountId.player(buyer), amount.negate()),
                posting(config.escrowAccount(), amount)));
    }

    public TransferPlan listingFee(UUID orderId, UUID seller, BigDecimal amount) {
        return plan("ah/listing-fee/" + orderId, "listing fee", List.of(
                posting(AccountId.player(seller), amount.negate()),
                posting(config.feeAccount(), amount)));
    }

    public TransferPlan match(UUID fillId, UUID buyer, UUID seller, BigDecimal gross) {
        BigDecimal tax = MoneyMath.basisPoints(gross, config.taxBps(), gross.scale());
        BigDecimal sellerNet = MoneyMath.sellerNet(gross, tax, gross.scale());
        return plan("ah/match/" + fillId, "order match", List.of(
                posting(config.escrowAccount(), gross.negate()),
                posting(AccountId.player(seller), sellerNet),
                posting(config.taxAccount(), tax)));
    }

    public TransferPlan refund(UUID orderId, UUID buyer, BigDecimal amount) {
        return plan("ah/refund/" + orderId, "order refund", List.of(
                posting(config.escrowAccount(), amount.negate()),
                posting(AccountId.player(buyer), amount)));
    }

    public TransferPlan feeRefund(UUID orderId, UUID seller, BigDecimal amount) {
        return plan("ah/fee-refund/" + orderId, "listing fee refund", List.of(
                posting(config.feeAccount(), amount.negate()),
                posting(AccountId.player(seller), amount)));
    }

    private TransferPlan plan(String key, String reason, List<Posting> postings) {
        return new TransferPlan(new IdempotencyKey(key), actor, postings, reason,
                Map.of("plugin", "tradingpost", "settlement_key", key));
    }

    private Posting posting(AccountId account, BigDecimal amount) {
        return new Posting(account, new Money(config.currencyId(), amount));
    }

    public record TransferPlan(
            IdempotencyKey key,
            ActorId actor,
            List<Posting> postings,
            String reason,
            Map<String, String> metadata) {
        public TransferPlan {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(actor, "actor");
            postings = List.copyOf(Objects.requireNonNull(postings, "postings"));
            Objects.requireNonNull(reason, "reason");
            metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }
    }
}
