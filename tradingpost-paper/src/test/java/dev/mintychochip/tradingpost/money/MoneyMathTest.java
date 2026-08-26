package dev.mintychochip.tradingpost.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyMathTest {
  @Test
  void canonicalRejectsRounding() {
    assertThrows(ArithmeticException.class, () -> MoneyMath.canonical(new BigDecimal("1.001"), 2));
  }

  @Test
  void basisPointTaxRoundsDownAtCurrencyScale() {
    assertEquals(new BigDecimal("0.01"), MoneyMath.basisPoints(new BigDecimal("0.39"), 500, 2));
  }

  @Test
  void sellerNetConservesGrossAfterTax() {
    BigDecimal gross = new BigDecimal("10.00");
    BigDecimal tax = MoneyMath.basisPoints(gross, 500, 2);
    assertEquals(new BigDecimal("9.50"), MoneyMath.sellerNet(gross, tax, 2));
  }

  @Test
  void totalUsesExactScale() {
    assertEquals(new BigDecimal("7.50"), MoneyMath.total(new BigDecimal("2.50"), 3, 2));
  }
}
