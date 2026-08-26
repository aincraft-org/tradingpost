package dev.mintychochip.tradingpost.settlement;

import dev.mintychochip.tradingpost.domain.SettlementState;

public final class SettlementStateMachine {
  public SettlementState advance(SettlementState from, SettlementState to) {
    if (from == SettlementState.RESERVED
        && (to == SettlementState.MONEY_SETTLED || to == SettlementState.FAILED)) {
      return to;
    }
    if (from == SettlementState.MONEY_SETTLED && to == SettlementState.DELIVERED) {
      return to;
    }
    throw new IllegalStateException("invalid settlement transition: " + from + " -> " + to);
  }
}
