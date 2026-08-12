package dev.jlo.tradingpost.market;

import dev.jlo.tradingpost.config.TradingPostConfig;
import dev.jlo.tradingpost.db.Database;
import dev.jlo.tradingpost.db.MailboxRepository;
import dev.jlo.tradingpost.db.OrderRepository;
import dev.jlo.tradingpost.db.SettlementRepository;
import dev.jlo.tradingpost.domain.BuyOrder;
import dev.jlo.tradingpost.domain.MailboxItem;
import dev.jlo.tradingpost.domain.OrderStatus;
import dev.jlo.tradingpost.domain.SellOrder;
import dev.jlo.tradingpost.domain.SellOrderMode;
import dev.jlo.tradingpost.domain.SettlementKind;
import dev.jlo.tradingpost.items.ItemCodec;
import dev.jlo.tradingpost.lifecycle.AsyncExecutor;
import dev.jlo.tradingpost.money.MoneyMath;
import dev.jlo.tradingpost.settlement.SettlementService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrderService {
    private final JavaPlugin plugin;
    private final Database database;
    private final OrderRepository orders;
    private final SettlementRepository settlements;
    private final MailboxRepository mailbox;
    private final SettlementService settlementService;
    private final TradingPostConfig config;
    private final AsyncExecutor executor;
    private final MatchingEngine matching = new MatchingEngine();

    public OrderService(JavaPlugin plugin, Database database, TradingPostConfig config,
                        SettlementService settlementService, AsyncExecutor executor) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.database = Objects.requireNonNull(database, "database");
        this.orders = new OrderRepository(config.schema());
        this.settlements = new SettlementRepository(config.schema());
        this.mailbox = new MailboxRepository(config.schema());
        this.settlementService = Objects.requireNonNull(settlementService, "settlementService");
        this.config = Objects.requireNonNull(config, "config");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public CompletionStage<OrderResult> placeSell(Player player, SellDraft draft) {
        return placeSell(player, draft, SellOrderMode.NORMAL);
    }

    public CompletionStage<OrderResult> placeSellNow(Player player, SellDraft draft) {
        return placeSell(player, draft, SellOrderMode.INSTANT);
    }

    private CompletionStage<OrderResult> placeSell(Player player, SellDraft draft, SellOrderMode mode) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(draft, "draft");
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<OrderResult> result = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> placeSell(player, draft, mode)
                    .whenComplete((value, failure) -> completeOnCurrent(result, value, failure)));
            return result;
        }
        ItemStack current = player.getInventory().getItem(draft.slot());
        if (current == null || current.getType().isAir() || draft.quantity() > current.getAmount()) {
            return CompletableFuture.completedFuture(OrderResult.rejected("selected stack is unavailable"));
        }
        ItemCodec.Split split = ItemCodec.split(current, draft.quantity());
        player.getInventory().setItem(draft.slot(), split.remaining().getAmount() == 0 ? null : split.remaining());
        UUID orderId = UUID.randomUUID();
        SellOrder order = new SellOrder(orderId, draft.marketName(), player.getUniqueId(),
                current.getType().getKey().toString(), ItemCodec.encode(split.filled()),
                ItemCodec.fingerprint(ItemCodec.encode(split.filled())), draft.quantity(), draft.quantity(),
                draft.unitPrice(), mode, OrderStatus.CREATING, draft.expiresAt(), Instant.now());
        CompletableFuture<OrderResult> result = new CompletableFuture<>();
        CompletionStage<PersistResult> persisted = executor.submit(() -> database.transaction(connection -> {
            orders.insertSell(connection, order);
            UUID feeSettlementId = null;
            if (mode == SellOrderMode.NORMAL && config.feeBps() > 0) {
                BigDecimal gross = MoneyMath.total(draft.unitPrice(), draft.quantity(), draft.unitPrice().scale());
                BigDecimal fee = MoneyMath.basisPoints(gross, config.feeBps(), gross.scale());
                if (fee.signum() > 0) {
                    feeSettlementId = UUID.randomUUID();
                    settlements.insertReserved(connection, new SettlementRepository.SettlementDraft(
                            feeSettlementId, SettlementKind.LISTING_FEE,
                            "ah/listing-fee/" + orderId, null, orderId, fee));
                }
            }
            // Activate only when no listing fee is pending. Fee settlement activates after MONEY_SETTLED.
            if (feeSettlementId == null) {
                orders.activateSell(connection, orderId);
            }
            List<UUID> matchSettlements = List.of();
            if (mode == SellOrderMode.INSTANT) {
                matchSettlements = reserveSellNowMatches(connection, orderId);
            }
            return new PersistResult(feeSettlementId, matchSettlements);
        }));
        persisted.whenComplete((persistResult, persistFailure) -> {
            if (persistFailure != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    restore(player, split.filled());
                    result.complete(OrderResult.rejected("could not persist order: " + persistFailure.getMessage()));
                });
                return;
            }
            // Durable order exists; settlement failures remain RESERVED for recovery/compensation.
            submitPersistSettlements(persistResult).whenComplete((ignored, submitFailure) ->
                    Bukkit.getScheduler().runTask(plugin, () -> result.complete(new OrderResult(orderId, true,
                            mode == SellOrderMode.INSTANT ? "Sell Now order created" : "Sell order created"))));
        });
        return result;
    }

    public CompletionStage<OrderResult> placeBuy(Player player, BuyDraft draft) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(draft, "draft");
        UUID orderId = UUID.randomUUID();
        BigDecimal escrow = MoneyMath.total(draft.unitPrice(), draft.quantity(), draft.unitPrice().scale());
        UUID settlementId = UUID.randomUUID();
        BuyOrder order = new BuyOrder(orderId, draft.marketName(), player.getUniqueId(), draft.material(),
                draft.templateFingerprint(), draft.quantity(), draft.quantity(), draft.unitPrice(), escrow,
                OrderStatus.CREATING, draft.expiresAt(), Instant.now());
        return executor.submit(() -> database.transaction(connection -> {
            orders.insertBuy(connection, order);
            settlements.insertReserved(connection, new SettlementRepository.SettlementDraft(
                    settlementId, SettlementKind.BUY_ESCROW,
                    "ah/buy-escrow/" + orderId, null, orderId, escrow));
            return null;
        })).thenCompose(ignored -> settlementService.submitReserved(settlementId))
                .thenCompose(ignored -> executor.submit(() -> database.transaction(connection -> {
                    BuyOrder opened = orders.findBuy(connection, orderId, false)
                            .orElseThrow(() -> new IllegalStateException("buy order missing after escrow"));
                    if (opened.status() != OrderStatus.OPEN) {
                        throw new IllegalStateException("buy escrow was not funded");
                    }
                    return null;
                })))
                .thenCompose(ignored -> matchBuy(orderId))
                .thenApply(ignored -> new OrderResult(orderId, true, "Buy order created"))
                .exceptionally(failure -> OrderResult.rejected("could not create buy order: " + failure.getMessage()));
    }

    private List<UUID> reserveSellNowMatches(java.sql.Connection connection, UUID sellOrderId) throws java.sql.SQLException {
        SellOrder sell = orders.findSell(connection, sellOrderId, true)
                .orElseThrow(() -> new IllegalStateException("Sell Now order missing"));
        List<BuyOrder> bids = orders.bestBids(connection, sell.marketName(), sell.material(), config.maxBuyOrders());
        List<MatchingEngine.MatchDecision> decisions = matching.matchSellNowAgainstBuys(sell, bids);
        List<UUID> settlementIds = new ArrayList<>();
        byte[] currentBlob = sell.itemBlob();
        for (MatchingEngine.MatchDecision decision : decisions) {
            ItemCodec.Split split = ItemCodec.split(ItemCodec.decode(currentBlob), decision.quantity());
            UUID settlementId = UUID.randomUUID();
            orders.reserveMatch(connection, decision,
                    new OrderRepository.SettlementDraft(settlementId, SettlementKind.MATCH_SETTLEMENT, "unused",
                            decision.sellOrderId(), decision.buyOrderId(),
                            decision.executionPrice().multiply(BigDecimal.valueOf(decision.quantity()))),
                    ItemCodec.encode(split.filled()), ItemCodec.encode(split.remaining()));
            settlementIds.add(settlementId);
            currentBlob = ItemCodec.encode(split.remaining());
        }
        SellOrder after = orders.findSell(connection, sellOrderId, true)
                .orElseThrow(() -> new IllegalStateException("Sell Now order missing after match"));
        if (after.quantityRemaining() > 0 && after.status() == OrderStatus.ACTIVE) {
            mailbox.insert(connection, new MailboxItem(UUID.randomUUID(), after.marketName(), after.seller(),
                    after.itemBlob(), after.fingerprint(), "SELL_NOW_REMAINDER", "UNCLAIMED", null, Instant.now()));
            orders.cancelSell(connection, sellOrderId, after.seller());
        }
        return List.copyOf(settlementIds);
    }

    private CompletionStage<Void> matchBuy(UUID buyOrderId) {
        return executor.submit(() -> database.transaction(connection -> {
            BuyOrder buy = orders.findBuy(connection, buyOrderId, true)
                    .orElseThrow(() -> new IllegalStateException("buy order missing"));
            if (buy.status() != OrderStatus.OPEN) {
                return List.<UUID>of();
            }
            List<SellOrder> asks = orders.bestAsks(connection, buy.marketName(), buy.material(), config.maxSellOrders());
            List<MatchingEngine.MatchDecision> decisions = matching.matchBuyAgainstSells(buy, asks);
            List<UUID> settlementIds = new ArrayList<>();
            for (MatchingEngine.MatchDecision decision : decisions) {
                SellOrder sell = orders.findSell(connection, decision.sellOrderId(), true)
                        .orElseThrow(() -> new IllegalStateException("sell order missing"));
                ItemCodec.Split split = ItemCodec.split(ItemCodec.decode(sell.itemBlob()), decision.quantity());
                UUID settlementId = UUID.randomUUID();
                orders.reserveMatch(connection, decision,
                        new OrderRepository.SettlementDraft(settlementId, SettlementKind.MATCH_SETTLEMENT, "unused",
                                decision.sellOrderId(), decision.buyOrderId(),
                                decision.executionPrice().multiply(BigDecimal.valueOf(decision.quantity()))),
                        ItemCodec.encode(split.filled()), ItemCodec.encode(split.remaining()));
                settlementIds.add(settlementId);
            }
            return List.copyOf(settlementIds);
        })).thenCompose(this::submitAll);
    }

    private CompletionStage<Void> submitPersistSettlements(PersistResult persist) {
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        if (persist.feeSettlementId() != null) {
            chain = chain.thenCompose(ignored -> settlementService.submitReserved(persist.feeSettlementId()));
        }
        return chain.thenCompose(ignored -> submitAll(persist.matchSettlementIds()));
    }

    private CompletionStage<Void> submitAll(List<UUID> settlementIds) {
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (UUID settlementId : settlementIds) {
            chain = chain.thenCompose(ignored -> settlementService.submitReserved(settlementId));
        }
        return chain;
    }

    public CompletionStage<OrderResult> cancel(Player player, UUID orderId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(orderId, "orderId");
        return executor.submit(() -> database.transaction(connection -> {
            var sell = orders.findSell(connection, orderId, true);
            if (sell.isPresent() && sell.get().seller().equals(player.getUniqueId())
                    && orders.cancelSell(connection, orderId, player.getUniqueId())) {
                if (sell.get().quantityRemaining() > 0) {
                    mailbox.insert(connection,
                            new MailboxItem(UUID.randomUUID(), sell.get().marketName(),
                                    sell.get().seller(), sell.get().itemBlob(), sell.get().fingerprint(),
                                    "ORDER_CANCELED", "UNCLAIMED", null, Instant.now()));
                }
                return new Cancellation(null);
            }
            var buy = orders.findBuy(connection, orderId, true);
            if (buy.isPresent() && buy.get().buyer().equals(player.getUniqueId())
                    && orders.cancelBuy(connection, orderId, player.getUniqueId())) {
                if (buy.get().escrowReserved().signum() > 0) {
                    UUID settlementId = UUID.randomUUID();
                    settlements.insertReserved(connection, new SettlementRepository.SettlementDraft(
                            settlementId, SettlementKind.REFUND,
                            "ah/refund/" + orderId, null, orderId, buy.get().escrowReserved()));
                    return new Cancellation(settlementId);
                }
                return new Cancellation(null);
            }
            throw new IllegalStateException("order is not cancellable or is not owned by player");
        })).thenCompose(cancellation -> cancellation.settlementId() == null
                ? CompletableFuture.completedFuture(new OrderResult(orderId, true, "Order canceled"))
                : settlementService.submitReserved(cancellation.settlementId())
                        .thenApply(ignored -> new OrderResult(orderId, true, "Order canceled and refunded")))
                .exceptionally(failure -> OrderResult.rejected("could not cancel order: " + failure.getMessage()));
    }

    private record Cancellation(UUID settlementId) {
    }

    private record PersistResult(UUID feeSettlementId, List<UUID> matchSettlementIds) {
        private PersistResult {
            matchSettlementIds = List.copyOf(matchSettlementIds);
        }
    }

    private void restore(Player player, ItemStack selected) {
        var leftovers = player.getInventory().addItem(selected);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private static void completeOnCurrent(CompletableFuture<OrderResult> result, OrderResult value, Throwable failure) {
        if (failure == null) result.complete(value);
        else result.completeExceptionally(failure);
    }

    public record SellDraft(String marketName, int slot, int quantity, BigDecimal unitPrice, Instant expiresAt) {
        public SellDraft {
            Objects.requireNonNull(marketName, "marketName");
            Objects.requireNonNull(unitPrice, "unitPrice");
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (marketName.isBlank() || slot < 0 || slot >= 36 || quantity < 1 || unitPrice.signum() <= 0) {
                throw new IllegalArgumentException("invalid sell draft");
            }
        }
    }

    public record BuyDraft(String marketName, String material, String templateFingerprint, int quantity,
                           BigDecimal unitPrice, Instant expiresAt) {
        public BuyDraft {
            Objects.requireNonNull(marketName, "marketName");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(unitPrice, "unitPrice");
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (marketName.isBlank() || material.isBlank() || quantity < 1 || unitPrice.signum() <= 0) {
                throw new IllegalArgumentException("invalid buy draft");
            }
        }
    }

    public record OrderResult(UUID orderId, boolean accepted, String message) {
        public static OrderResult rejected(String message) {
            return new OrderResult(null, false, message);
        }
    }
}
