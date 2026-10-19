package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.ServerSettings;

/** The host server's own configuration (name, MOTD, limits). */
public interface ServerConfiguration {

    ServerSettings read();
}
