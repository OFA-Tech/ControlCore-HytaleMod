package net.ofatech.controlcore.api;

import java.util.Collection;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;

/**
 * Players' sessions: first join, last seen and total playtime, saved per player.
 *
 * <pre>{@code
 * api.sessions().find(player).ifPresent(session -> log(session.playtime().toHours() + " h"));
 * }</pre>
 *
 * @since API 2.1.0
 */
public interface SessionsApi {

    /**
     * Returns a player's session data.
     *
     * @param player the player
     * @return the session, or empty if the player never joined while ControlCore was installed
     */
    Optional<PlayerSession> find(PlayerId player);

    /**
     * Returns the sessions of every online player.
     *
     * @return the online players' sessions
     */
    Collection<PlayerSession> online();

    /**
     * Returns the world a player is in, as last reported by the game.
     *
     * @param player the player
     * @return the world's name, or empty if unknown or offline
     */
    Optional<String> world(PlayerId player);
}
