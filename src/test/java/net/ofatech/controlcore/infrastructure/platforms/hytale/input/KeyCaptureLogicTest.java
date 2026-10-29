package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.keys.Key;
import org.junit.jupiter.api.Test;

class KeyCaptureLogicTest {

    private static final int CTRL = 1073742048;
    private static final int RIGHT_ALT = 1073742054;
    private static final int NUMPAD_5 = 1073741917;
    private static final int ESC = 27;

    @Test
    void sdlCodesMapToKeys() {
        assertThat(SdlKeys.key(97)).contains(Key.A);
        assertThat(SdlKeys.key(49)).contains(Key.NUM_1);
        assertThat(SdlKeys.key(1073741882)).contains(Key.F1);
        assertThat(SdlKeys.key(1073741893)).contains(Key.F12);
        assertThat(SdlKeys.key(NUMPAD_5)).contains(Key.NUMPAD_5);
        assertThat(SdlKeys.key(1073741922)).contains(Key.NUMPAD_0);
        assertThat(SdlKeys.key(1073741912)).contains(Key.NUMPAD_ENTER);
        assertThat(SdlKeys.key(1073741906)).contains(Key.UP);
        assertThat(SdlKeys.key(127)).contains(Key.DELETE);
        assertThat(SdlKeys.key(1073741925)).contains(Key.MENU);
        assertThat(SdlKeys.key(42424242)).isEmpty();
        assertThat(SdlKeys.isModifier(CTRL)).isTrue();
        assertThat(SdlKeys.hold(RIGHT_ALT)).contains("ALT");
        assertThat(SdlKeys.hold(1073742051)).isEmpty();
    }

    @Test
    void ctrlThenNumpadFive() {
        KeyCaptureLogic logic = new KeyCaptureLogic();

        assertThat(logic.accept(CTRL, false, 0)).isEmpty();
        assertThat(logic.accept(CTRL, true, 100)).isEmpty();

        assertThat(logic.accept(NUMPAD_5, false, 300)).contains(Optional.of("CTRL+NUMPAD_5"));
    }

    @Test
    void staleModifierIsNotHeld() {
        KeyCaptureLogic logic = new KeyCaptureLogic();
        logic.accept(CTRL, false, 0);

        assertThat(logic.accept(NUMPAD_5, false, 2000)).contains(Optional.of("NUMPAD_5"));
    }

    @Test
    void digitsAreWrittenAsTheyLook() {
        assertThat(new KeyCaptureLogic().accept(51, false, 0)).contains(Optional.of("3"));
    }

    @Test
    void escapeCancels() {
        assertThat(new KeyCaptureLogic().accept(ESC, false, 0)).contains(Optional.empty());
    }

    @Test
    void repeatsAndUnknownKeysAreIgnored() {
        KeyCaptureLogic logic = new KeyCaptureLogic();

        assertThat(logic.accept(NUMPAD_5, true, 0)).isEmpty();
        assertThat(logic.accept(42424242, false, 0)).isEmpty();
        assertThat(logic.accept(1073742051, false, 0)).isEmpty();
    }
}
