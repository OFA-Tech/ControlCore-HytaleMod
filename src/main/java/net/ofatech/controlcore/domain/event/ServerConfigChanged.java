package net.ofatech.controlcore.domain.event;

import net.ofatech.controlcore.domain.model.settings.SettingId;

public record ServerConfigChanged(SettingId id, String oldValue, String newValue) implements DomainEvent {}
