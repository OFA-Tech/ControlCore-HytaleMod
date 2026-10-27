package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

public interface PlayerContext {

    record Context(String world, String gameMode, boolean inCombat) {

        public static final Context UNKNOWN = new Context("", "", false);
    }

    Context current(PlayerId player);
}
