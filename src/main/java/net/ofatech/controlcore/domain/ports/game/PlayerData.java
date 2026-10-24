package net.ofatech.controlcore.domain.ports.game;

import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;

public interface PlayerData {

    Optional<String> get(PlayerId player, String key);

    void put(PlayerId player, String key, String value);

    void remove(PlayerId player, String key);

    void flush();

    Map<PlayerId, Map<String, String>> all();
}
