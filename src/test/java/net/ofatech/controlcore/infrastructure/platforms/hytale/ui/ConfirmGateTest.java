package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfirmGateTest {

    @Test
    void theSecondPressOfTheSameKeyConfirmsOnceThenRearms() {
        ConfirmGate gate = new ConfirmGate(60_000);
        assertThat(gate.confirmed("a")).isFalse();
        assertThat(gate.confirmed("a")).isTrue();
        assertThat(gate.confirmed("a")).as("a third press starts over").isFalse();
    }

    @Test
    void anotherKeyOrDisarmStartsOver() {
        ConfirmGate gate = new ConfirmGate(60_000);
        gate.confirmed("a");
        assertThat(gate.confirmed("b")).isFalse();
        assertThat(gate.confirmed("a")).isFalse();
        gate.disarm();
        assertThat(gate.confirmed("a")).isFalse();
    }

    @Test
    void anExpiredArmDoesNotConfirm() {
        ConfirmGate gate = new ConfirmGate(0);
        gate.confirmed("a");
        assertThat(gate.confirmed("a")).isFalse();
    }
}
