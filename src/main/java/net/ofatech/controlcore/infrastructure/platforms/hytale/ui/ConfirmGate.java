package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import java.util.Optional;

final class ConfirmGate {

    private final long millis;
    private Optional<String> armed = Optional.empty();
    private long armedAt;

    ConfirmGate(long millis) {
        this.millis = millis;
    }

    boolean confirmed(String key) {
        long now = System.currentTimeMillis();
        if (armed.filter(key::equals).isPresent() && now - armedAt < millis) {
            armed = Optional.empty();
            return true;
        }
        armed = Optional.of(key);
        armedAt = now;
        return false;
    }

    void disarm() {
        armed = Optional.empty();
    }
}
