package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.result.Result;

/** The host server's own configuration (name, MOTD, limits). */
public interface ServerConfiguration {

    ServerSettings read();

    /** @return the values a fresh server starts with */
    ServerSettings defaults();

    /**
     * Applies the editable values of {@code settings} (everything except the password) and saves the server
     * config. Changes take effect where the server supports it, for example the view radius of online players.
     *
     * @return the configuration after the change, or an error if it could not be applied or saved
     */
    Result<ServerSettings> write(ServerSettings settings);
}
