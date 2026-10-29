package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.keys.Key;

final class SdlKeys {

    static final int ESCAPE = 27;

    private static final int SCANCODE_BASE = 1073741824;
    private static final Map<Integer, Key> KEYS = new HashMap<>();
    private static final Map<Integer, String> HOLDS = Map.of(
            1073742048, "CTRL",
            1073742049, "SHIFT",
            1073742050, "ALT",
            1073742052, "CTRL",
            1073742053, "SHIFT",
            1073742054, "ALT");

    static {
        for (char letter = 'a'; letter <= 'z'; letter++) {
            KEYS.put((int) letter, Key.valueOf(String.valueOf(Character.toUpperCase(letter))));
        }
        for (int digit = 0; digit <= 9; digit++) {
            KEYS.put('0' + digit, Key.valueOf("NUM_" + digit));
        }
        for (int f = 1; f <= 12; f++) {
            KEYS.put(SCANCODE_BASE + 57 + f, Key.valueOf("F" + f));
        }
        for (int n = 1; n <= 9; n++) {
            KEYS.put(SCANCODE_BASE + 88 + n, Key.valueOf("NUMPAD_" + n));
        }
        put(SCANCODE_BASE + 98, Key.NUMPAD_0);
        put(SCANCODE_BASE + 84, Key.NUMPAD_DIVIDE);
        put(SCANCODE_BASE + 85, Key.NUMPAD_MULTIPLY);
        put(SCANCODE_BASE + 86, Key.NUMPAD_MINUS);
        put(SCANCODE_BASE + 87, Key.NUMPAD_PLUS);
        put(SCANCODE_BASE + 88, Key.NUMPAD_ENTER);
        put(SCANCODE_BASE + 99, Key.NUMPAD_PERIOD);
        put(SCANCODE_BASE + 83, Key.NUM_LOCK);
        put(SCANCODE_BASE + 70, Key.PRINT_SCREEN);
        put(SCANCODE_BASE + 71, Key.SCROLL_LOCK);
        put(SCANCODE_BASE + 72, Key.PAUSE);
        put(SCANCODE_BASE + 73, Key.INSERT);
        put(SCANCODE_BASE + 74, Key.HOME);
        put(SCANCODE_BASE + 75, Key.PAGE_UP);
        put(SCANCODE_BASE + 77, Key.END);
        put(SCANCODE_BASE + 78, Key.PAGE_DOWN);
        put(SCANCODE_BASE + 79, Key.RIGHT);
        put(SCANCODE_BASE + 80, Key.LEFT);
        put(SCANCODE_BASE + 81, Key.DOWN);
        put(SCANCODE_BASE + 82, Key.UP);
        put(SCANCODE_BASE + 57, Key.CAPS);
        put(SCANCODE_BASE + 101, Key.MENU);
        put(127, Key.DELETE);
        put(9, Key.TAB);
        put(13, Key.ENTER);
        put(8, Key.BACKSPACE);
        put(32, Key.SPACE);
        put('`', Key.GRAVE);
        put('-', Key.MINUS);
        put('=', Key.EQUALS);
        put('[', Key.LEFT_BRACKET);
        put(']', Key.RIGHT_BRACKET);
        put('\\', Key.BACKSLASH);
        put(';', Key.SEMICOLON);
        put('\'', Key.APOSTROPHE);
        put(',', Key.COMMA);
        put('.', Key.PERIOD);
        put('/', Key.SLASH);
    }

    private SdlKeys() {}

    static Optional<Key> key(int keycode) {
        return Optional.ofNullable(KEYS.get(keycode));
    }

    static boolean isModifier(int keycode) {
        return keycode >= 1073742048 && keycode <= 1073742055;
    }

    static Optional<String> hold(int keycode) {
        return Optional.ofNullable(HOLDS.get(keycode));
    }

    private static void put(int keycode, Key key) {
        KEYS.put(keycode, key);
    }
}
