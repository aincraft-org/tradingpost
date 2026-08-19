package dev.mintychochip.tradingpost.settlement;

import dev.mintychochip.mint.api.id.AccountId;
import dev.mintychochip.mint.api.result.Committed;
import dev.mintychochip.mint.api.result.OperationOutcome;
import dev.mintychochip.mint.api.result.Rejected;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.db.Database;
import dev.mintychochip.tradingpost.db.MailboxRepository;
import dev.mintychochip.tradingpost.db.OrderRepository;
import dev.mintychochip.tradingpost.db.ReviewRepository;
import dev.mintychochip.tradingpost.db.SellNowOperationRepository;
import dev.mintychochip.tradingpost.db.SettlementRepository;
import dev.mintychochip.tradingpost.domain.MailboxItem;
import dev.mintychochip.tradingpost.domain.OrderStatus;
import dev.mintychochip.tradingpost.domain.SellNowOperation;
import dev.mintychochip.tradingpost.domain.SellNowOperationState;
import dev.mintychochip.tradingpost.domain.SellOrder;
import dev.mintychochip.tradingpost.domain.Settlement;
import dev.mintychochip.tradingpost.domain.SettlementKind;
import dev.mintychochip.tradingpost.domain.SettlementState;
import dev.mintychochip.tradingpost.lifecycle.AsyncExecutor;
import dev.mintychochip.tradingpost.mint.MintOperations;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class SettlementService {
  private final Database database;
  private final SettlementRepository settlements;
  private final OrderRepository orders;
  private final MailboxRepository mailbox;
  private final MintOperations mint;
  private final AsyncExecutor executor;
  private final SettlementTransferBuilder transfers;
  private final SellNowOperationRepository operations;
  private final ReviewRepository reviews;
  private final SettlementStateMachine states = new SettlementStateMachine();

  public SettlementService(
      Database database, TradingPostConfig config, MintOperations mint, AsyncExecutor executor) {
    this.database = Objects.requireNonNull(database, "database");
    this.settlements = new SettlementRepository(config);
    this.orders = new OrderRepository(config);
    this.mailbox = new MailboxRepository(config);
    this.mint = Objects.requireNonNull(mint, "mint");
    this.executor = Objects.requireNonNull(executor, "executor");
    this.transfers = new SettlementTransferBuilder(config);
    this.operations = new SellNowOperationRepository(config);
    this.reviews = new ReviewRepository(config);
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
    return executor.submit(
        () ->
            database.transaction(
                connection -> {
                  Settlement settlement =
                      settlements
                          .find(connection, settlementId, true)
                          .orElseThrow(
                              () ->
                                  new IllegalStateException("settlement missing: " + settlementId));
                  if (settlement.state() == SettlementState.DELIVERED) return null;
                  states.advance(settlement.state(), SettlementState.DELIVERED);
                  if (settlement.kind() == SettlementKind.MATCH_SETTLEMENT) {
                    OrderRepository.FillContext fill =
                        orders
                            .findFillContext(connection, settlement.fillId(), true)
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "fill missing: " + settlement.fillId()));
                    mailbox.insertDelivery(
                        connection,
                        settlement.id(),
                        fill.fill().marketName(),
                        fill.buyer(),
                        fill.fill().itemBlob(),
                        fill.fingerprint(),
                        "PURCHASE");
                    orders.markFillDelivered(connection, settlement.fillId());
                  }
                  settlements.advance(
                      connection,
                      settlement.id(),
                      SettlementState.MONEY_SETTLED,
                      SettlementState.DELIVERED,
                      null);
                  if (settlement.kind() == SettlementKind.MATCH_SETTLEMENT
                      && fillOperationId(connection, settlement.fillId()) != null) {
                    finalizeSellNowIfComplete(
                        connection, fillOperationId(connection, settlement.fillId()));
                  }
                  return null;
                }));
  }

  private CompletionStage<Void> handleOutcome(
      SettlementContext context,
      OperationOutcome<dev.mintychochip.mint.api.ledger.TransactionReceipt> outcome) {
    if (outcome instanceof Committed<?>) {
      return markMoneySettled(context).thenCompose(ignored -> deliver(context.settlement().id()));
    }
    Rejected<?> rejected = (Rejected<?>) outcome;
    return compensate(context, rejected.rejection().message());
  }

  private CompletionStage<Void> compensate(SettlementContext context, String reason) {
    return executor.submit(
        () ->
            database.transaction(
                connection -> {
                  if (context.settlement().kind() == SettlementKind.MATCH_SETTLEMENT) {
                    if (context.operationId() == null) {
                      orders.compensateFill(connection, context.settlement().fillId());
                    } else {
                      compensateSellNow(connection, context, reason);
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
                      mailbox.insert(
                          connection,
                          new MailboxItem(
                              UUID.randomUUID(),
                              sell.marketName(),
                              sell.seller(),
                              sell.itemBlob(),
                              sell.fingerprint(),
                              "LISTING_FEE_REJECTED",
                              "UNCLAIMED",
                              context.settlement().id(),
                              Instant.now()));
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

  private void compensateSellNow(
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
    if (sell.quantityRemaining() > 0) {
      mailbox.insertDelivery(
          connection,
          operation.operationId(),
          operation.marketName(),
          operation.seller(),
          operation.sourceItemBlob(),
          operation.sourceFingerprint(),
          "SELL_NOW_FAILED");
    }
    orders.cancelSell(connection, sell.id(), sell.seller());
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
        connection.prepareStatement(
            "SELECT count(*) FILTER (WHERE status <> 'DELIVERED') FROM "
                + operations.schema()
                + ".fills WHERE operation_id=?")) {
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
    if (sell.quantityRemaining() > 0) {
      mailbox.insertDelivery(
          connection,
          operation.operationId(),
          operation.marketName(),
          operation.seller(),
          sell.itemBlob(),
          sell.fingerprint(),
          "SELL_NOW_REMAINDER");
    }
    orders.cancelSell(connection, sell.id(), sell.seller());
    SellNowOperationState expected = operation.state();
    operations.advance(connection, operationId, expected, SellNowOperationState.COMPLETED, null);
  }

  private boolean hasCommittedOrDeliveredFill(java.sql.Connection connection, UUID operationId)
      throws java.sql.SQLException {
    try (var statement =
        connection.prepareStatement(
            "SELECT EXISTS (SELECT 1 FROM "
                + operations.schema()
                + ".settlements WHERE operation_id=? AND state IN ('MONEY_SETTLED','DELIVERED'))")) {
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

  private record SettlementContext(
      Settlement settlement,
      OrderRepository.FillContext fill,
      UUID buyer,
      UUID seller,
      UUID operationId) {}
}
