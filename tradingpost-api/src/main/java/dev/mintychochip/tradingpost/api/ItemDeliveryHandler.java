package dev.mintychochip.tradingpost.api;

import java.util.concurrent.CompletionStage;

/**
 * Startup-bound delivery authority for items leaving the Trading Post book.
 *
 * <p>Implementations register on Bukkit's {@code ServicesManager} before TradingPost becomes READY.
 * {@link #deliver(ItemDelivery)} must be idempotent on {@link ItemDelivery#deliveryId()}.
 */
public interface ItemDeliveryHandler {
  CompletionStage<Void> deliver(ItemDelivery delivery);
}
