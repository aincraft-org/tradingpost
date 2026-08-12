package dev.jlo.tradingpost.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jlo.tradingpost.domain.SettlementState;
import org.junit.jupiter.api.Test;

class SettlementStateMachineTest {
    @Test
    void acceptsOnlyForwardSettlementTransitions() {
        SettlementStateMachine machine = new SettlementStateMachine();

        assertEquals(SettlementState.MONEY_SETTLED,
                machine.advance(SettlementState.RESERVED, SettlementState.MONEY_SETTLED));
        assertEquals(SettlementState.DELIVERED,
                machine.advance(SettlementState.MONEY_SETTLED, SettlementState.DELIVERED));
        assertEquals(SettlementState.FAILED,
                machine.advance(SettlementState.RESERVED, SettlementState.FAILED));
    }

    @Test
    void rejectsSkippingMoneyOrReopeningSettlement() {
        SettlementStateMachine machine = new SettlementStateMachine();

        assertThrows(IllegalStateException.class,
                () -> machine.advance(SettlementState.RESERVED, SettlementState.DELIVERED));
        assertThrows(IllegalStateException.class,
                () -> machine.advance(SettlementState.DELIVERED, SettlementState.MONEY_SETTLED));
        assertThrows(IllegalStateException.class,
                () -> machine.advance(SettlementState.FAILED, SettlementState.RESERVED));
    }
}
