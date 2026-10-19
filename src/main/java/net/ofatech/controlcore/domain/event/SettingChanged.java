package net.ofatech.controlcore.domain.event;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;

/**
 * A registered setting got a new value, or was reset to its default.
 *
 * @param id the setting
 * @param scope server-wide or per player
 * @param player the player whose preference changed; empty for server settings
 * @param oldValue previous effective value, as text
 * @param newValue new effective value, as text
 */
public record SettingChanged(
        SettingId id, SettingScope scope, Optional<PlayerId> player, String oldValue, String newValue)
        implements DomainEvent {}
