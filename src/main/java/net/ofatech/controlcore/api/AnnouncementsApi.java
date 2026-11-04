package net.ofatech.controlcore.api;

import java.util.List;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Rotating announcements: messages sent to everyone every few minutes, in turn or at random, as in the control
 * panel's Automation tab (Announcements) and the {@code /cc-announce-*} commands. Texts take the same placeholders and
 * colour codes as automation messages ({@code {online}}, {@code {max}}, {@code {time}}, {@code {tps}}). Get it with
 * {@link ControlCoreApi#announcements()}.
 *
 * <p>The API acts with full rights, like the console. {@link #actingFor(PlayerId)} gives the same API with a
 * player's rights: admins and players with {@code ofatech.controlcore.announcements} may manage announcements.
 *
 * <pre>{@code
 * api.announcements().add("Vote for us! {online}/{max} online");
 * api.announcements().setSettings(new Settings(true, 15, "random", "chat", 2));
 * }</pre>
 *
 * @since API 2.5.0
 */
public interface AnnouncementsApi {

    /**
     * Returns this API acting for a player. For a player without the right {@link #list()} is empty and every change
     * fails with {@code Announcements.Forbidden}.
     *
     * @param player the player whose rights apply
     * @return the announcements API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    AnnouncementsApi actingFor(PlayerId player);

    /**
     * Lists the announcements in the order they are sent.
     *
     * @return every announcement; empty if this API may not manage them
     */
    List<Announcement> list();

    /**
     * Adds an announcement at the end of the list, turned on.
     *
     * @param text the text, up to 256 characters
     * @return a message with the new announcement's id, or an error such as {@code Announcements.InvalidText},
     *     {@code Announcements.TooMany} or {@code Announcements.Forbidden}
     */
    Result<String> add(String text);

    /**
     * Deletes an announcement.
     *
     * @param id the announcement's id
     * @return a confirmation message, or an error such as {@code Announcements.Unknown} or
     *     {@code Announcements.Forbidden}
     */
    Result<String> remove(String id);

    /**
     * Turns one announcement on or off; announcements that are off are skipped.
     *
     * @param id the announcement's id
     * @param enabled {@code true} to send it
     * @return a confirmation message, or an error such as {@code Announcements.Unknown} or
     *     {@code Announcements.Forbidden}
     */
    Result<String> setEnabled(String id, boolean enabled);

    /**
     * Moves an announcement up or down the list.
     *
     * @param id the announcement's id
     * @param by how many places: negative moves it up, positive down
     * @return a confirmation message, or an error such as {@code Announcements.Unknown},
     *     {@code Announcements.CannotMove} or {@code Announcements.Forbidden}
     */
    Result<String> move(String id, int by);

    /**
     * Reads the settings of the whole list.
     *
     * @return whether announcements run, how often, in which order, where and from how many players online
     */
    Settings settings();

    /**
     * Changes the settings of the whole list.
     *
     * @param settings the new settings
     * @return a confirmation message, or an error such as {@code Announcements.InvalidInterval},
     *     {@code Announcements.InvalidOrder}, {@code Announcements.InvalidChannel},
     *     {@code Announcements.InvalidMinPlayers} or {@code Announcements.Forbidden}
     */
    Result<String> setSettings(Settings settings);

    /**
     * Sends the next announcement now, as the rotation would, even if announcements are off.
     *
     * @return the text sent, or an error such as {@code Announcements.NothingToSend} or
     *     {@code Announcements.Forbidden}
     */
    Result<String> sendNow();

    /**
     * Sends one announcement now, even if it or the rotation is off.
     *
     * @param id the announcement's id
     * @return the text sent, or an error such as {@code Announcements.Unknown} or {@code Announcements.Forbidden}
     */
    Result<String> send(String id);

    /**
     * One announcement.
     *
     * @param id its id, a short number
     * @param text its text, placeholders not filled in
     * @param enabled whether it is sent
     */
    record Announcement(String id, String text, boolean enabled) {}

    /**
     * The settings of the whole list.
     *
     * @param enabled whether announcements are sent
     * @param intervalMinutes the minutes between two announcements, at least 1
     * @param order {@code in-turn} or {@code random} (never the same one twice in a row)
     * @param channel {@code chat}, {@code toast} or {@code warning} (the warning bar)
     * @param minPlayers send only when at least this many players are online; 0 for always
     */
    record Settings(boolean enabled, int intervalMinutes, String order, String channel, int minPlayers) {}
}
