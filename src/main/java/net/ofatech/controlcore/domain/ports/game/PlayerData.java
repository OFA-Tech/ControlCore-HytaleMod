package net.ofatech.controlcore.domain.ports.game;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;

/** Small persistent key/value data per player. */
public interface PlayerData {

    Optional<String> get(PlayerId player, String key);

    void put(PlayerId player, String key, String value);

    void remove(PlayerId player, String key);

    /** Writes pending changes to storage. */
    void flush();
}
