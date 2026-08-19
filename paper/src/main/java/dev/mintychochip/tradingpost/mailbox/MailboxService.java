package dev.mintychochip.tradingpost.mailbox;

import dev.mintychochip.tradingpost.db.Database;
import dev.mintychochip.tradingpost.db.MailboxRepository;
import dev.mintychochip.tradingpost.db.SqlDialect;
import dev.mintychochip.tradingpost.domain.MailboxItem;
import dev.mintychochip.tradingpost.items.ItemCodec;
import dev.mintychochip.tradingpost.lifecycle.AsyncExecutor;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class MailboxService {
  private final JavaPlugin plugin;
  private final Database database;
  private final MailboxRepository mailbox;
  private final AsyncExecutor executor;

  public MailboxService(
      JavaPlugin plugin, Database database, SqlDialect sql, AsyncExecutor executor) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.database = Objects.requireNonNull(database, "database");
    this.mailbox = new MailboxRepository(sql);
    this.executor = Objects.requireNonNull(executor, "executor");
  }

  public CompletionStage<ClaimResult> claim(Player player, UUID mailboxId) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(mailboxId, "mailboxId");
    if (!Bukkit.isPrimaryThread()) {
      CompletableFuture<ClaimResult> result = new CompletableFuture<>();
      Bukkit.getScheduler()
          .runTask(
              plugin,
              () ->
                  claim(player, mailboxId)
                      .whenComplete(
                          (value, failure) -> {
                            if (failure == null) result.complete(value);
                            else result.completeExceptionally(failure);
                          }));
      return result;
    }
    CompletionStage<MailboxItem> loaded =
        executor.submit(
            () ->
                database.transaction(
                    connection -> {
                      MailboxItem item =
                          mailbox
                              .find(connection, mailboxId, true)
                              .orElseThrow(() -> new IllegalStateException("mailbox item missing"));
                      if (!item.owner().equals(player.getUniqueId())
                          || !mailbox.markClaiming(connection, mailboxId, player.getUniqueId())) {
                        throw new IllegalStateException("mailbox item is not claimable");
                      }
                      return item;
                    }));
    return loaded.thenCompose(item -> placeOnMain(player, item));
  }

  private CompletionStage<ClaimResult> placeOnMain(Player player, MailboxItem item) {
    CompletableFuture<ClaimResult> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              ItemStack stack = ItemCodec.decode(item.itemBlob());
              if (!canFit(player.getInventory(), stack)) {
                executor.submit(
                    () ->
                        database.transaction(
                            connection -> {
                              mailbox.releaseClaiming(connection, item.id());
                              return null;
                            }));
                result.complete(new ClaimResult(false, "inventory is full"));
                return;
              }
              player.getInventory().addItem(stack);
              executor
                  .submit(
                      () ->
                          database.transaction(
                              connection -> {
                                mailbox.markClaimed(connection, item.id());
                                return null;
                              }))
                  .whenComplete(
                      (ignored, failure) -> {
                        if (failure == null) result.complete(new ClaimResult(true, "item claimed"));
                        else result.complete(new ClaimResult(false, "claim confirmation failed"));
                      });
            });
    return result;
  }

  private static boolean canFit(Inventory inventory, ItemStack incoming) {
    int remaining = incoming.getAmount();
    for (ItemStack existing : inventory.getStorageContents()) {
      if (existing == null || existing.getType().isAir()) {
        remaining -= incoming.getMaxStackSize();
      } else if (existing.isSimilar(incoming)) {
        remaining -= Math.max(0, existing.getMaxStackSize() - existing.getAmount());
      }
      if (remaining <= 0) return true;
    }
    return false;
  }

  public record ClaimResult(boolean claimed, String message) {}
}
