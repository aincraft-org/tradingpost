package dev.jlo.tradingpost.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TradingPostApiTest {
    @Test
    void exposesStableApiVersion() {
        assertEquals("1.0.0", TradingPostApi.API_VERSION);
    }
}
