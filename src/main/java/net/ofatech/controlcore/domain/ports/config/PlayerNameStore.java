package net.ofatech.controlcore.domain.ports.config;

import java.util.Map;
import net.ofatech.controlcore.domain.model.PlayerId;

public interface PlayerNameStore {

    Map<PlayerId, String> all();

    void put(PlayerId id, String name);
}
