package dev.mintychochip.tradingpost.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ItemCodecTest {
    @Test
    void fingerprintIsStableLowercaseSha256() {
        byte[] bytes = "serialized item".getBytes(StandardCharsets.UTF_8);

        String fingerprint = ItemCodec.fingerprint(bytes);

        assertEquals(ItemCodec.fingerprint(bytes), fingerprint);
        assertEquals(64, fingerprint.length());
        assertEquals(fingerprint.toLowerCase(java.util.Locale.ROOT), fingerprint);
    }

    @Test
    void fingerprintChangesWhenSerializedItemChanges() {
        assertNotEquals(
                ItemCodec.fingerprint(new byte[] {1}),
                ItemCodec.fingerprint(new byte[] {2}));
    }
}
