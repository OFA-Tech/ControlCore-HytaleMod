package net.ofatech.controlcore.domain.ports.game;

import java.util.Collection;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;

public interface Players {

    Optional<PlayerInfo> find(PlayerId id);

    Optional<PlayerInfo> findByName(String name);

    Collection<PlayerInfo> online();

    /** @return the language the player's game is set to, such as {@code pt-BR}; read it each time, it can change */
    default Optional<String> language(PlayerId id) {
        return Optional.empty();
    }
}
