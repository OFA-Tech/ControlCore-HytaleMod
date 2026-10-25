package net.ofatech.controlcore.domain.ports.game;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;

public interface PlayerTuning {

    record Tuning(int speedPercent, int jumpPercent, int sectionsPerSecond, int sectionsPerTick) {

        public static final Tuning NONE = new Tuning(100, 100, 0, 0);
    }

    PlayerTuning NONE = new PlayerTuning() {
        @Override
        public Optional<String> worldOf(PlayerId player) {
            return Optional.empty();
        }

        @Override
        public void apply(PlayerId player, Tuning tuning) {}
    };

    Optional<String> worldOf(PlayerId player);

    void apply(PlayerId player, Tuning tuning);
}
