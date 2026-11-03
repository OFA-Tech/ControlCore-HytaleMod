package net.ofatech.controlcore.domain.event;

import net.ofatech.controlcore.domain.model.PlayerInfo;

/**
 * A player left the server.
 *
 * @param player the player who left
 */
public record PlayerLeft(PlayerInfo player) implements DomainEvent {}
