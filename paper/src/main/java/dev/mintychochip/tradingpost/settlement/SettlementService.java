package dev.mintychochip.tradingpost.settlement;

import dev.jlo.mint.api.id.AccountId;
import dev.jlo.mint.api.result.Committed;
import dev.jlo.mint.api.result.OperationOutcome;
import dev.jlo.mint.api.result.Rejected;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.db.Database;
import dev.mintychochip.tradingpost.db.MailboxRepository;
import dev.mintychochip.tradingpost.db.OrderRepository;
import dev.mintychochip.tradingpost.db.SettlementRepository;
import dev.mintychochip.tradingpost.domain.Settlement;
import dev.mintychochip.tradingpost.domain.SettlementKind;
import dev.mintychochip.tradingpost.domain.SettlementState;
import dev.mintychochip.tradingpost.lifecycle.AsyncExecutor;
import dev.mintychochip.tradingpost.domain.MailboxItem;
import dev.mintychochip.tradingpost.domain.OrderStatus;
import dev.mintychochip.tradingpost.mint.MintOperations;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

public final class SettlementService {
    private final Database database;
    private final SettlementRepository settlements;
    private final OrderRepository orders;
    private final MailboxRepository mailbox;
    private final MintOperations mint;
    private final AsyncExecutor executor;
    private final SettlementTransferBuilder transfers;
    private final SettlementStateMachine states = new SettlementStateMachine();

    public SettlementService(Database database, TradingPostConfig config, MintOperations mint,
                             AsyncExecutor executor) {
        this.database = Objects.requireNonNull(database, "database");
        this.settlements = new SettlementRepository(config.schema());
        this.orders = new OrderRepository(config.schema());
        this.mailbox = new MailboxRepository(config.schema());
        this.mint = Objects.requireNonNull(mint, "mint");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.transfers = new SettlementTransferBuilder(config);
    }

    public CompletionStage<Void> submitReserved(UUID settlementId) {
        CompletionStage<SettlementContext> loaded = executor.submit(
                () -> database.transaction(connection -> load(connection, settlementId)));
        return loaded.thenCompose(context -> {
            if (context.settlement().state() == SettlementState.DELIVERED
                    || context.settlement().state() == SettlementState.FAILED) {
                return CompletableFuture.completedFuture(null);
            }
            if (context.settlement().state() == SettlementState.MONEY_SETTLED) {
                return deliver(settlementId);
            }
            SettlementTransferBuilder.TransferPlan plan = plan(context);
            List<AccountId> accounts = plan.postings().stream().map(posting -> posting.accountId()).toList();
            return mint.accountsExist(accounts).thenCompose(exists -> {
                if (!exists) {
                    return compensate(context, "required Mint account is missing");
                }
                return mint.transfer(plan.key(), plan.actor(), plan.postings(), plan.reason(), plan.metadata())
                        .thenCompose(outcome -> handleOutcome(context, outcome));
            });
        });
    }

    public CompletionStage<Void> recover(UUID settlementId) {
        CompletionStage<SettlementContext> loaded = executor.submit(
                () -> database.transaction(connection -> load(connection, settlementId)));
        return loaded.thenCompose(context -> {
            if (context.settlement().state() == SettlementState.DELIVERED
                    || context.settlement().state() == SettlementState.FAILED) {
                return CompletableFuture.completedFuture(null);
            }
            if (context.settlement().state() == SettlementState.MONEY_SETTLED) {
                return deliver(settlementId);
            }
            return mint.receipt(new dev.jlo.mint.api.id.IdempotencyKey(context.settlement().idempotencyKey()))
                    .thenCompose(receipt -> receipt.isPresent()
                            ? markMoneySettled(context).thenCompose(ignored -> deliver(settlementId))
                            : submitReserved(settlementId));
        });
    }

