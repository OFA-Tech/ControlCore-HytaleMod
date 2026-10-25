package net.ofatech.controlcore.domain.model.history;

import java.time.Instant;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.PlayerId;

public record ChangeRecord(
        long id,
        Instant at,
        Actor actor,
        Source source,
        Kind kind,
        String target,
        String oldValue,
        String newValue,
        boolean restart,
        long undoes) {

    public enum Kind {
        SETTING,
        SERVER_CONFIG,
        SERVER_KEYS,
        CUSTOM_BIND,
        PERMISSION,
        MOD,
        BAN
    }

    public enum Source {
        PANEL,
        COMMAND,
        API,
        ROLLBACK,
        SNAPSHOT,
        OUTSIDE,
        AUTOMATION
    }

    public record Actor(Optional<PlayerId> player, String name) {

        public static final Actor MOD = new Actor(Optional.empty(), "");

        public Actor {
            Guard.notNull(player, "player");
            name = name == null ? "" : name;
        }

        public static Actor player(PlayerId player, String name) {
            return new Actor(Optional.of(player), name);
        }

        public static Actor console(String name) {
            return new Actor(Optional.empty(), name == null || name.isBlank() ? "Console" : name);
        }

        public boolean isMod() {
            return player.isEmpty() && name.isEmpty();
        }
    }

    public ChangeRecord {
        Guard.notNull(at, "at");
        Guard.notNull(actor, "actor");
        Guard.notNull(source, "source");
        Guard.notNull(kind, "kind");
        Guard.notNull(target, "target");
        oldValue = oldValue == null ? "" : oldValue;
        newValue = newValue == null ? "" : newValue;
    }

    public boolean isRollback() {
        return undoes > 0;
    }
}
