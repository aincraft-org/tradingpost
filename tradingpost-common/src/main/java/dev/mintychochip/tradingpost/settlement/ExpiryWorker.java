package dev.mintychochip.tradingpost.settlement;

import dev.mintychochip.tradingpost.api.ItemDelivery;
import dev.mintychochip.tradingpost.api.ItemDeliveryHandler;
import dev.mintychochip.tradingpost.db.Database;
import dev.mintychochip.tradingpost.db.OrderRepository;
import dev.mintychochip.tradingpost.db.SettlementRepository;
import dev.mintychochip.tradingpost.db.SqlDialect;
import dev.mintychochip.tradingpost.domain.OrderStatus;
import dev.mintychochip.tradingpost.domain.SellOrder;
import dev.mintychochip.tradingpost.domain.SettlementKind;
import dev.mintychochip.tradingpost.lifecycle.AsyncExecutor;
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
  private final ItemDeliveryHandler deliveries;
  private final SettlementService settlementService;
  private final AsyncExecutor executor;
  private final int batchSize;

  public ExpiryWorker(
      Database database,
      SqlDialect sql,
      SettlementService settlementService,
      ItemDeliveryHandler deliveries,
      AsyncExecutor executor,
      int batchSize) {
    this.database = Objects.requireNonNull(database, "database");
    this.orders = new OrderRepository(sql);
    this.settlements = new SettlementRepository(sql);
    this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
    this.settlementService = Objects.requireNonNull(settlementService, "settlementService");
    this.executor = Objects.requireNonNull(executor, "executor");
    if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
    this.batchSize = batchSize;
  }

  public CompletionStage<Integer> runOnce() {
    return executor
        .submit(
            () ->
                database.transaction(
                    connection -> {
                      int expired = 0;
                      List<UUID> refundIds = new ArrayList<>();
                      for (var sell : orders.expiredSells(connection, Instant.now(), batchSize)) {
                        orders.expireSell(connection, sell.id());
                        expired++;
                      }
                      for (var buy :
                          orders.expiredBuys(connection, Instant.now(), batchSize - expired)) {
                        orders.expireBuy(connection, buy.id());
                        if (buy.escrowReserved().signum() > 0) {
                          UUID settlementId = UUID.randomUUID();
                          settlements.insertReserved(
                              connection,
                              new SettlementRepository.SettlementDraft(
                                  settlementId,
                                  SettlementKind.REFUND,
                                  "ah/refund/" + buy.id(),
                                  null,
                                  buy.id(),
                                  buy.escrowReserved()));
                          refundIds.add(settlementId);
                        }
                        expired++;
                      }
                      return new ExpiryResult(expired, List.copyOf(refundIds));
                    }))
        .thenCompose(this::deliverPendingReturns)
        .thenCompose(
            result -> submitRefunds(result.refundIds()).thenApply(ignored -> result.expired()));
  }

  private CompletionStage<ExpiryResult> deliverPendingReturns(ExpiryResult result) {
    return executor
        .submit(
            () -> database.transaction(connection -> orders.pendingReturns(connection, batchSize)))
        .thenCompose(pending -> deliverAll(pending).thenApply(ignored -> result));
  }

  private CompletionStage<Void> deliverAll(List<SellOrder> pending) {
    CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
    for (SellOrder sell : pending) {
      chain = chain.thenCompose(ignored -> deliverReturn(sell));
    }
    return chain;
  }

  private CompletionStage<Void> deliverReturn(SellOrder sell) {
    String reason =
        sell.status() == OrderStatus.EXPIRED
            ? ItemDelivery.ORDER_EXPIRED
            : ItemDelivery.ORDER_CANCELED;
    return deliveries
        .deliver(
            new ItemDelivery(
                sell.id(),
                sell.seller(),
                sell.marketName(),
                sell.itemBlob(),
                sell.fingerprint(),
                reason))
        .thenCompose(
            ignored ->
                executor.submit(
                    () -> {
                      database.transaction(
                          connection -> {
                            orders.clearRemaining(connection, sell.id());
                            return null;
                          });
                      return (Void) null;
                    }))
        .exceptionally(failure -> null);
  }

  private CompletionStage<Void> submitRefunds(List<UUID> refundIds) {
    CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
    for (UUID refundId : refundIds) {
      chain = chain.thenCompose(ignored -> settlementService.submitReserved(refundId));
    }
    return chain;
  }

  private record ExpiryResult(int expired, List<UUID> refundIds) {}
}
