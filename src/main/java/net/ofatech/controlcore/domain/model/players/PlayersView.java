package net.ofatech.controlcore.domain.model.players;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.result.Result;

public interface PlayersView {

    enum Filter {
        ALL,
        ONLINE,
        OFFLINE,
        BANNED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Filter of(String id) {
            for (Filter filter : values()) {
                if (filter.id().equals(id)) {
                    return filter;
                }
            }
            return ALL;
        }
    }

    record Entry(String id, String name, boolean online, boolean banned, String status, String ban) {

        public String initial() {
            String shown = name.isEmpty() ? id : name;
            return shown.isEmpty() ? "?" : shown.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        public int colour(int colours) {
            return Math.floorMod(id.hashCode(), colours);
        }

        public boolean matches(Filter filter, String query) {
            boolean kept = switch (filter) {
                case ALL -> true;
                case ONLINE -> online;
                case OFFLINE -> !online;
                case BANNED -> banned;
            };
            String wanted = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
            return kept && (wanted.isEmpty() || name.toLowerCase(Locale.ROOT).contains(wanted) || id.contains(wanted));
        }
    }

    record Choice(String id, String label) {}

    record Rights(boolean kick, boolean gameMode, boolean teleport, boolean ban) {}

    String viewer();

    Rights rights();

    List<Entry> list();

    List<Choice> filters();

    List<Choice> lengths();

    boolean showHeads();

    CompletableFuture<Optional<String>> resolve(String id);

    CompletableFuture<Optional<String>> live(String id);

    CompletableFuture<Result<String>> ban(String player, String length, String reason);

    Result<String> unban(String id);

    Result<String> kick(String id, String reason);

    CompletableFuture<Result<String>> toggleGameMode(String id);

    CompletableFuture<Result<String>> teleportTo(String id);

    CompletableFuture<Result<String>> bring(String id);

    Subscription onChange(Runnable listener);
}
