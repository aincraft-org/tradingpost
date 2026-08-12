package dev.mintychochip.tradingpost.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mintychochip.tradingpost.money.MoneyMath;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Pure checks for New World Buy Now total / qty clamping used by detail view host. */
class BuyNowDraftTest {
    @Test
    void buyNowTotalIsUnitTimesQuantityAtCurrencyScale() {
        BigDecimal unit = new BigDecimal("8.50");
        int qty = 3;
        assertEquals(new BigDecimal("25.50"), MoneyMath.total(unit, qty, 2));
    }

    @Test
    void buyQuantityClampsToAvailableViaSessionHelper() {
        int available = 7;
        int requested = 20;
        assertEquals(7, TradingPostSession.clampBuyQuantity(requested, available));
    }
}
