package net.ofatech.controlcore.fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.PlayerData;

public final class FakePlayerData implements PlayerData {

    private final Map<PlayerId, Map<String, String>> values = new ConcurrentHashMap<>();
    public int flushes;

    @Override
    public Optional<String> get(PlayerId player, String key) {
        return Optional.ofNullable(values.getOrDefault(player, Map.of()).get(key));
    }

    @Override
    public void put(PlayerId player, String key, String value) {
        values.computeIfAbsent(player, id -> new ConcurrentHashMap<>()).put(key, value);
    }

    @Override
    public void remove(PlayerId player, String key) {
        Map<String, String> stored = values.get(player);
        if (stored != null) {
            stored.remove(key);
        }
    }

    @Override
    public void flush() {
        flushes++;
    }

    @Override
    public Map<PlayerId, Map<String, String>> all() {
        Map<PlayerId, Map<String, String>> copy = new HashMap<>();
        values.forEach((player, stored) -> copy.put(player, Map.copyOf(stored)));
        return copy;
    }
}
