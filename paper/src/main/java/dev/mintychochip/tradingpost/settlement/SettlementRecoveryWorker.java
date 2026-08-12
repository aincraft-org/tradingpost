package dev.mintychochip.tradingpost.settlement;

import dev.mintychochip.tradingpost.db.Database;
import dev.mintychochip.tradingpost.db.SettlementRepository;
import dev.mintychochip.tradingpost.lifecycle.AsyncExecutor;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class SettlementRecoveryWorker {
    private final Database database;
    private final SettlementRepository settlements;
    private final SettlementService service;
    private final AsyncExecutor executor;
    private final String nodeId;

    public SettlementRecoveryWorker(Database database, String schema, String nodeId,
                                    SettlementService service, AsyncExecutor executor) {
        this.database = Objects.requireNonNull(database, "database");
        this.settlements = new SettlementRepository(schema);
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.service = Objects.requireNonNull(service, "service");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public CompletionStage<Integer> runOnce() {
        CompletionStage<UUID> leased = executor.submit(() -> database.transaction(connection ->
                settlements.leaseNext(connection, nodeId, Instant.now()).map(settlement -> settlement.id()).orElse(null)));
        return leased.thenCompose(id -> {
            if (id == null) return CompletableFuture.completedFuture(0);
            return service.recover(id).thenApply(ignored -> 1);
        });
    }
}
