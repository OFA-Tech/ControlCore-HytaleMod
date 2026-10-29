package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import static org.assertj.core.api.Assertions.assertThat;

import net.ofatech.controlcore.domain.model.keys.Key;
import org.junit.jupiter.api.Test;

class KeyCaptureLogicBanca3Test {

    private static final int BASE = 1073741824;
    private static final int LCTRL = BASE + 224;
    private static final int LALT = BASE + 226;
    private static final int RCTRL = BASE + 228;
    private static final int RALT = BASE + 230;
    private static final int LGUI = BASE + 227;

    private final KeyCaptureLogic logic = new KeyCaptureLogic();

    @Test
    void sdlHoldsMapToTheKeyEnum() {
        assertThat(SdlKeys.hold(LCTRL)).contains(Key.CTRL);
        assertThat(SdlKeys.hold(RCTRL)).contains(Key.CTRL);
        assertThat(SdlKeys.hold(LALT)).contains(Key.ALT);
        assertThat(SdlKeys.hold(RALT)).contains(Key.ALT);
        assertThat(SdlKeys.hold(LGUI)).isEmpty();
        java.util.Set<Key> mapped = java.util.EnumSet.noneOf(Key.class);
        for (int code = BASE + 224; code <= BASE + 231; code++) {
            SdlKeys.hold(code).ifPresent(mapped::add);
        }
        assertThat(mapped).containsExactlyInAnyOrderElementsOf(Key.HOLDS);
    }

    @Test
    void altThenCtrlIsWrittenInHoldsOrder() {
        logic.accept(LALT, false, 0);
        logic.accept(LCTRL, false, 0);

        assertThat(logic.accept(BASE + 84, false, 10))
                .isEqualTo(new KeyCaptureLogic.Outcome.Captured("CTRL+ALT+NUMPAD_DIVIDE"));
    }

    @Test
    void holdOlderThanTheWindowIsDropped() {
        logic.accept(LCTRL, false, 0);

        assertThat(logic.accept('f', false, KeyCaptureLogic.HOLD_WINDOW_MILLIS + 1))
                .isEqualTo(new KeyCaptureLogic.Outcome.Captured("F"));
    }

    @Test
    void holdExactlyAtTheWindowStillCounts() {
        logic.accept(LCTRL, false, 0);

        assertThat(logic.accept('f', false, KeyCaptureLogic.HOLD_WINDOW_MILLIS))
                .isEqualTo(new KeyCaptureLogic.Outcome.Captured("CTRL+F"));
    }

    @Test
    void modifierRepeatKeepsTheHoldAlive() {
        logic.accept(LCTRL, false, 0);
        logic.accept(LCTRL, true, 1000);
        logic.accept(LCTRL, true, 2000);

        assertThat(logic.accept('f', false, 3000)).isEqualTo(new KeyCaptureLogic.Outcome.Captured("CTRL+F"));
    }

    @Test
    void guiKeyIsIgnoredAndDoesNotCancel() {
        assertThat(logic.accept(LGUI, false, 0)).isInstanceOf(KeyCaptureLogic.Outcome.Waiting.class);
        assertThat(logic.accept('q', false, 1)).isEqualTo(new KeyCaptureLogic.Outcome.Captured("Q"));
    }

    @Test
    void digitsAreCapturedAsDigitsAndNumpadSymbolsByName() {
        assertThat(new KeyCaptureLogic().accept('7', false, 0)).isEqualTo(new KeyCaptureLogic.Outcome.Captured("7"));
        assertThat(new KeyCaptureLogic().accept(BASE + 87, false, 0))
                .isEqualTo(new KeyCaptureLogic.Outcome.Captured("NUMPAD_PLUS"));
        assertThat(new KeyCaptureLogic().accept(BASE + 99, false, 0))
                .isEqualTo(new KeyCaptureLogic.Outcome.Captured("NUMPAD_PERIOD"));
    }
}
