package net.ofatech.controlcore.domain.model.keys;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Keys and game actions used together, for example {@code CTRL + F} or {@code WALK + NUM_3}.
 *
 * @param inputs the parts, in the order written; at least one, no repeats
 */
public record KeyCombo(List<KeyInput> inputs) {

    private static final Map<String, KeyInput> ALIASES = Map.ofEntries(
            Map.entry("CONTROL", Key.CTRL),
            Map.entry("LCTRL", Key.CTRL),
            Map.entry("RCTRL", Key.CTRL),
            Map.entry("LSHIFT", Key.SHIFT),
            Map.entry("RSHIFT", Key.SHIFT),
            Map.entry("LALT", Key.ALT),
            Map.entry("RALT", Key.ALT),
            Map.entry("CAPSLOCK", Key.CAPS),
            Map.entry("CAPS_LOCK", Key.CAPS),
            Map.entry("ESC", Key.ESCAPE),
            Map.entry("RETURN", Key.ENTER),
            Map.entry("DEL", Key.DELETE),
            Map.entry("PGUP", Key.PAGE_UP),
            Map.entry("PGDN", Key.PAGE_DOWN),
            Map.entry("LMB", Key.MOUSE_LEFT),
            Map.entry("MOUSE1", Key.MOUSE_LEFT),
            Map.entry("RMB", Key.MOUSE_RIGHT),
            Map.entry("MOUSE2", Key.MOUSE_RIGHT),
            Map.entry("MMB", Key.MOUSE_MIDDLE),
            Map.entry("MOUSE3", Key.MOUSE_MIDDLE),
            Map.entry("MOUSE4", Key.MOUSE_4),
            Map.entry("MOUSE5", Key.MOUSE_5));

    public KeyCombo {
        if (inputs == null || inputs.isEmpty()) {
            throw new DomainException("A key combination needs at least one key");
        }
        if (inputs.stream().anyMatch(java.util.Objects::isNull)) {
            throw new DomainException("A key combination cannot contain null");
        }
        if (new LinkedHashSet<>(inputs).size() != inputs.size()) {
            throw new DomainException("A key combination cannot repeat a key: " + inputs);
        }
        inputs = List.copyOf(inputs);
    }

    /** @return the combination of {@code inputs} */
    public static KeyCombo of(KeyInput... inputs) {
        return new KeyCombo(inputs == null ? null : java.util.Arrays.asList(inputs));
    }

    /**
     * Parses text such as {@code ctrl+f}, {@code Alt + 3} or {@code crouch+interact}. Names are those of
     * {@link Key} and {@link GameAction}, case-insensitive; digits stand for {@code NUM_0} to {@code NUM_9}.
     *
     * @return the combination, or the error {@code Keys.InvalidCombo}
     */
    public static Result<KeyCombo> parse(String text) {
        if (text == null || text.isBlank()) {
            return invalid("Write at least one key, for example ctrl+f.");
        }
        List<KeyInput> inputs = new ArrayList<>();
        for (String part : text.split("\\+")) {
            Optional<KeyInput> input = input(part);
            if (input.isEmpty()) {
                return invalid("'" + part.strip() + "' is not a key or game action. Examples: ctrl, f, 3, mouse_left,"
                        + " crouch, walk_forward.");
            }
            if (inputs.contains(input.get())) {
                return invalid(input.get().name() + " is used twice in '" + text.strip() + "'.");
            }
            inputs.add(input.get());
        }
        return Result.success(new KeyCombo(inputs));
    }

    /**
     * Works out how the server notices this combination during play.
     *
     * @return the trigger, or the error {@code Keys.NotDetectable} naming the inputs the client never reports
     */
    public Result<KeyTrigger> trigger() {
        boolean alone = inputs.size() == 1;
        List<String> missing = new ArrayList<>();
        KeySignal press = null;
        Set<KeySignal> held = EnumSet.noneOf(KeySignal.class);
        for (KeyInput input : inputs) {
            Optional<KeySignal> signal = input.signal(alone);
            if (signal.isEmpty()) {
                missing.add(input.label());
            } else if (!signal.get().isPress()) {
                held.add(signal.get());
            } else if (press != null) {
                return Result.failure(Error.validation(
                                "Keys.NotDetectable",
                                "Only one of these can be in a combination: Q, E, R, F, O, 1-9 and the mouse buttons.")
                        .localized("controlcore.errors.keys.notdetectable"));
            } else {
                press = signal.get();
            }
        }
        if (!missing.isEmpty()) {
            return Result.failure(Error.validation(
                            "Keys.NotDetectable",
                            "Hytale does not tell servers about " + String.join(", ", missing) + " yet.")
                    .localized("controlcore.errors.keys.notdetectable_2", "missing", String.join(", ", missing)));
        }
        if (held.contains(KeySignal.MOVING) && held.contains(KeySignal.STANDING_STILL)) {
            return Result.failure(Error.validation("Keys.NotDetectable", "Move and Stand still cannot be combined.")
                    .localized("controlcore.errors.keys.notdetectable_3"));
        }
        return Result.success(new KeyTrigger(Optional.ofNullable(press), held));
    }

    /** @return the combination as players write it, for example {@code CTRL + F} */
    @Override
    public String toString() {
        return inputs.stream().map(KeyCombo::written).collect(Collectors.joining(" + "));
    }

    private static String written(KeyInput input) {
        return input instanceof Key key && key.name().startsWith("NUM_") ? key.label() : input.name();
    }

    private static Optional<KeyInput> input(String part) {
        String name = part.strip().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (name.isEmpty()) {
            return Optional.empty();
        }
        if (name.length() == 1 && Character.isDigit(name.charAt(0))) {
            return Optional.of(Key.valueOf("NUM_" + name));
        }
        if (ALIASES.containsKey(name)) {
            return Optional.of(ALIASES.get(name));
        }
        for (Key key : Key.values()) {
            if (key.name().equals(name)) {
                return Optional.of(key);
            }
        }
        for (GameAction action : GameAction.values()) {
            if (action.name().equals(name)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }

    private static <T> Result<T> invalid(String description) {
        return Result.failure(Error.validation("Keys.InvalidCombo", description)
                .localized("controlcore.errors.keys.invalidcombo", "reason", description));
    }
}
