package net.ofatech.controlcore.domain.event;

import net.ofatech.controlcore.domain.model.PlayerInfo;

public record PlayerLeft(PlayerInfo player) implements DomainEvent {}
