package net.ofatech.controlcore.domain.ports.game;

import java.util.Collection;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;

public interface Players {

    Optional<PlayerInfo> find(PlayerId id);

    Optional<PlayerInfo> findByName(String name);

    Collection<PlayerInfo> online();
}
