package net.ofatech.controlcore.fakes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.config.PlayerNameStore;

public final class FakePlayerNameStore implements PlayerNameStore {

    public final Map<PlayerId, String> names = new ConcurrentHashMap<>();

    @Override
    public Map<PlayerId, String> all() {
        return Map.copyOf(names);
    }

    @Override
    public void put(PlayerId id, String name) {
        names.put(id, name);
    }
}
