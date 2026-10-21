package net.ofatech.controlcore.domain.event;

import net.ofatech.controlcore.domain.model.PlayerInfo;

public record PlayerJoined(PlayerInfo player) implements DomainEvent {}
