package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeyCaptureNotInListsTest {

    private static final int L = 'l';
    private static final int F = 'f';
    private static final int LCTRL = 1073742048;

    @Test
    void withoutEveryKeyAKeyTheServerCannotHearIsRefusedAndTheScreenKeepsWaiting() {
        KeyCaptureLogic logic = new KeyCaptureLogic(false);

        logic.accept(LCTRL, false, 0);
        KeyCaptureLogic.Outcome refused = logic.accept(L, false, 100);
        KeyCaptureLogic.Outcome picked = logic.accept(F, false, 200);

        assertThat(refused).isInstanceOf(KeyCaptureLogic.Outcome.NotInLists.class);
        assertThat(refused.captured()).isEmpty();
        assertThat(picked.captured()).contains("CTRL+F");
    }

    @Test
    void withEveryKeyAnyKeyIsCaptured() {
        KeyCaptureLogic logic = new KeyCaptureLogic(true);

        logic.accept(LCTRL, false, 0);

        assertThat(logic.accept(L, false, 100).captured()).contains("CTRL+L");
    }
}
