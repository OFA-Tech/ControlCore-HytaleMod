package net.ofatech.controlcore.domain.event;

import net.ofatech.controlcore.domain.model.PlayerInfo;

/**
 * A player joined the server.
 *
 * @param player the player who joined
 */
public record PlayerJoined(PlayerInfo player) implements DomainEvent {}
