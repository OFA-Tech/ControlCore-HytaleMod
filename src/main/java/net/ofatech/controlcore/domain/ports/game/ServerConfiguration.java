package net.ofatech.controlcore.domain.ports.game;

import java.util.List;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.result.Result;

/** The host server's own configuration file. */
public interface ServerConfiguration {

    /** @return the commonly used values, typed */
    ServerSettings read();

    /** @return every key of the configuration, in file order, with whether it may be changed */
    List<ConfigEntry> entries();

    /**
     * Changes one key and saves the configuration. Changes take effect where the server supports it, for
     * example the view radius of online players.
     *
     * @param path {@link ConfigEntry#path()}
     * @param value new value, written as {@link ConfigEntry#kind()} describes
     * @return the entry after the change, or an error such as {@code ServerConfig.ReadOnly},
     *     {@code ServerConfig.InvalidValue} or {@code ServerConfig.UnknownKey}
     */
    Result<ConfigEntry> write(String path, String value);
}