    private CompletionStage<Void> markMoneySettled(SettlementContext context) {
        return executor.submit(() -> database.transaction(connection -> {
            settlements.advance(connection, context.settlement().id(), SettlementState.RESERVED,
                    SettlementState.MONEY_SETTLED, null);
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
        return executor.submit(() -> database.transaction(connection -> {
            Settlement settlement = settlements.find(connection, settlementId, true)
                    .orElseThrow(() -> new IllegalStateException("settlement missing: " + settlementId));
            if (settlement.state() == SettlementState.DELIVERED) return null;
            states.advance(settlement.state(), SettlementState.DELIVERED);
            if (settlement.kind() == SettlementKind.MATCH_SETTLEMENT) {
                OrderRepository.FillContext fill = orders.findFillContext(connection, settlement.fillId(), true)
                        .orElseThrow(() -> new IllegalStateException("fill missing: " + settlement.fillId()));
                mailbox.insertDelivery(connection, settlement.id(), fill.fill().marketName(), fill.buyer(),
                        fill.fill().itemBlob(), fill.fingerprint(), "PURCHASE");
                orders.markFillDelivered(connection, settlement.fillId());
            }
            settlements.advance(connection, settlement.id(), SettlementState.MONEY_SETTLED,
                    SettlementState.DELIVERED, null);
            return null;
        }));
    }

    private CompletionStage<Void> handleOutcome(SettlementContext context,
                                                 OperationOutcome<dev.jlo.mint.api.ledger.TransactionReceipt> outcome) {
        if (outcome instanceof Committed<?>) {
            return markMoneySettled(context).thenCompose(ignored -> deliver(context.settlement().id()));
        }
        Rejected<?> rejected = (Rejected<?>) outcome;
        return compensate(context, rejected.rejection().message());
    }

    private CompletionStage<Void> compensate(SettlementContext context, String reason) {
        return executor.submit(() -> database.transaction(connection -> {
            if (context.settlement().kind() == SettlementKind.MATCH_SETTLEMENT) {
                // Restore both order remainders and the full sell stack on the book; do not mailbox
                // the filled portion (that would double-grant the item).
                orders.compensateFill(connection, context.settlement().fillId());
            } else if (context.settlement().kind() == SettlementKind.LISTING_FEE) {
                var sell = orders.findSell(connection, context.settlement().orderId(), true)
                        .orElseThrow(() -> new IllegalStateException("sell order missing for fee compensation"));
                if (sell.status() == OrderStatus.CREATING) {
                    orders.failCreatingSell(connection, sell.id());
                    mailbox.insert(connection, new MailboxItem(UUID.randomUUID(), sell.marketName(), sell.seller(),
                            sell.itemBlob(), sell.fingerprint(), "LISTING_FEE_REJECTED", "UNCLAIMED",
                            context.settlement().id(), Instant.now()));
                }
            } else if (context.settlement().kind() == SettlementKind.BUY_ESCROW) {
                orders.failCreatingBuy(connection, context.settlement().orderId());
            }
            states.advance(context.settlement().state(), SettlementState.FAILED);
            settlements.advance(connection, context.settlement().id(), context.settlement().state(),
                    SettlementState.FAILED, reason);
            return null;
        }));
    }

    private SettlementContext load(java.sql.Connection connection, UUID id) throws java.sql.SQLException {
        Settlement settlement = settlements.find(connection, id, true)
                .orElseThrow(() -> new IllegalStateException("settlement missing: " + id));
        if (settlement.kind() == SettlementKind.MATCH_SETTLEMENT) {
            OrderRepository.FillContext fill = orders.findFillContext(connection, settlement.fillId(), true)
                    .orElseThrow(() -> new IllegalStateException("fill missing: " + settlement.fillId()));
            return new SettlementContext(settlement, fill, null, null);
        }
        if (settlement.kind() == SettlementKind.BUY_ESCROW || settlement.kind() == SettlementKind.REFUND) {
            var buy = orders.findBuy(connection, settlement.orderId(), true)
                    .orElseThrow(() -> new IllegalStateException("buy order missing: " + settlement.orderId()));
            return new SettlementContext(settlement, null, buy.buyer(), null);
        }
        var sell = orders.findSell(connection, settlement.orderId(), true)
                .orElseThrow(() -> new IllegalStateException("sell order missing: " + settlement.orderId()));
        return new SettlementContext(settlement, null, null, sell.seller());
    }

    private SettlementTransferBuilder.TransferPlan plan(SettlementContext context) {
        Settlement settlement = context.settlement();
        return switch (settlement.kind()) {
            case BUY_ESCROW -> transfers.buyEscrow(settlement.orderId(), context.buyer(), settlement.amount());
            case LISTING_FEE -> transfers.listingFee(settlement.orderId(), context.seller(), settlement.amount());
            case MATCH_SETTLEMENT -> transfers.match(settlement.fillId(), context.fill().buyer(),
                    context.fill().seller(), settlement.amount());
            case REFUND -> transfers.refund(settlement.orderId(), context.buyer(), settlement.amount());
            case FEE_REFUND -> transfers.feeRefund(settlement.orderId(), context.seller(), settlement.amount());
        };
    }

    private record SettlementContext(Settlement settlement, OrderRepository.FillContext fill,
                                     UUID buyer, UUID seller) {
    }
}
