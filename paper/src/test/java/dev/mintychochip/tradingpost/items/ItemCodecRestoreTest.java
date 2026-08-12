package dev.mintychochip.tradingpost.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * restoreFromSplit is Paper ItemStack-based; offline unit coverage exercises validation
 * and the empty-remaining branch that does not require a registry.
 */
class ItemCodecRestoreTest {
    @Test
    void restoreFromSplitRejectsNullFilledBlob() {
        assertThrows(NullPointerException.class, () -> ItemCodec.restoreFromSplit(null, new byte[] {1}));
    }

    @Test
    void restoreFromSplitWithEmptyRemainingReturnsFilledClone() {
        byte[] filled = new byte[] {1, 2, 3};
        byte[] restored = ItemCodec.restoreFromSplit(filled, new byte[] {});
        assertArrayEquals(filled, restored);
        // clone, not same reference
        assertEquals(false, restored == filled);
    }
}
