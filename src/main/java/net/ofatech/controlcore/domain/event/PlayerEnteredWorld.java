package net.ofatech.controlcore.domain.event;

import net.ofatech.controlcore.domain.model.PlayerInfo;

public record PlayerEnteredWorld(PlayerInfo player, String world) implements DomainEvent {}
