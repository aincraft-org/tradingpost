package dev.jlo.tradingpost.mint;

import dev.jlo.mint.api.id.AccountId;
import dev.jlo.mint.api.id.ActorId;
import dev.jlo.mint.api.id.IdempotencyKey;
import dev.jlo.mint.api.ledger.Posting;
import dev.jlo.mint.api.ledger.TransactionReceipt;
import dev.jlo.mint.api.id.CurrencyId;
import dev.jlo.mint.api.lifecycle.MintState;
import dev.jlo.mint.api.result.OperationOutcome;
import dev.jlo.mint.api.service.MintClient;
import dev.jlo.mint.paper.api.PaperMintAccess;
import dev.jlo.tradingpost.config.TradingPostConfig;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class MintGateway implements MintOperations {
    private final TradingPostConfig config;
    private final PaperMintAccess access;
    private final MintClient client;

    private MintGateway(TradingPostConfig config, PaperMintAccess access, MintClient client) {
        this.config = config;
        this.access = access;
        this.client = client;
    }

    public static Optional<MintGateway> discover(TradingPostConfig config) {
        RegisteredServiceProvider<PaperMintAccess> registration =
                Bukkit.getServicesManager().getRegistration(PaperMintAccess.class);
        if (registration == null) {
            return Optional.empty();
        }
        PaperMintAccess access = registration.getProvider();
        return Optional.of(new MintGateway(config, access, access.mint().client(config.clientId())));
    }

    public boolean ready() {
        return access.mint().state() == MintState.READY;
    }

    public MintState state() {
        return access.mint().state();
    }

    public CompletionStage<Boolean> validateCurrency() {
        return client.currencies().find(config.currencyId()).thenApply(Optional::isPresent);
    }

    public CompletionStage<Boolean> ensureSystemAccounts() {
        CompletionStage<Boolean> escrow = client.accounts().ensure(config.escrowAccount());
        CompletionStage<Boolean> fees = client.accounts().ensure(config.feeAccount());
        CompletionStage<Boolean> tax = client.accounts().ensure(config.taxAccount());
        return CompletableFuture.allOf(
                        escrow.toCompletableFuture(),
                        fees.toCompletableFuture(),
                        tax.toCompletableFuture())
                .thenCompose(ignored -> client.accounts().exists(config.escrowAccount())
                        .thenCombine(client.accounts().exists(config.feeAccount()), Boolean::logicalAnd)
                        .thenCombine(client.accounts().exists(config.taxAccount()), Boolean::logicalAnd));
    }
    public CompletionStage<Boolean> accountsExist(List<AccountId> accountIds) {
        List<CompletionStage<Boolean>> checks = accountIds.stream()
                .distinct()
                .map(client.accounts()::exists)
                .toList();
        return CompletableFuture.allOf(checks.stream()
                        .map(CompletionStage::toCompletableFuture)
                        .toArray(CompletableFuture[]::new))
                .thenApply(ignored -> checks.stream().allMatch(stage -> stage.toCompletableFuture().join()));
    }


    public CompletionStage<OperationOutcome<TransactionReceipt>> transfer(
            IdempotencyKey key,
            ActorId actor,
            List<Posting> postings,
            String reason,
            Map<String, String> metadata) {
        return client.ledger().transact(new dev.jlo.mint.api.ledger.TransactionRequest(
                key, actor, postings, reason, metadata));
    }

    public CompletionStage<Optional<TransactionReceipt>> receipt(IdempotencyKey key) {
        return client.ledger().receipt(key);
    }

    public CompletionStage<dev.jlo.mint.api.ledger.BalanceSnapshot> balance(AccountId accountId) {
        return client.ledger().balance(accountId, config.currencyId());
    }

    public TradingPostConfig config() {
        return config;
    }

    public CurrencyId currencyId() {
        return config.currencyId();
    }
}
