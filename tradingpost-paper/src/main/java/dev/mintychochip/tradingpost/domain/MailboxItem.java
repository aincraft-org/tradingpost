package dev.mintychochip.tradingpost.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public record MailboxItem(
    UUID id,
    String marketName,
    UUID owner,
    byte[] itemBlob,
    String fingerprint,
    String reason,
    String state,
    UUID settlementId,
    Instant createdAt) {
  public MailboxItem {
    itemBlob = itemBlob.clone();
  }

  @Override
  public byte[] itemBlob() {
    return itemBlob.clone();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof MailboxItem that)) return false;
    return id.equals(that.id) && Arrays.equals(itemBlob, that.itemBlob);
  }

  @Override
  public int hashCode() {
    return 31 * id.hashCode() + Arrays.hashCode(itemBlob);
  }
}
