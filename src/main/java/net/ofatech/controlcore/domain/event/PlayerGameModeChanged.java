package net.ofatech.controlcore.domain.event;

import net.ofatech.controlcore.domain.model.PlayerInfo;

public record PlayerGameModeChanged(PlayerInfo player) implements DomainEvent {}
