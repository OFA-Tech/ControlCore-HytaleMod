package net.ofatech.controlcore.domain.event;

import net.ofatech.controlcore.domain.model.settings.SettingId;

/**
 * A value of the server's own configuration was changed through ControlCore (the control panel). Changes
 * made with Hytale's commands or by editing {@code config.json} are not reported.
 *
 * @param id the field, for example {@code server:motd}
 * @param oldValue previous value, as text
 * @param newValue new value, as text
 */
public record ServerConfigChanged(SettingId id, String oldValue, String newValue) implements DomainEvent {}
