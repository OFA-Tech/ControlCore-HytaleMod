package net.ofatech.controlcore.fakes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.PlayerData;

public final class FakePlayerData implements PlayerData {

    private final Map<String, String> values = new ConcurrentHashMap<>();
    public int flushes;

    @Override
    public Optional<String> get(PlayerId player, String key) {
        return Optional.ofNullable(values.get(player + "/" + key));
    }

    @Override
    public void put(PlayerId player, String key, String value) {
        values.put(player + "/" + key, value);
    }

    @Override
    public void remove(PlayerId player, String key) {
        values.remove(player + "/" + key);
    }

    @Override
    public void flush() {
        flushes++;
    }
}
