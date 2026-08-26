package dev.mintychochip.tradingpost.settlement;

import dev.mintychochip.mint.api.id.AccountId;
import dev.mintychochip.mint.api.result.Committed;
import dev.mintychochip.mint.api.result.OperationOutcome;
import dev.mintychochip.mint.api.result.Rejected;
import dev.mintychochip.tradingpost.api.ItemDelivery;
import dev.mintychochip.tradingpost.api.ItemDeliveryHandler;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.db.Database;
import dev.mintychochip.tradingpost.db.OrderRepository;
import dev.mintychochip.tradingpost.db.ReviewRepository;
import dev.mintychochip.tradingpost.db.SellNowOperationRepository;
import dev.mintychochip.tradingpost.db.SettlementRepository;
import dev.mintychochip.tradingpost.db.SqlDialect;
import dev.mintychochip.tradingpost.db.SqlStatements;
import dev.mintychochip.tradingpost.domain.OrderStatus;
import dev.mintychochip.tradingpost.domain.SellNowOperation;
import dev.mintychochip.tradingpost.domain.SellNowOperationState;
import dev.mintychochip.tradingpost.domain.SellOrder;
import dev.mintychochip.tradingpost.domain.Settlement;
import dev.mintychochip.tradingpost.domain.SettlementKind;
import dev.mintychochip.tradingpost.domain.SettlementState;
import dev.mintychochip.tradingpost.lifecycle.AsyncExecutor;
import dev.mintychochip.tradingpost.mint.MintOperations;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class SettlementService {
  private final Database database;
  private final SettlementRepository settlements;
  private final OrderRepository orders;
  private final ItemDeliveryHandler deliveries;
  private final MintOperations mint;
  private final AsyncExecutor executor;
  private final SettlementTransferBuilder transfers;
  private final SellNowOperationRepository operations;
  private final ReviewRepository reviews;
  private final SqlDialect sql;
  private final SettlementStateMachine states = new SettlementStateMachine();

  public SettlementService(
      Database database,
      TradingPostConfig config,
      MintOperations mint,
      ItemDeliveryHandler deliveries,
      AsyncExecutor executor) {
    this.database = Objects.requireNonNull(database, "database");
    this.settlements = new SettlementRepository(config);
    this.orders = new OrderRepository(config);
    this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
    this.mint = Objects.requireNonNull(mint, "mint");
    this.executor = Objects.requireNonNull(executor, "executor");
    this.transfers = new SettlementTransferBuilder(config);
    this.operations = new SellNowOperationRepository(config);
    this.reviews = new ReviewRepository(config);
    this.sql = SqlDialect.from(config);
  }

  public CompletionStage<Void> submitReserved(UUID settlementId) {
    CompletionStage<SettlementContext> loaded =
        executor.submit(() -> database.transaction(connection -> load(connection, settlementId)));
    return loaded.thenCompose(
        context -> {
          if (context.settlement().state() == SettlementState.DELIVERED
              || context.settlement().state() == SettlementState.FAILED) {
            return CompletableFuture.completedFuture(null);
          }
          if (context.operationId() != null && operationTerminal(context.operationId())) {
            return CompletableFuture.completedFuture(null);
          }
          if (context.settlement().state() == SettlementState.MONEY_SETTLED) {
            return deliver(settlementId);
          }
          SettlementTransferBuilder.TransferPlan plan = plan(context);
          List<AccountId> accounts =
              plan.postings().stream().map(posting -> posting.accountId()).toList();
          return mint.accountsExist(accounts)
              .thenCompose(
                  exists -> {
                    if (!exists) {
                      return compensate(context, "required Mint account is missing");
                    }
                    return mint.transfer(
                            plan.key(), plan.postings(), plan.reason(), plan.metadata())
                        .thenCompose(outcome -> handleOutcome(context, outcome));
                  });
        });
  }

  public CompletionStage<Void> recover(UUID settlementId) {
    CompletionStage<SettlementContext> loaded =
        executor.submit(() -> database.transaction(connection -> load(connection, settlementId)));
    return loaded.thenCompose(
        context -> {
          if (context.settlement().state() == SettlementState.DELIVERED
              || context.settlement().state() == SettlementState.FAILED) {
            return CompletableFuture.completedFuture(null);
          }
          if (context.operationId() != null && operationTerminal(context.operationId())) {
            return CompletableFuture.completedFuture(null);
          }
          if (context.settlement().state() == SettlementState.MONEY_SETTLED) {
            return deliver(settlementId);
          }
          return mint.receipt(
                  new dev.mintychochip.mint.api.id.IdempotencyKey(
                      context.settlement().idempotencyKey()))
              .thenCompose(
                  receipt ->
                      receipt.isPresent()
                          ? markMoneySettled(context).thenCompose(ignored -> deliver(settlementId))
                          : submitReserved(settlementId));
        });
  }

  private CompletionStage<Void> markMoneySettled(SettlementContext context) {
    return executor.submit(
        () ->
            database.transaction(
                connection -> {
                  settlements.advance(
                      connection,
                      context.settlement().id(),
                      SettlementState.RESERVED,
                      SettlementState.MONEY_SETTLED,
                      null);
                  if (context.settlement().kind() == SettlementKind.MATCH_SETTLEMENT) {
                    orders.markFillMoneySettled(connection, context.settlement().fillId());
                  } else if (context.settlement().kind() == SettlementKind.BUY_ESCROW) {
                    orders.activateBuy(connection, context.settlement().orderId());
                  } else if (context.settlement().kind() == SettlementKind.LISTING_FEE) {
                    orders.activateSell(connection, context.settlement().orderId());
                  }
                  return null;
                }));
  }

  public CompletionStage<Void> deliver(UUID settlementId) {
    return executor
        .submit(
            () -> database.transaction(connection -> loadPendingDelivery(connection, settlementId)))
        .thenCompose(
            pending -> {
              if (pending == null) {
                return CompletableFuture.completedFuture(null);
              }
              CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
              if (pending.purchase() != null) {
                chain = chain.thenCompose(ignored -> deliveries.deliver(pending.purchase()));
              }
              if (pending.remainder() != null) {
                chain = chain.thenCompose(ignored -> deliveries.deliver(pending.remainder()));
              }
              return chain.thenCompose(ignored -> commitDelivery(pending));
            });
  }

  private CompletionStage<Void> handleOutcome(
      SettlementContext context,
      OperationOutcome<dev.mintychochip.mint.api.ledger.TransactionReceipt> outcome) {
    if (outcome instanceof Committed<?>) {
      return markMoneySettled(context)
          .thenCompose(
              ignored -> deliver(context.settlement().id()).exceptionally(failure -> null));
    }
    Rejected<?> rejected = (Rejected<?>) outcome;
    return compensate(context, rejected.rejection().message());
  }

  private CompletionStage<Void> compensate(SettlementContext context, String reason) {
    if (context.settlement().kind() == SettlementKind.LISTING_FEE) {
      return returnCreatingSell(context).thenCompose(ignored -> markFailed(context, reason));
    }
    if (context.settlement().kind() == SettlementKind.MATCH_SETTLEMENT
        && context.operationId() != null) {
      return returnFailedSellNow(context).thenCompose(ignored -> markFailed(context, reason));
    }
    return markFailed(context, reason);
  }

  private CompletionStage<Void> returnCreatingSell(SettlementContext context) {
    return executor
        .submit(
            () ->
                database.transaction(
                    connection ->
                        orders
                            .findSell(connection, context.settlement().orderId(), true)
                            .filter(sell -> sell.status() == OrderStatus.CREATING)
                            .orElse(null)))
        .thenCompose(
            sell -> {
              if (sell == null) {
                return CompletableFuture.completedFuture(null);
              }
              return deliveries.deliver(
                  new ItemDelivery(
                      context.settlement().id(),
                      sell.seller(),
                      sell.marketName(),
                      sell.itemBlob(),
                      sell.fingerprint(),
                      ItemDelivery.LISTING_FEE_REJECTED));
            });
  }

  private CompletionStage<Void> returnFailedSellNow(SettlementContext context) {
    return executor
        .submit(
            () -> database.transaction(connection -> loadFailedSellNowReturn(connection, context)))
        .thenCompose(
            delivery -> {
              if (delivery == null) {
                return CompletableFuture.completedFuture(null);
              }
              return deliveries.deliver(delivery);
            });
  }

  private ItemDelivery loadFailedSellNowReturn(
      java.sql.Connection connection, SettlementContext context) throws java.sql.SQLException {
    SellNowOperation operation =
        operations
            .find(connection, context.operationId(), true)
            .orElseThrow(() -> new IllegalStateException("Sell Now operation missing"));
    if (operation.state() == SellNowOperationState.COMPLETED
        || operation.state() == SellNowOperationState.REVIEW
        || operation.state() == SellNowOperationState.FAILED) {
      return null;
    }
    boolean committed =
        hasCommittedOrDeliveredFill(connection, context.operationId())
            || context.settlement().state() == SettlementState.MONEY_SETTLED
            || context.settlement().state() == SettlementState.DELIVERED;
    if (committed) {
      return null;
    }
    if (operation.sourceItemBlob().length == 0) {
      return null;
    }
    return new ItemDelivery(
        operation.operationId(),
        operation.seller(),
        operation.marketName(),
        operation.sourceItemBlob(),
        operation.sourceFingerprint(),
        ItemDelivery.SELL_NOW_FAILED);
  }

  private CompletionStage<Void> markFailed(SettlementContext context, String reason) {
    return executor.submit(
        () ->
            database.transaction(
                connection -> {
                  if (context.settlement().kind() == SettlementKind.MATCH_SETTLEMENT) {
                    if (context.operationId() == null) {
                      orders.compensateFill(connection, context.settlement().fillId());
                    } else {
                      persistFailedSellNow(connection, context, reason);
                    }
                  } else if (context.settlement().kind() == SettlementKind.LISTING_FEE) {
                    var sell =
                        orders
                            .findSell(connection, context.settlement().orderId(), true)
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "sell order missing for fee compensation"));
                    if (sell.status() == OrderStatus.CREATING) {
                      orders.failCreatingSell(connection, sell.id());
                      orders.clearRemaining(connection, sell.id());
                    }
                  } else if (context.settlement().kind() == SettlementKind.BUY_ESCROW) {
                    orders.failCreatingBuy(connection, context.settlement().orderId());
                  }
                  states.advance(context.settlement().state(), SettlementState.FAILED);
                  settlements.advance(
                      connection,
                      context.settlement().id(),
                      context.settlement().state(),
                      SettlementState.FAILED,
                      reason);
                  return null;
                }));
  }

  private boolean operationTerminal(UUID operationId) {
    return executor
        .submit(
            () ->
                database.transaction(connection -> operations.find(connection, operationId, false)))
        .toCompletableFuture()
        .join()
        .map(SellNowOperation::state)
        .map(
            state ->
                state == SellNowOperationState.COMPLETED
                    || state == SellNowOperationState.FAILED
                    || state == SellNowOperationState.REVIEW)
        .orElse(false);
  }

  private void persistFailedSellNow(
      java.sql.Connection connection, SettlementContext context, String reason)
      throws java.sql.SQLException {
    SellNowOperation operation =
        operations
            .find(connection, context.operationId(), true)
            .orElseThrow(() -> new IllegalStateException("Sell Now operation missing"));
    if (operation.state() == SellNowOperationState.COMPLETED
        || operation.state() == SellNowOperationState.REVIEW
        || operation.state() == SellNowOperationState.FAILED) {
      return;
    }
    boolean committed =
        hasCommittedOrDeliveredFill(connection, context.operationId())
            || context.settlement().state() == SettlementState.MONEY_SETTLED
            || context.settlement().state() == SettlementState.DELIVERED;
    if (committed) {
      operations.advance(
          connection,
          operation.operationId(),
          operation.state(),
          SellNowOperationState.REVIEW,
          reason);
      reviews.insert(
          connection,
          UUID.randomUUID(),
          operation.seller(),
          operation.sourceFingerprint(),
          "{\"type\":\"SELL_NOW_MIXED_OUTCOME\",\"operation_id\":\""
              + operation.operationId()
              + "\",\"settlement_id\":\""
              + context.settlement().id()
              + "\"}");
      return;
    }
    SellOrder sell =
        orders
            .findSell(connection, operation.sellOrderId(), true)
            .orElseThrow(() -> new IllegalStateException("Sell Now order missing"));
    orders.cancelSell(connection, sell.id(), sell.seller());
    orders.clearRemaining(connection, sell.id());
    operations.advance(
        connection,
        operation.operationId(),
        operation.state(),
        SellNowOperationState.FAILED,
        reason);
  }

  private UUID fillOperationId(java.sql.Connection connection, UUID fillId)
      throws java.sql.SQLException {
    return orders
        .findFillContext(connection, fillId, false)
        .map(OrderRepository.FillContext::operationId)
        .orElse(null);
  }

  private void finalizeSellNowIfComplete(java.sql.Connection connection, UUID operationId)
      throws java.sql.SQLException {
    SellNowOperation operation =
        operations
            .find(connection, operationId, true)
            .orElseThrow(() -> new IllegalStateException("Sell Now operation missing"));
    if (operation.state() == SellNowOperationState.COMPLETED
        || operation.state() == SellNowOperationState.FAILED
        || operation.state() == SellNowOperationState.REVIEW) {
      return;
    }
    try (var statement =
        connection.prepareStatement(statement("settlements/count-undelivered-fills.sql"))) {
      statement.setObject(1, operationId);
      try (var rows = statement.executeQuery()) {
        rows.next();
        if (rows.getInt(1) != 0) {
          if (operation.state() == SellNowOperationState.RESERVED) {
            operations.advance(
                connection,
                operationId,
                SellNowOperationState.RESERVED,
                SellNowOperationState.SETTLING,
                null);
          }
          return;
        }
      }
    }
    SellOrder sell =
        orders
            .findSell(connection, operation.sellOrderId(), true)
            .orElseThrow(() -> new IllegalStateException("Sell Now order missing"));
    orders.cancelSell(connection, sell.id(), sell.seller());
    orders.clearRemaining(connection, sell.id());
    SellNowOperationState expected = operation.state();
    operations.advance(connection, operationId, expected, SellNowOperationState.COMPLETED, null);
  }

  private PendingDelivery loadPendingDelivery(java.sql.Connection connection, UUID settlementId)
      throws java.sql.SQLException {
    Settlement settlement =
        settlements
            .find(connection, settlementId, true)
            .orElseThrow(() -> new IllegalStateException("settlement missing: " + settlementId));
    if (settlement.state() == SettlementState.DELIVERED) {
      return null;
    }
    states.advance(settlement.state(), SettlementState.DELIVERED);
    if (settlement.kind() != SettlementKind.MATCH_SETTLEMENT) {
      return new PendingDelivery(settlement, null, null, false);
    }
    OrderRepository.FillContext fill =
        orders
            .findFillContext(connection, settlement.fillId(), true)
            .orElseThrow(() -> new IllegalStateException("fill missing: " + settlement.fillId()));
    ItemDelivery purchase =
        new ItemDelivery(
            settlement.id(),
            fill.buyer(),
            fill.fill().marketName(),
            fill.fill().itemBlob(),
            fill.fingerprint(),
            ItemDelivery.PURCHASE);
    ItemDelivery remainder = null;
    boolean finalizeSellNow = false;
    UUID operationId = fill.operationId();
    if (operationId != null) {
      int undelivered = countUndeliveredFills(connection, operationId);
      if (undelivered <= 1) {
        finalizeSellNow = true;
        SellNowOperation operation =
            operations
                .find(connection, operationId, true)
                .orElseThrow(() -> new IllegalStateException("Sell Now operation missing"));
        if (operation.state() != SellNowOperationState.COMPLETED
            && operation.state() != SellNowOperationState.FAILED
            && operation.state() != SellNowOperationState.REVIEW) {
          SellOrder sell =
              orders
                  .findSell(connection, operation.sellOrderId(), true)
                  .orElseThrow(() -> new IllegalStateException("Sell Now order missing"));
          if (sell.quantityRemaining() > 0) {
            remainder =
                new ItemDelivery(
                    operation.operationId(),
                    operation.seller(),
                    operation.marketName(),
                    sell.itemBlob(),
                    sell.fingerprint(),
                    ItemDelivery.SELL_NOW_REMAINDER);
          }
        }
      }
    }
    return new PendingDelivery(settlement, purchase, remainder, finalizeSellNow);
  }

  private CompletionStage<Void> commitDelivery(PendingDelivery pending) {
    return executor.submit(
        () ->
            database.transaction(
                connection -> {
                  Settlement settlement =
                      settlements
                          .find(connection, pending.settlement().id(), true)
                          .orElseThrow(
                              () ->
                                  new IllegalStateException(
                                      "settlement missing: " + pending.settlement().id()));
                  if (settlement.state() == SettlementState.DELIVERED) {
                    return null;
                  }
                  if (settlement.kind() == SettlementKind.MATCH_SETTLEMENT) {
                    orders.markFillDelivered(connection, settlement.fillId());
                  }
                  settlements.advance(
                      connection,
                      settlement.id(),
                      SettlementState.MONEY_SETTLED,
                      SettlementState.DELIVERED,
                      null);
                  if (pending.finalizeSellNow()) {
                    finalizeSellNowIfComplete(
                        connection, fillOperationId(connection, settlement.fillId()));
                  }
                  return null;
                }));
  }

  private int countUndeliveredFills(java.sql.Connection connection, UUID operationId)
      throws java.sql.SQLException {
    try (var statement =
        connection.prepareStatement(statement("settlements/count-undelivered-fills.sql"))) {
      statement.setObject(1, operationId);
      try (var rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    }
  }

  private boolean hasCommittedOrDeliveredFill(java.sql.Connection connection, UUID operationId)
      throws java.sql.SQLException {
    try (var statement =
        connection.prepareStatement(statement("settlements/exists-committed-or-delivered.sql"))) {
      statement.setObject(1, operationId);
      try (var rows = statement.executeQuery()) {
        rows.next();
        return rows.getBoolean(1);
      }
    }
  }

  private SettlementContext load(java.sql.Connection connection, UUID id)
      throws java.sql.SQLException {
    Settlement settlement =
        settlements
            .find(connection, id, true)
            .orElseThrow(() -> new IllegalStateException("settlement missing: " + id));
    if (settlement.kind() == SettlementKind.MATCH_SETTLEMENT) {
      OrderRepository.FillContext fill =
          orders
              .findFillContext(connection, settlement.fillId(), true)
              .orElseThrow(() -> new IllegalStateException("fill missing: " + settlement.fillId()));
      return new SettlementContext(settlement, fill, null, null, fill.operationId());
    }
    if (settlement.kind() == SettlementKind.BUY_ESCROW
        || settlement.kind() == SettlementKind.REFUND) {
      var buy =
          orders
              .findBuy(connection, settlement.orderId(), true)
              .orElseThrow(
                  () -> new IllegalStateException("buy order missing: " + settlement.orderId()));
      return new SettlementContext(settlement, null, buy.buyer(), null, null);
    }
    var sell =
        orders
            .findSell(connection, settlement.orderId(), true)
            .orElseThrow(
                () -> new IllegalStateException("sell order missing: " + settlement.orderId()));
    return new SettlementContext(settlement, null, null, sell.seller(), null);
  }

  private SettlementTransferBuilder.TransferPlan plan(SettlementContext context) {
    Settlement settlement = context.settlement();
    return switch (settlement.kind()) {
      case BUY_ESCROW ->
          transfers.buyEscrow(settlement.orderId(), context.buyer(), settlement.amount());
      case LISTING_FEE ->
          transfers.listingFee(settlement.orderId(), context.seller(), settlement.amount());
      case MATCH_SETTLEMENT ->
          transfers.match(
              settlement.fillId(),
              context.fill().buyer(),
              context.fill().seller(),
              settlement.amount());
      case REFUND -> transfers.refund(settlement.orderId(), context.buyer(), settlement.amount());
      case FEE_REFUND ->
          transfers.feeRefund(settlement.orderId(), context.seller(), settlement.amount());
    };
  }

  private String statement(String name) {
    return SqlStatements.load(name, sql);
  }

  private record SettlementContext(
      Settlement settlement,
      OrderRepository.FillContext fill,
      UUID buyer,
      UUID seller,
      UUID operationId) {}

  private record PendingDelivery(
      Settlement settlement,
      ItemDelivery purchase,
      ItemDelivery remainder,
      boolean finalizeSellNow) {}
}
