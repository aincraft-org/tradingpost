package dev.mintychochip.tradingpost.domain;

import java.time.Instant;
import java.util.UUID;

public record SellNowOperation(
    UUID operationId,
    UUID sellOrderId,
    String marketName,
    UUID seller,
    byte[] sourceItemBlob,
    String sourceFingerprint,
    int originalQuantity,
    SellNowOperationState state,
    String failureDetail,
    Instant createdAt,
    Instant updatedAt) {
  public SellNowOperation {
    sourceItemBlob = sourceItemBlob.clone();
  }

  @Override
  public byte[] sourceItemBlob() {
    return sourceItemBlob.clone();
  }
}
