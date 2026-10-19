package net.ofatech.controlcore.domain.event;

import net.ofatech.controlcore.domain.model.PlayerInfo;

/** A player finished joining a world and is ready to receive messages. */
public record PlayerJoined(PlayerInfo player) implements DomainEvent {}
