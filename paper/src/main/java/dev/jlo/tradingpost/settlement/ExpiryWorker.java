package dev.jlo.tradingpost.settlement;

import dev.jlo.tradingpost.db.Database;
import dev.jlo.tradingpost.db.MailboxRepository;
import dev.jlo.tradingpost.db.OrderRepository;
import dev.jlo.tradingpost.db.SettlementRepository;
import dev.jlo.tradingpost.domain.MailboxItem;
import dev.jlo.tradingpost.domain.SettlementKind;
import dev.jlo.tradingpost.lifecycle.AsyncExecutor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ExpiryWorker {
    private final Database database;
    private final OrderRepository orders;
    private final SettlementRepository settlements;
    private final MailboxRepository mailbox;
    private final SettlementService settlementService;
    private final AsyncExecutor executor;
    private final int batchSize;

    public ExpiryWorker(Database database, String schema, SettlementService settlementService,
                        AsyncExecutor executor, int batchSize) {
        this.database = Objects.requireNonNull(database, "database");
        this.orders = new OrderRepository(schema);
        this.settlements = new SettlementRepository(schema);
        this.mailbox = new MailboxRepository(schema);
        this.settlementService = Objects.requireNonNull(settlementService, "settlementService");
        this.executor = Objects.requireNonNull(executor, "executor");
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
        this.batchSize = batchSize;
    }

    public CompletionStage<Integer> runOnce() {
        return executor.submit(() -> database.transaction(connection -> {
            int expired = 0;
            List<UUID> refundIds = new ArrayList<>();
            for (var sell : orders.expiredSells(connection, Instant.now(), batchSize)) {
                orders.expireSell(connection, sell.id());
                if (sell.quantityRemaining() > 0) {
                    mailbox.insert(connection, new MailboxItem(UUID.randomUUID(), sell.marketName(), sell.seller(),
                            sell.itemBlob(), sell.fingerprint(), "ORDER_EXPIRED", "UNCLAIMED", null, Instant.now()));
                }
                expired++;
            }
            for (var buy : orders.expiredBuys(connection, Instant.now(), batchSize - expired)) {
                orders.expireBuy(connection, buy.id());
                if (buy.escrowReserved().signum() > 0) {
                    UUID settlementId = UUID.randomUUID();
                    settlements.insertReserved(connection, new SettlementRepository.SettlementDraft(
                            settlementId, SettlementKind.REFUND, "ah/refund/" + buy.id(), null,
                            buy.id(), buy.escrowReserved()));
                    refundIds.add(settlementId);
                }
                expired++;
            }
            return new ExpiryResult(expired, List.copyOf(refundIds));
        })).thenCompose(result -> submitRefunds(result.refundIds()).thenApply(ignored -> result.expired()));
    }

    private CompletionStage<Void> submitRefunds(List<UUID> refundIds) {
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (UUID refundId : refundIds) {
            chain = chain.thenCompose(ignored -> settlementService.submitReserved(refundId));
        }
        return chain;
    }

    private record ExpiryResult(int expired, List<UUID> refundIds) {
    }
}
