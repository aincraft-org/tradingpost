package dev.mintychochip.tradingpost.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mintychochip.mint.api.id.AccountId;
import dev.mintychochip.mint.api.id.ClientId;
import dev.mintychochip.mint.api.id.CurrencyId;
import dev.mintychochip.mint.api.id.NamespaceId;
import dev.mintychochip.mint.api.ledger.Posting;
import dev.mintychochip.tradingpost.config.DatabaseEngine;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementTransferBuilderTest {
  private static final TradingPostConfig CONFIG =
      new TradingPostConfig(
          DatabaseEngine.POSTGRESQL,
          "jdbc",
          "user",
          "password",
          "tradingpost",
          4,
          ClientId.of(NamespaceId.parse("tradingpost:plugin")),
          CurrencyId.parse("mint:credits"),
          AccountId.of(NamespaceId.parse("tradingpost:escrow")),
          AccountId.of(NamespaceId.parse("tradingpost:fees")),
          AccountId.of(NamespaceId.parse("tradingpost:tax")),
          100,
          500,
          4,
          30,
          30,
          576,
          List.of(Duration.ofHours(1)),
          Duration.ofMinutes(1),
          Duration.ofMinutes(1),
          Duration.ofMinutes(10),
          Duration.ofSeconds(1));

  @Test
  void matchMovesGrossFromEscrowAndSplitsTaxFromSellerNet() {
    UUID fillId = UUID.randomUUID();
    UUID buyer = UUID.randomUUID();
    UUID seller = UUID.randomUUID();

    SettlementTransferBuilder.TransferPlan plan =
        new SettlementTransferBuilder(CONFIG).match(fillId, buyer, seller, new BigDecimal("10.00"));

    assertEquals("ah/match/" + fillId, plan.key().toString());
    assertEquals(
        List.of(
            new Posting(CONFIG.escrowAccount(), money("-10.00")),
            new Posting(AccountId.player(seller), money("9.50")),
            new Posting(CONFIG.taxAccount(), money("0.50"))),
        plan.postings());
  }

  @Test
  void buyEscrowDebitsBuyerAndCreditsEscrow() {
    UUID orderId = UUID.randomUUID();
    UUID buyer = UUID.randomUUID();

    SettlementTransferBuilder.TransferPlan plan =
        new SettlementTransferBuilder(CONFIG).buyEscrow(orderId, buyer, new BigDecimal("12.00"));

    assertEquals("ah/buy-escrow/" + orderId, plan.key().toString());
    assertEquals(
        List.of(
            new Posting(AccountId.player(buyer), money("-12.00")),
            new Posting(CONFIG.escrowAccount(), money("12.00"))),
        plan.postings());
  }

  private static dev.mintychochip.mint.api.money.Money money(String amount) {
    return new dev.mintychochip.mint.api.money.Money(
        CurrencyId.parse("mint:credits"), new BigDecimal(amount));
  }
}
