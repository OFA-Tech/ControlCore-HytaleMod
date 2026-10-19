package net.ofatech.controlcore.fakes;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.game.Players;

public final class FakePlayers implements Players {

    private final Map<PlayerId, PlayerInfo> online = new ConcurrentHashMap<>();

    public PlayerInfo join(String name) {
        PlayerInfo player = new PlayerInfo(PlayerId.of(UUID.nameUUIDFromBytes(name.getBytes())), name);
        online.put(player.id(), player);
        return player;
    }

    @Override
    public Optional<PlayerInfo> find(PlayerId id) {
        return Optional.ofNullable(online.get(id));
    }

    @Override
    public Optional<PlayerInfo> findByName(String name) {
        return online.values().stream()
                .filter(p -> p.name().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public Collection<PlayerInfo> online() {
        return List.copyOf(online.values());
    }
}
