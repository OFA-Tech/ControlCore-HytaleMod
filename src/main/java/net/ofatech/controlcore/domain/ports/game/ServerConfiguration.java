package net.ofatech.controlcore.domain.ports.game;

import java.util.List;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.result.Result;

public interface ServerConfiguration {

    ServerSettings read();

    List<ConfigEntry> entries();

    Result<ConfigEntry> write(String path, String value);
}
