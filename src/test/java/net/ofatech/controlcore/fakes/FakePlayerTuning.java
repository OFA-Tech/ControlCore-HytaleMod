package net.ofatech.controlcore.fakes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.PlayerTuning;

public final class FakePlayerTuning implements PlayerTuning {

    public final Map<PlayerId, String> worlds = new ConcurrentHashMap<>();
    public final Map<PlayerId, Tuning> applied = new ConcurrentHashMap<>();

    @Override
    public Optional<String> worldOf(PlayerId player) {
        return Optional.ofNullable(worlds.get(player));
    }

    @Override
    public void apply(PlayerId player, Tuning tuning) {
        applied.put(player, tuning);
    }
}
