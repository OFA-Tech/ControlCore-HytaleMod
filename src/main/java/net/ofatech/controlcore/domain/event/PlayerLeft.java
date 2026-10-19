package net.ofatech.controlcore.domain.event;

import net.ofatech.controlcore.domain.model.PlayerInfo;

/** A player disconnected. */
public record PlayerLeft(PlayerInfo player) implements DomainEvent {}
