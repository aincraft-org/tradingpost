package dev.mintychochip.tradingpost.mint;

import dev.mintychochip.mint.api.id.AccountId;
import dev.mintychochip.mint.api.id.CurrencyId;
import dev.mintychochip.mint.api.id.IdempotencyKey;
import dev.mintychochip.mint.api.ledger.Posting;
import dev.mintychochip.mint.api.ledger.TransactionReceipt;
import dev.mintychochip.mint.api.ledger.TransactionRequest;
import dev.mintychochip.mint.api.result.OperationOutcome;
import dev.mintychochip.mint.api.service.MintClientLease;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class MintGateway implements MintOperations {
  private final TradingPostConfig config;
  private volatile MintClientLease client;

  public MintGateway(TradingPostConfig config) {
    this.config = Objects.requireNonNull(config, "config");
  }

  public void bindMintClient(MintClientLease lease) {
    this.client = Objects.requireNonNull(lease, "lease");
  }

  public void clearMintClient() {
    this.client = null;
  }

  public boolean ready() {
    return client != null;
  }

  public CompletionStage<Boolean> validateCurrency() {
    return requireClient().currencies().find(config.currencyId()).thenApply(Optional::isPresent);
  }

  public CompletionStage<Boolean> ensureSystemAccounts() {
    var mint = requireClient();
    CompletionStage<Boolean> escrow = mint.accounts().ensure(config.escrowAccount());
    CompletionStage<Boolean> fees = mint.accounts().ensure(config.feeAccount());
    CompletionStage<Boolean> tax = mint.accounts().ensure(config.taxAccount());
    return CompletableFuture.allOf(
            escrow.toCompletableFuture(), fees.toCompletableFuture(), tax.toCompletableFuture())
        .thenCompose(
            ignored ->
                mint.accounts()
                    .exists(config.escrowAccount())
                    .thenCombine(mint.accounts().exists(config.feeAccount()), Boolean::logicalAnd)
                    .thenCombine(mint.accounts().exists(config.taxAccount()), Boolean::logicalAnd));
  }

  public CompletionStage<Boolean> accountsExist(List<AccountId> accountIds) {
    var mint = requireClient();
    List<CompletionStage<Boolean>> checks =
        accountIds.stream().distinct().map(mint.accounts()::exists).toList();
    return CompletableFuture.allOf(
            checks.stream()
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new))
        .thenApply(
            ignored -> checks.stream().allMatch(stage -> stage.toCompletableFuture().join()));
  }

  public CompletionStage<OperationOutcome<TransactionReceipt>> transfer(
      IdempotencyKey key, List<Posting> postings, String reason, Map<String, String> metadata) {
    return requireClient()
        .ledger()
        .transact(new TransactionRequest(key, postings, reason, metadata));
  }

  public CompletionStage<Optional<TransactionReceipt>> receipt(IdempotencyKey key) {
    return requireClient().ledger().receipt(key);
  }

  public CompletionStage<dev.mintychochip.mint.api.ledger.BalanceSnapshot> balance(
      AccountId accountId) {
    return requireClient().ledger().balance(accountId, config.currencyId());
  }

  public TradingPostConfig config() {
    return config;
  }

  public CurrencyId currencyId() {
    return config.currencyId();
  }

  private MintClientLease requireClient() {
    MintClientLease current = client;
    if (current == null) {
      throw new IllegalStateException("Mint client lease is not bound");
    }
    return current;
  }
}
