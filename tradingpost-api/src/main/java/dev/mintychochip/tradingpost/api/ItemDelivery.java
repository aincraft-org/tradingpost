package dev.mintychochip.tradingpost.api;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Immutable item payload handed to an {@link ItemDeliveryHandler}. */
public record ItemDelivery(
    UUID deliveryId,
    UUID owner,
    String marketName,
    byte[] itemBlob,
    String fingerprint,
    String reason) {
  public static final String PURCHASE = "PURCHASE";
  public static final String LISTING_FEE_REJECTED = "LISTING_FEE_REJECTED";
  public static final String SELL_NOW_FAILED = "SELL_NOW_FAILED";
  public static final String SELL_NOW_REMAINDER = "SELL_NOW_REMAINDER";
  public static final String ORDER_CANCELED = "ORDER_CANCELED";
  public static final String ORDER_EXPIRED = "ORDER_EXPIRED";

  public ItemDelivery {
    Objects.requireNonNull(deliveryId, "deliveryId");
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(marketName, "marketName");
    itemBlob = Objects.requireNonNull(itemBlob, "itemBlob").clone();
    Objects.requireNonNull(fingerprint, "fingerprint");
    Objects.requireNonNull(reason, "reason");
  }

  @Override
  public byte[] itemBlob() {
    return itemBlob.clone();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ItemDelivery that)) {
      return false;
    }
    return deliveryId.equals(that.deliveryId)
        && owner.equals(that.owner)
        && marketName.equals(that.marketName)
        && Arrays.equals(itemBlob, that.itemBlob)
        && fingerprint.equals(that.fingerprint)
        && reason.equals(that.reason);
  }

  @Override
  public int hashCode() {
    return 31 * deliveryId.hashCode() + Arrays.hashCode(itemBlob);
  }
}
