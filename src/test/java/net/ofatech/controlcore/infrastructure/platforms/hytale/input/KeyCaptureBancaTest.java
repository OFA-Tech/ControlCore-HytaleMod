package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import org.junit.jupiter.api.Test;

class KeyCaptureBancaTest {

    private static final int MASK = 1 << 30;
    private static final int L_CTRL = MASK | 224;
    private static final int L_SHIFT = MASK | 225;
    private static final int L_ALT = MASK | 226;
    private static final int L_GUI = MASK | 227;
    private static final int R_CTRL = MASK | 228;
    private static final int R_SHIFT = MASK | 229;
    private static final int R_GUI = MASK | 231;

    @Test
    void everyNumpadKeyMapsFromItsSdlCode() {
        int[] digits = {98, 89, 90, 91, 92, 93, 94, 95, 96, 97};
        for (int n = 0; n <= 9; n++) {
            assertThat(SdlKeys.key(MASK | digits[n])).as("KP_" + n).contains(Key.valueOf("NUMPAD_" + n));
        }
        assertThat(SdlKeys.key(MASK | 84)).contains(Key.NUMPAD_DIVIDE);
        assertThat(SdlKeys.key(MASK | 85)).contains(Key.NUMPAD_MULTIPLY);
        assertThat(SdlKeys.key(MASK | 86)).contains(Key.NUMPAD_MINUS);
        assertThat(SdlKeys.key(MASK | 87)).contains(Key.NUMPAD_PLUS);
        assertThat(SdlKeys.key(MASK | 88)).contains(Key.NUMPAD_ENTER);
        assertThat(SdlKeys.key(MASK | 99)).contains(Key.NUMPAD_PERIOD);
        assertThat(SdlKeys.key(MASK | 83)).contains(Key.NUM_LOCK);
    }

    @Test
    void everyFunctionKeyMapsFromItsSdlCode() {
        for (int f = 1; f <= 12; f++) {
            assertThat(SdlKeys.key(MASK | (57 + f))).as("F" + f).contains(Key.valueOf("F" + f));
        }
    }

    @Test
    void navigationAndLockKeysMapFromTheirSdlCodes() {
        assertThat(SdlKeys.key(MASK | 57)).contains(Key.CAPS);
        assertThat(SdlKeys.key(MASK | 70)).contains(Key.PRINT_SCREEN);
        assertThat(SdlKeys.key(MASK | 71)).contains(Key.SCROLL_LOCK);
        assertThat(SdlKeys.key(MASK | 72)).contains(Key.PAUSE);
        assertThat(SdlKeys.key(MASK | 73)).contains(Key.INSERT);
        assertThat(SdlKeys.key(MASK | 74)).contains(Key.HOME);
        assertThat(SdlKeys.key(MASK | 75)).contains(Key.PAGE_UP);
        assertThat(SdlKeys.key(MASK | 77)).contains(Key.END);
        assertThat(SdlKeys.key(MASK | 78)).contains(Key.PAGE_DOWN);
        assertThat(SdlKeys.key(MASK | 79)).contains(Key.RIGHT);
        assertThat(SdlKeys.key(MASK | 80)).contains(Key.LEFT);
        assertThat(SdlKeys.key(MASK | 81)).contains(Key.DOWN);
        assertThat(SdlKeys.key(MASK | 82)).contains(Key.UP);
        assertThat(SdlKeys.key(MASK | 101)).contains(Key.MENU);
    }

    @Test
    void everyCapturedKeyIsWrittenSoKeyBindsReadsItBack() {
        Set<Key> seen = EnumSet.noneOf(Key.class);
        for (int code = 0; code < 256; code++) {
            check(code, seen);
        }
        for (int code = MASK; code < MASK + 300; code++) {
            check(code, seen);
        }
        assertThat(seen).contains(Key.NUMPAD_5, Key.NUM_LOCK, Key.F12, Key.A, Key.NUM_3, Key.SLASH);
    }

    @Test
    void ctrlWithEveryNumpadKeyParses() {
        for (int code = MASK | 84; code <= (MASK | 99); code++) {
            KeyCaptureLogic logic = new KeyCaptureLogic(true);
            logic.accept(L_CTRL, false, 0);
            String written = logic.accept(code, false, 10).captured().orElseThrow();
            assertThat(written).startsWith("CTRL+");
            assertThat(KeyBinds.parse(written).isSuccess()).as(written).isTrue();
        }
    }

    @Test
    void guiKeysAreIgnoredAndDoNotBecomeAHold() {
        KeyCaptureLogic logic = new KeyCaptureLogic(true);

        assertThat(logic.accept(L_GUI, false, 0).captured()).isEmpty();
        assertThat(logic.accept(R_GUI, false, 0).captured()).isEmpty();

        assertThat(logic.accept('a', false, 10).captured()).contains("A");
    }

    @Test
    void rightSideModifiersCountLikeLeftOnes() {
        KeyCaptureLogic ctrl = new KeyCaptureLogic(true);
        ctrl.accept(R_CTRL, false, 0);
        assertThat(ctrl.accept('a', false, 10).captured()).contains("CTRL+A");

        KeyCaptureLogic shift = new KeyCaptureLogic(true);
        shift.accept(R_SHIFT, false, 0);
        assertThat(shift.accept(MASK | 58, false, 10).captured()).contains("SHIFT+F1");
    }

    @Test
    void aModifierHeldPastTheWindowStaysHeldThroughItsRepeats() {
        KeyCaptureLogic logic = new KeyCaptureLogic(true);
        logic.accept(L_CTRL, false, 0);
        logic.accept(L_CTRL, true, 1000);
        logic.accept(L_CTRL, true, 2000);

        assertThat(logic.accept(MASK | 93, false, 2500).captured()).contains("CTRL+NUMPAD_5");
    }

    @Test
    void theRepeatOfTheMainKeyIsIgnoredButTheFirstPressIsTaken() {
        KeyCaptureLogic logic = new KeyCaptureLogic(true);

        assertThat(logic.accept('q', true, 0).captured()).isEmpty();
        assertThat(logic.accept('q', false, 0).captured()).contains("Q");
    }

    @Test
    void aModifierTappedAndReleasedJustBeforeStillCountsAsHeld() {
        KeyCaptureLogic logic = new KeyCaptureLogic(true);
        logic.accept(L_SHIFT, false, 0);

        assertThat(logic.accept('a', false, 800).captured()).contains("SHIFT+A");
    }

    @Test
    void ctrlAndAltTogetherAreBothKept() {
        KeyCaptureLogic logic = new KeyCaptureLogic(true);
        logic.accept(L_CTRL, false, 0);
        logic.accept(L_ALT, false, 50);

        assertThat(logic.accept('a', false, 100).captured()).contains("CTRL+ALT+A");
    }

    private static void check(int code, Set<Key> seen) {
        if (code == SdlKeys.ESCAPE || SdlKeys.isModifier(code)) {
            return;
        }
        Optional<String> result =
                new KeyCaptureLogic(true).accept(code, false, 0).captured();
        if (result.isEmpty()) {
            return;
        }
        String written = result.get();
        var parsed = KeyBinds.parse(written);
        assertThat(parsed.isSuccess()).as(code + " -> " + written).isTrue();
        assertThat(parsed.value().combos()).hasSize(1);
        Key key = (Key) parsed.value().combos().getFirst().inputs().getFirst();
        assertThat(SdlKeys.key(code)).as(code + " -> " + written).contains(key);
        seen.add(key);
    }
}
