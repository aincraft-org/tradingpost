package dev.mintychochip.tradingpost.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyMath {
    private MoneyMath() {
    }

    public static BigDecimal canonical(BigDecimal amount, int scale) {
        if (amount == null || scale < 0 || scale > 18) {
            throw new IllegalArgumentException("invalid money value or scale");
        }
        return amount.setScale(scale, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal basisPoints(BigDecimal gross, int basisPoints, int scale) {
        if (gross == null || gross.signum() < 0 || basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("invalid basis-point calculation");
        }
        return gross.multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(10_000), scale, RoundingMode.DOWN);
    }

    public static BigDecimal sellerNet(BigDecimal gross, BigDecimal tax, int scale) {
        BigDecimal net = canonical(gross, scale).subtract(canonical(tax, scale));
        if (net.signum() < 0) {
            throw new IllegalArgumentException("tax cannot exceed gross proceeds");
        }
        return net.setScale(scale, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal total(BigDecimal unitPrice, int quantity, int scale) {
        if (unitPrice == null || quantity < 1) {
            throw new IllegalArgumentException("invalid total");
        }
        return canonical(unitPrice, scale).multiply(BigDecimal.valueOf(quantity))
                .setScale(scale, RoundingMode.UNNECESSARY);
    }
}
