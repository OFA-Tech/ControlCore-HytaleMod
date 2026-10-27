package net.ofatech.controlcore.domain.model.bans;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.result.Result;

public interface BansView {

    record Entry(String id, String name, String details, boolean active) {

        public String initial() {
            String shown = name.isEmpty() ? id : name;
            return shown.isEmpty() ? "?" : shown.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        public int colour(int colours) {
            return Math.floorMod(id.hashCode(), colours);
        }
    }

    record Length(String id, String label) {}

    List<Entry> list();

    boolean showHeads();

    CompletableFuture<Optional<String>> resolve(String id);

    List<Length> lengths();

    CompletableFuture<Result<String>> ban(String player, String length, String reason);

    Result<String> unban(String id);

    net.ofatech.controlcore.domain.event.Subscription onChange(Runnable listener);
}
