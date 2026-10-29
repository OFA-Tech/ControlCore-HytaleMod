package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.keys.Key;

final class KeyCaptureLogic {

    static final long HOLD_WINDOW_MILLIS = 600;

    private String hold;
    private long heldUntil = Long.MIN_VALUE;

    Optional<Optional<String>> accept(int keycode, boolean repeat, long nowMillis) {
        if (keycode == SdlKeys.ESCAPE) {
            return Optional.of(Optional.empty());
        }
        if (SdlKeys.isModifier(keycode)) {
            SdlKeys.hold(keycode).ifPresent(name -> {
                hold = name;
                heldUntil = nowMillis + HOLD_WINDOW_MILLIS;
            });
            return Optional.empty();
        }
        if (repeat) {
            return Optional.empty();
        }
        Optional<Key> key = SdlKeys.key(keycode);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        String written = key.get().name().startsWith("NUM_")
                ? key.get().label()
                : key.get().name();
        boolean held = hold != null && nowMillis <= heldUntil;
        return Optional.of(Optional.of(held ? hold + "+" + written : written));
    }
}
