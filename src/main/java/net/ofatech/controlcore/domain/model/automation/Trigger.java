package net.ofatech.controlcore.domain.model.automation;

import java.util.Locale;
import java.util.Optional;

public enum Trigger {
    JOIN(Value.NONE, 0, true),
    FIRST_JOIN(Value.NONE, 0, true),
    LEAVE(Value.NONE, 0, true),
    WORLD_ENTER(Value.OPTIONAL, 0, true),
    EVERY_MINUTES(Value.REQUIRED, 0, false),
    DAILY_AT(Value.REQUIRED, 0, false),
    TPS_BELOW(Value.REQUIRED, 300, false),
    ONLINE_REACHES(Value.REQUIRED, 60, false),
    SERVER_EMPTY(Value.NONE, 60, false),
    SETTING_CHANGED(Value.OPTIONAL, 0, false),
    MOD_TRIGGER(Value.REQUIRED, 0, false);

    public enum Value {
        NONE,
        OPTIONAL,
        REQUIRED
    }

    private final Value value;
    private final int defaultCooldown;
    private final boolean playerBased;

    Trigger(Value value, int defaultCooldown, boolean playerBased) {
        this.value = value;
        this.defaultCooldown = defaultCooldown;
        this.playerBased = playerBased;
    }

    public Value value() {
        return value;
    }

    public int defaultCooldown() {
        return defaultCooldown;
    }

    public boolean playerBased() {
        return playerBased;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<Trigger> of(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Trigger trigger : values()) {
            if (trigger.name().equals(wanted)) {
                return Optional.of(trigger);
            }
        }
        return Optional.empty();
    }
}
