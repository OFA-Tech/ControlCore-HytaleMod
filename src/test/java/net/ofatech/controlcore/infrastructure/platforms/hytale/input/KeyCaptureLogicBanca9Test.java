package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import static org.assertj.core.api.Assertions.assertThat;

import net.ofatech.controlcore.domain.model.keys.Key;
import org.junit.jupiter.api.Test;

class KeyCaptureLogicBanca9Test {

    private static final int NUMPAD_5 = 1073741917;
    private static final int ESC = 27;
    private static final int F = 'f';
    private static final int L = 'l';

    @Test
    void refusedKeyNamesTheKeyAndTheScreenKeepsWaitingForAnother() {
        KeyCaptureLogic logic = new KeyCaptureLogic(false);

        KeyCaptureLogic.Outcome first = logic.accept(NUMPAD_5, false, 0);
        KeyCaptureLogic.Outcome again = logic.accept(NUMPAD_5, false, 50);
        KeyCaptureLogic.Outcome picked = logic.accept(F, false, 100);

        assertThat(first).isEqualTo(new KeyCaptureLogic.Outcome.NotInLists(Key.NUMPAD_5.label()));
        assertThat(again).isInstanceOf(KeyCaptureLogic.Outcome.NotInLists.class);
        assertThat(picked.captured()).contains("F");
    }

    @Test
    void escapeStillCancelsWhenOnlyListedKeysAreAllowed() {
        KeyCaptureLogic logic = new KeyCaptureLogic(false);

        assertThat(logic.accept(ESC, false, 0)).isInstanceOf(KeyCaptureLogic.Outcome.Cancelled.class);
    }

    @Test
    void anyKeyTakesTheKeyTheOtherModeRefuses() {
        assertThat(new KeyCaptureLogic(true).accept(NUMPAD_5, false, 0).captured())
                .contains("NUMPAD_5");
        assertThat(new KeyCaptureLogic(true).accept(L, false, 0).captured()).contains("L");
        assertThat(new KeyCaptureLogic(false).accept(L, false, 0).captured()).isEmpty();
    }

    @Test
    void refusedKeysAreExactlyTheKeysTheServerCannotHear() {
        for (Key key : Key.values()) {
            assertThat(key.heardByServer())
                    .as(key.name())
                    .isEqualTo(key.signal(false).isPresent() || key.signal(true).isPresent());
        }
        assertThat(Key.F.heardByServer()).isTrue();
        assertThat(Key.NUMPAD_5.heardByServer()).isFalse();
    }
}
