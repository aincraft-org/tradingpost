package dev.mintychochip.tradingpost.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemDeliveryTest {
  @Test
  void copiesItemBlobSoCallersCannotMutateThePayload() {
    byte[] blob = {1, 2, 3};
    ItemDelivery delivery =
        new ItemDelivery(
            UUID.randomUUID(), UUID.randomUUID(), "spawn", blob, "fp", ItemDelivery.PURCHASE);
    blob[0] = 9;
    assertArrayEquals(new byte[] {1, 2, 3}, delivery.itemBlob());
    byte[] exposed = delivery.itemBlob();
    exposed[0] = 7;
    assertArrayEquals(new byte[] {1, 2, 3}, delivery.itemBlob());
    assertNotSame(delivery.itemBlob(), delivery.itemBlob());
    assertEquals(ItemDelivery.PURCHASE, delivery.reason());
  }
}
