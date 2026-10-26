package net.ofatech.controlcore.api;

import net.ofatech.controlcore.domain.model.PlayerId;

/**
 * Sends chat messages and on-screen titles to players. Offline players are skipped silently.
 *
 * <pre>{@code
 * api.notifications().title(player, "Welcome!", "Have fun");
 * api.notifications().toAdmins("Backup finished");
 * }</pre>
 *
 * @since API 2.1.0
 */
public interface NotificationsApi {

    /**
     * Sends a chat message to one player.
     *
     * @param player the player to message
     * @param text the message text
     */
    void message(PlayerId player, String text);

    /**
     * Sends a coloured chat message to one player.
     *
     * @param player the player to message
     * @param text the message text
     * @param color the colour as {@code #rrggbb}
     */
    void message(PlayerId player, String text, String color);

    /**
     * Sends a chat message to every online player.
     *
     * @param text the message text
     */
    void broadcast(String text);

    /**
     * Sends a chat message to every online ControlCore admin.
     *
     * @param text the message text
     */
    void toAdmins(String text);

    /**
     * Shows a large title in the middle of one player's screen.
     *
     * @param player the player who sees it
     * @param title the big line
     * @param subtitle the smaller line under it; empty for none
     */
    void title(PlayerId player, String title, String subtitle);

    /**
     * Shows a large title in the middle of every online player's screen.
     *
     * @param title the big line
     * @param subtitle the smaller line under it; empty for none
     */
    void titleAll(String title, String subtitle);
}
