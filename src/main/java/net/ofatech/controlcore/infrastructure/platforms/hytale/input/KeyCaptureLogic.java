package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyCombo;

final class KeyCaptureLogic {

    static final long HOLD_WINDOW_MILLIS = 1200;

    sealed interface Outcome {

        default Optional<String> captured() {
            return this instanceof Captured captured ? Optional.of(captured.keys()) : Optional.empty();
        }

        record Waiting() implements Outcome {}

        record Holding(String holds) implements Outcome {}

        record Unsupported(int keycode) implements Outcome {}

        record Cancelled() implements Outcome {}

        record Captured(String keys) implements Outcome {}
    }

    private final Map<Key, Long> lastSeen = new LinkedHashMap<>();

    Outcome accept(int keycode, boolean repeat, long nowMillis) {
        if (keycode == SdlKeys.ESCAPE) {
            return new Outcome.Cancelled();
        }
        if (SdlKeys.isModifier(keycode)) {
            Optional<Key> hold = SdlKeys.hold(keycode);
            if (hold.isEmpty()) {
                return new Outcome.Waiting();
            }
            lastSeen.put(hold.get(), nowMillis);
            return new Outcome.Holding(holds(nowMillis));
        }
        if (repeat) {
            return new Outcome.Waiting();
        }
        Optional<Key> key = SdlKeys.key(keycode);
        if (key.isEmpty()) {
            return new Outcome.Unsupported(keycode);
        }
        String holds = holds(nowMillis);
        String written = KeyCombo.write(key.get());
        return new Outcome.Captured(holds.isEmpty() ? written : holds + "+" + written);
    }

    private String holds(long nowMillis) {
        return Key.HOLDS.stream()
                .filter(hold -> {
                    Long seen = lastSeen.get(hold);
                    return seen != null && nowMillis - seen <= HOLD_WINDOW_MILLIS;
                })
                .map(KeyCombo::write)
                .collect(Collectors.joining("+"));
    }
}
