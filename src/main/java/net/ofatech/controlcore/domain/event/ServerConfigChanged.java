package net.ofatech.controlcore.domain.event;

import net.ofatech.controlcore.domain.model.settings.SettingId;

/**
 * A {@code config.json} or world key changed.
 *
 * @param id the key, for example {@code server:motd} or {@code world:default.pvp-enabled}
 * @param oldValue the value before, as text
 * @param newValue the value after, as text
 */
public record ServerConfigChanged(SettingId id, String oldValue, String newValue) implements DomainEvent {}
