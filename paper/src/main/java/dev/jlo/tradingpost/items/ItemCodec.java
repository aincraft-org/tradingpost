package dev.jlo.tradingpost.items;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

public final class ItemCodec {
    private ItemCodec() {
    }

    public static byte[] encode(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return stack.serializeAsBytes();
    }

    public static ItemStack decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return ItemStack.deserializeBytes(bytes);
    }

    public static String fingerprint(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 is required by the JDK", impossible);
        }
    }

    public static Split split(ItemStack stack, int quantity) {
        Objects.requireNonNull(stack, "stack");
        if (quantity < 1 || quantity > stack.getAmount()) {
            throw new IllegalArgumentException("split quantity is outside stack amount");
        }
        ItemStack filled = stack.clone();
        filled.setAmount(quantity);
        ItemStack remaining = stack.clone();
        remaining.setAmount(stack.getAmount() - quantity);
        return new Split(filled, remaining);
    }

    public static ItemStack merge(ItemStack first, ItemStack second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.isSimilar(second) || first.getAmount() + second.getAmount() > first.getMaxStackSize()) {
            throw new IllegalArgumentException("items cannot be merged");
        }
        ItemStack merged = first.clone();
        merged.setAmount(first.getAmount() + second.getAmount());
        return merged;
    }

    /**
     * Rebuilds a stack from filled + remaining Paper item blobs (merge).
     * Prefer fill.remaining_item_blob pre-match restore via OrderRepository for compensation.
     */
    public static byte[] restoreFromSplit(byte[] filledBlob, byte[] remainingBlob) {
        Objects.requireNonNull(filledBlob, "filledBlob");
        if (remainingBlob == null || remainingBlob.length == 0) {
            return filledBlob.clone();
        }
        ItemStack filled = decode(filledBlob);
        ItemStack remaining = decode(remainingBlob);
        boolean filledEmpty = filled.getType().isAir() || filled.getAmount() < 1;
        boolean remainingEmpty = remaining.getType().isAir() || remaining.getAmount() < 1;
        if (filledEmpty && remainingEmpty) {
            throw new IllegalArgumentException("cannot restore an empty split");
        }
        if (filledEmpty) {
            return encode(remaining);
        }
        if (remainingEmpty) {
            return encode(filled);
        }
        return encode(merge(filled, remaining));
    }

    public record Split(ItemStack filled, ItemStack remaining) {
        public Split {
            filled = Objects.requireNonNull(filled, "filled").clone();
            remaining = Objects.requireNonNull(remaining, "remaining").clone();
        }
    }
}
