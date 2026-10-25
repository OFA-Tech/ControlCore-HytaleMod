package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.Bans;

public final class FakeBans implements Bans {

    public final Map<PlayerId, Ban> bans = new ConcurrentHashMap<>();
    public final Map<String, PlayerId> known = new ConcurrentHashMap<>();

    public PlayerId add(String name) {
        PlayerId id = PlayerId.of(UUID.nameUUIDFromBytes(name.getBytes()));
        known.put(name.toLowerCase(), id);
        return id;
    }

    @Override
    public List<Ban> all() {
        return List.copyOf(bans.values());
    }

    @Override
    public boolean ban(Ban ban) {
        bans.put(ban.target(), ban);
        return true;
    }

    @Override
    public boolean unban(PlayerId target) {
        return bans.remove(target) != null;
    }

    @Override
    public CompletableFuture<Optional<Profile>> lookup(String nameOrId) {
        PlayerId id = known.get(nameOrId.strip().toLowerCase());
        return CompletableFuture.completedFuture(
                Optional.ofNullable(id).map(found -> new Profile(found, nameOrId.strip())));
    }

    @Override
    public CompletableFuture<Optional<String>> nameOf(PlayerId id) {
        return CompletableFuture.completedFuture(known.entrySet().stream()
                .filter(entry -> entry.getValue().equals(id))
                .map(Map.Entry::getKey)
                .findFirst());
    }
}
