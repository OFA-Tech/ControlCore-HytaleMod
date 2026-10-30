package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.keys.KeyCombo;
import net.ofatech.controlcore.domain.model.keys.KeyInput;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.model.text.Translator;

final class KeyPicker {

    static final String NONE = "NONE";
    static final String CUSTOM = "CUSTOM";

    static final List<String> HOLDS = Stream.of(
                    Stream.of(NONE), Key.HOLDS.stream().map(Key::name), Stream.of(Key.SPACE.name(), "STAND_STILL"))
            .flatMap(names -> names)
            .toList();
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
        return read(text, false);
    }

    static Picked read(String text, boolean allKeys) {
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
            if (signal.isEmpty() && allKeys && key == null && input instanceof Key extra && isExtra(extra)) {
                key = extra.name();
                continue;
            }
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

    static String withDetected(String detected, String current) {
        String rest = read(current).rest();
        return rest.isEmpty() ? detected : detected + " | " + rest;
    }

    static String describe(Translator tr, String keys) {
        Picked picked = read(keys, true);
        if (picked.custom() || NONE.equals(picked.key())) {
            return keys;
        }
        String key = keyLabel(tr, picked.key());
        return NONE.equals(picked.hold())
                ? key
                : tr.t("controlcore.panel.keypick.hold." + picked.hold().toLowerCase(Locale.ROOT)) + " + " + key;
    }

    static boolean followsGameAction(String keys) {
        return firstCombo(keys)
                .map(combo -> combo.inputs().stream()
                        .anyMatch(input -> input.signal(combo.inputs().size() == 1)
                                .filter(KeySignal::isPress)
                                .isPresent()))
                .orElse(false);
    }

    static boolean sharesKeys(String a, String b) {
        Set<Object> first = triggers(a);
        return triggers(b).stream().anyMatch(first::contains);
    }

    static boolean heard(String keys) {
        return firstCombo(keys).map(combo -> combo.trigger().isSuccess()).orElse(false);
    }

    private static Optional<KeyCombo> firstCombo(String keys) {
        var parsed = KeyBinds.parse(keys);
        return parsed.isSuccess() && !parsed.value().combos().isEmpty()
                ? Optional.of(parsed.value().combos().getFirst())
                : Optional.empty();
    }

    private static Set<Object> triggers(String keys) {
        var parsed = KeyBinds.parse(keys);
        if (parsed.isFailure()) {
            return Set.of();
        }
        Set<Object> triggers = new HashSet<>();
        for (KeyCombo combo : parsed.value().combos()) {
            var trigger = combo.trigger();
            triggers.add(trigger.isSuccess() ? trigger.value() : new HashSet<>(combo.inputs()));
        }
        return triggers;
    }

    static List<Option> holds(Translator tr) {
        return HOLDS.stream()
                .map(hold -> new Option(hold, tr.t("controlcore.panel.keypick.hold." + hold.toLowerCase(Locale.ROOT))))
                .toList();
    }

    static List<Option> keys(Translator tr, Picked picked) {
        return keys(tr, picked, false);
    }

    static List<Option> keys(Translator tr, Picked picked, boolean allKeys) {
        List<Option> options = new ArrayList<>();
        if (picked.custom()) {
            options.add(new Option(CUSTOM, tr.t("controlcore.panel.keypick.custom", "keys", picked.first())));
        }
        for (String key : KEYS) {
            options.add(new Option(key, keyLabel(tr, key)));
        }
        if (allKeys) {
            for (Key extra : Key.values()) {
                if (isExtra(extra)) {
                    options.add(
                            new Option(extra.name(), tr.t("controlcore.panel.keypick.unheard", "key", extra.label())));
                }
            }
        }
        return options;
    }

    private static boolean isExtra(Key key) {
        return !key.heardByServer();
    }

    private static String keyLabel(Translator tr, String key) {
        if (!KEYS.contains(key)) {
            return KeyCombo.parse(key)
                    .map(combo -> combo.inputs().getFirst().label())
                    .orElse(key);
        }
        if (key.length() == 1 && Character.isDigit(key.charAt(0))) {
            return tr.t("controlcore.panel.keypick.key.hotbar", "n", key);
        }
        return tr.t("controlcore.panel.keypick.key." + key.toLowerCase(Locale.ROOT));
    }
}
