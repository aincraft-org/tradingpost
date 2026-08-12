package dev.mintychochip.tradingpost.lifecycle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class AsyncExecutor implements AutoCloseable {
  private final ExecutorService executor;
  private final Semaphore admission;
  private volatile boolean closed;

  public AsyncExecutor(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    this.executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    this.admission = new Semaphore(capacity);
  }

  public <T> CompletionStage<T> submit(Callable<T> task) {
    Objects.requireNonNull(task, "task");
    CompletableFuture<T> result = new CompletableFuture<>();
    if (closed || !admission.tryAcquire()) {
      result.completeExceptionally(
          new IllegalStateException("TradingPost executor is unavailable"));
      return result;
    }
    executor.submit(
        () -> {
          try {
            result.complete(task.call());
          } catch (Throwable failure) {
            result.completeExceptionally(failure);
          } finally {
            admission.release();
          }
        });
    return result;
  }

  public void shutdown(Duration grace) {
    shutdown(grace, () -> {});
  }

  public void shutdown(Duration grace, Runnable afterTermination) {
    Objects.requireNonNull(grace, "grace");
    Objects.requireNonNull(afterTermination, "afterTermination");
    closed = true;
    executor.shutdown();
    Thread.startVirtualThread(
        () -> {
          try {
            if (!executor.awaitTermination(grace.toMillis(), TimeUnit.MILLISECONDS)) {
              executor.shutdownNow();
            }
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
          } finally {
            afterTermination.run();
          }
        });
  }

  @Override
  public void close() {
    shutdown(Duration.ofSeconds(10));
  }
}
