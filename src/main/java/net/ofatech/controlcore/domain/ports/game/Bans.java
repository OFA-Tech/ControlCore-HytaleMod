package net.ofatech.controlcore.domain.ports.game;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.model.PlayerId;

public interface Bans {

    record Ban(PlayerId target, Optional<PlayerId> by, Instant at, Optional<Instant> expires, String reason) {

        public Ban {
            reason = reason == null ? "" : reason;
        }

        public boolean inEffect(Instant now) {
            return expires.map(end -> end.isAfter(now)).orElse(true);
        }
    }

    record Profile(PlayerId id, String name) {}

    Bans NONE = new Bans() {
        @Override
        public List<Ban> all() {
            return List.of();
        }

        @Override
        public boolean ban(Ban ban) {
            return false;
        }

        @Override
        public boolean unban(PlayerId target) {
            return false;
        }

        @Override
        public CompletableFuture<Optional<Profile>> lookup(String nameOrId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Optional<String>> nameOf(PlayerId id) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    };

    List<Ban> all();

    boolean ban(Ban ban);

    boolean unban(PlayerId target);

    CompletableFuture<Optional<Profile>> lookup(String nameOrId);

    CompletableFuture<Optional<String>> nameOf(PlayerId id);
}
