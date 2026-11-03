package net.ofatech.controlcore.domain.model.history;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.result.Result;

public interface HistoryView {

    enum Filter {
        ALL(null),
        SETTINGS(ChangeRecord.Kind.SETTING),
        CONFIG(ChangeRecord.Kind.SERVER_CONFIG),
        WORLDS(ChangeRecord.Kind.SERVER_CONFIG),
        KEYS(ChangeRecord.Kind.SERVER_KEYS),
        BINDS(ChangeRecord.Kind.CUSTOM_BIND),
        PERMISSIONS(ChangeRecord.Kind.PERMISSION),
        MODS(ChangeRecord.Kind.MOD),
        BANS(ChangeRecord.Kind.BAN),
        PLAYERS(ChangeRecord.Kind.PLAYER);

        private final ChangeRecord.Kind kind;

        Filter(ChangeRecord.Kind kind) {
            this.kind = kind;
        }

        public static Filter of(String name) {
            return find(name).orElse(ALL);
        }

        public static Optional<Filter> find(String name) {
            return Arrays.stream(values())
                    .filter(filter -> filter.name().equalsIgnoreCase(name))
                    .findFirst();
        }

        public boolean accepts(Entry entry) {
            if (this == KEYS && entry.kind() == ChangeRecord.Kind.GAME_ACTION) {
                return true;
            }
            if (kind == null || entry.kind() != kind) {
                return kind == null;
            }
            boolean world = entry.target().startsWith("world:");
            return this == WORLDS ? world : this != CONFIG || !world;
        }
    }

    record Entry(
            long id,
            ChangeRecord.Kind kind,
            String target,
            String when,
            String who,
            String source,
            String what,
            String before,
            String after,
            boolean restart,
            boolean rollback,
            boolean rolledBack,
            boolean canRollback) {}

    List<Entry> entries();

    List<Entry> entries(int newest);

    int total();

    long newest();

    Set<String> targets();

    boolean canRollback();

    Result<String> rollback(long id);

    boolean canClear();

    Result<String> clear(Optional<Duration> olderThan);
}
