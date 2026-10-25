package net.ofatech.controlcore.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;

/**
 * What ControlCore knows about a player's time on the server.
 *
 * @param player the player
 * @param name the player's last known name
 * @param firstJoin when the player first joined while ControlCore was installed
 * @param lastSeen when the player last joined or left
 * @param onlineSince when the current session started, or empty if the player is offline
 * @param playtime total time online, including the current session
 * @since API 2.1.0
 */
public record PlayerSession(
        PlayerId player,
        String name,
        Instant firstJoin,
        Instant lastSeen,
        Optional<Instant> onlineSince,
        Duration playtime) {

    /**
     * Whether the player is online now.
     *
     * @return true while the player is online
     */
    public boolean online() {
        return onlineSince.isPresent();
    }
}
