package net.ofatech.controlcore.api;

import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;

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

    /**
     * Shows a small timed message (a toast) in a corner of one player's screen. Up to three toasts stack;
     * a fourth pushes the oldest out. Players choose the corner, or Hytale's own notifications, in their
     * preferences, and can turn toasts off.
     *
     * @param player the player who sees it
     * @param title the bold first line; empty for none
     * @param text the message
     * @param level how important it is; decides the colour
     * @param seconds how long it stays, from 1 to 60
     * @since API 2.2.0
     */
    void toast(PlayerId player, String title, String text, ToastLevel level, int seconds);

    /**
     * Shows a toast to every online player.
     *
     * @param title the bold first line; empty for none
     * @param text the message
     * @param level how important it is; decides the colour
     * @param seconds how long it stays, from 1 to 60
     * @since API 2.2.0
     */
    void toastAll(String title, String text, ToastLevel level, int seconds);

    /**
     * Shows a warning bar to one player (under the compass by default) until it expires or is cleared.
     * Showing a warning with the same id again replaces it.
     *
     * @param player the player who sees it
     * @param id your own name for this warning, used to replace or clear it, for example {@code "restart"}
     * @param text the warning
     * @param level how important it is; decides the colour
     * @param seconds how long it stays; 0 keeps it until {@link #clearWarning} is called
     * @since API 2.2.0
     */
    void warning(PlayerId player, String id, String text, ToastLevel level, int seconds);

    /**
     * Shows a warning bar to every player, including players who join while it is shown.
     *
     * @param id your own name for this warning, used to replace or clear it
     * @param text the warning
     * @param level how important it is; decides the colour
     * @param seconds how long it stays; 0 keeps it until {@link #clearWarningAll} is called
     * @since API 2.2.0
     */
    void warningAll(String id, String text, ToastLevel level, int seconds);

    /**
     * Removes one player's warning.
     *
     * @param player the player
     * @param id the warning's id
     * @since API 2.2.0
     */
    void clearWarning(PlayerId player, String id);

    /**
     * Removes a warning shown with {@link #warningAll}, and the same warning shown to single players.
     *
     * @param id the warning's id
     * @since API 2.2.0
     */
    void clearWarningAll(String id);
}
