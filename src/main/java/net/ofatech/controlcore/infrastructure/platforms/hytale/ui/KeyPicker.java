package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.keys.KeyCombo;
import net.ofatech.controlcore.domain.model.keys.KeyInput;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.model.text.Translator;

final class KeyPicker {

    static final String NONE = "NONE";
    static final String CUSTOM = "CUSTOM";

    static final List<String> HOLDS = List.of(NONE, "CTRL", "ALT", "SHIFT", "SPACE", "STAND_STILL");
    static final List<String> KEYS = List.of(
            NONE,
            "Q",
            "E",
            "R",
            "ABILITY_4",
            "F",
            "O",
            "DODGE",
            "ALT",
            "MOUSE_LEFT",
            "MOUSE_RIGHT",
            "MOUSE_MIDDLE",
            "1",
            "2",
            "3",
            "4",
            "5",
            "6",
            "7",
            "8",
            "9");

    private static final Map<KeySignal, String> HELD = Map.of(
            KeySignal.CROUCHING, "CTRL",
            KeySignal.WALKING, "ALT",
            KeySignal.SPRINTING, "SHIFT",
            KeySignal.JUMPING, "SPACE",
            KeySignal.STANDING_STILL, "STAND_STILL");

    private static final Map<KeySignal, String> PRESSED = Map.ofEntries(
            Map.entry(KeySignal.ABILITY_1, "Q"),
            Map.entry(KeySignal.ABILITY_2, "E"),
            Map.entry(KeySignal.ABILITY_3, "R"),
            Map.entry(KeySignal.ABILITY_4, "ABILITY_4"),
            Map.entry(KeySignal.USE, "F"),
            Map.entry(KeySignal.GAME_MODE_SWAP, "O"),
            Map.entry(KeySignal.DODGE, "DODGE"),
            Map.entry(KeySignal.WALK_TAP, "ALT"),
            Map.entry(KeySignal.PRIMARY, "MOUSE_LEFT"),
            Map.entry(KeySignal.SECONDARY, "MOUSE_RIGHT"),
            Map.entry(KeySignal.PICK, "MOUSE_MIDDLE"),
            Map.entry(KeySignal.HOTBAR_1, "1"),
            Map.entry(KeySignal.HOTBAR_2, "2"),
            Map.entry(KeySignal.HOTBAR_3, "3"),
            Map.entry(KeySignal.HOTBAR_4, "4"),
            Map.entry(KeySignal.HOTBAR_5, "5"),
            Map.entry(KeySignal.HOTBAR_6, "6"),
            Map.entry(KeySignal.HOTBAR_7, "7"),
            Map.entry(KeySignal.HOTBAR_8, "8"),
            Map.entry(KeySignal.HOTBAR_9, "9"));

    record Picked(String hold, String key, String first, String rest) {

        boolean custom() {
            return CUSTOM.equals(key);
        }
    }

    record Option(String value, String label) {}

    private KeyPicker() {}

    static Picked read(String text) {
        var parsed = KeyBinds.parse(text);
        if (parsed.isFailure()) {
            return new Picked(NONE, CUSTOM, text == null ? "" : text.strip(), "");
        }
        List<KeyCombo> combos = parsed.value().combos();
        if (combos.isEmpty()) {
            return new Picked(NONE, NONE, "", "");
        }
        String first = combos.getFirst().toString();
        String rest = combos.stream().skip(1).map(KeyCombo::toString).collect(Collectors.joining(" | "));
        List<KeyInput> inputs = combos.getFirst().inputs();
        boolean alone = inputs.size() == 1;
        String hold = NONE;
        String key = null;
        for (KeyInput input : inputs) {
            Optional<KeySignal> signal = input.signal(alone);
            if (signal.isEmpty()) {
                return new Picked(NONE, CUSTOM, first, rest);
            }
            if (signal.get().isPress() && PRESSED.containsKey(signal.get()) && key == null) {
                key = PRESSED.get(signal.get());
            } else if (!signal.get().isPress() && HELD.containsKey(signal.get()) && NONE.equals(hold)) {
                hold = HELD.get(signal.get());
            } else {
                return new Picked(NONE, CUSTOM, first, rest);
            }
        }
        return key == null ? new Picked(NONE, CUSTOM, first, rest) : new Picked(hold, key, first, rest);
    }

    static String compose(String hold, String key, String current) {
        Picked before = read(current);
        if (key == null || CUSTOM.equals(key)) {
            return current;
        }
        if (NONE.equals(key)) {
            return KeyBinds.NONE;
        }
        String combo = hold == null || NONE.equals(hold) ? key : hold + "+" + key;
        return before.rest().isEmpty() ? combo : combo + " | " + before.rest();
    }

    static List<Option> holds(Translator tr) {
        return HOLDS.stream()
                .map(hold -> new Option(hold, tr.t("controlcore.panel.keypick.hold." + hold.toLowerCase(Locale.ROOT))))
                .toList();
    }

    static List<Option> keys(Translator tr, Picked picked) {
        List<Option> options = new ArrayList<>();
        if (picked.custom()) {
            options.add(new Option(CUSTOM, tr.t("controlcore.panel.keypick.custom", "keys", picked.first())));
        }
        for (String key : KEYS) {
            options.add(new Option(key, keyLabel(tr, key)));
        }
        return options;
    }

    private static String keyLabel(Translator tr, String key) {
        if (key.length() == 1 && Character.isDigit(key.charAt(0))) {
            return tr.t("controlcore.panel.keypick.key.hotbar", "n", key);
        }
        return tr.t("controlcore.panel.keypick.key." + key.toLowerCase(Locale.ROOT));
    }
}
