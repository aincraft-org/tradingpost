package dev.mintychochip.tradingpost.settlement;

import dev.mintychochip.tradingpost.db.Database;
import dev.mintychochip.tradingpost.db.OrderRepository;
import dev.mintychochip.tradingpost.db.ReviewRepository;
import dev.mintychochip.tradingpost.lifecycle.AsyncExecutor;
import dev.mintychochip.tradingpost.mint.MintGateway;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class ReconciliationWorker {
  private final Database database;
  private final OrderRepository orders;
  private final ReviewRepository reviews;
  private final MintGateway mint;
  private final AsyncExecutor executor;

  public ReconciliationWorker(
      Database database, String schema, MintGateway mint, AsyncExecutor executor) {
    this.database = Objects.requireNonNull(database, "database");
    this.orders = new OrderRepository(schema);
    this.reviews = new ReviewRepository(schema);
    this.mint = Objects.requireNonNull(mint, "mint");
    this.executor = Objects.requireNonNull(executor, "executor");
  }

  public CompletionStage<ReconciliationReport> runOnce() {
    CompletionStage<BigDecimal> expected =
        executor.submit(() -> database.transaction(orders::escrowObligation));
    return expected.thenCompose(
        value ->
            mint.balance(mint.config().escrowAccount())
                .thenCompose(
                    snapshot -> {
                      ReconciliationReport report =
                          new ReconciliationReport(
                              value, snapshot.total(), value.compareTo(snapshot.total()) != 0);
                      if (!report.diverged())
                        return java.util.concurrent.CompletableFuture.completedFuture(report);
                      return executor.submit(
                          () -> {
                            database.transaction(
                                connection -> {
                                  reviews.insert(
                                      connection,
                                      UUID.randomUUID(),
                                      null,
                                      null,
                                      "{\"kind\":\"ESCROW_DIVERGENCE\",\"expected\":\""
                                          + value.toPlainString()
                                          + "\",\"observed\":\""
                                          + snapshot.total().toPlainString()
                                          + "\"}");
                                  return null;
                                });
                            return report;
                          });
                    }));
  }

  public record ReconciliationReport(
      BigDecimal expectedEscrow, BigDecimal observedEscrow, boolean diverged) {}
}
