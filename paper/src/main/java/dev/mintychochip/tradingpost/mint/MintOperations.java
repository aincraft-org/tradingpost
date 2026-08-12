package dev.mintychochip.tradingpost.mint;

import dev.jlo.mint.api.id.AccountId;
import dev.jlo.mint.api.id.ActorId;
import dev.jlo.mint.api.id.IdempotencyKey;
import dev.jlo.mint.api.ledger.BalanceSnapshot;
import dev.jlo.mint.api.ledger.Posting;
import dev.jlo.mint.api.ledger.TransactionReceipt;
import dev.jlo.mint.api.result.OperationOutcome;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Mint boundary used by settlement and reconciliation. */
public interface MintOperations {
  CompletionStage<Boolean> accountsExist(List<AccountId> accountIds);

  CompletionStage<OperationOutcome<TransactionReceipt>> transfer(
      IdempotencyKey key,
      ActorId actor,
      List<Posting> postings,
      String reason,
      Map<String, String> metadata);

  CompletionStage<Optional<TransactionReceipt>> receipt(IdempotencyKey key);

  CompletionStage<BalanceSnapshot> balance(AccountId accountId);

  TradingPostConfig config();
}
