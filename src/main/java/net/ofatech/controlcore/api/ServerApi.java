package net.ofatech.controlcore.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The server as a whole: maintenance mode and restarts. Maintenance mode is as in the control panel's Server Configs -> Players view (Whitelisted
 * filter) and {@code /cc-maintenance}; restarts as the Restart buttons and {@code /cc-restart}. Get it with {@link ControlCoreApi#server()}.
 *
 * <p>While maintenance mode is on, only admins and players with {@code ofatech.controlcore.maintenance.join} or
 * {@code ofatech.controlcore.maintenance} may join; turning it on kicks every other online player with the
 * maintenance message. A maintenance MOTD, if given, replaces config.json {@code MOTD} while it is on and the old one
 * comes back when it is turned off.
 *
 * <p>The API acts with full rights, like the console. {@link #actingFor(PlayerId)} gives the same API with a
 * player's rights: admins and players with {@code ofatech.controlcore.maintenance} may change maintenance mode
 * (only admins may set its MOTD; for others the MOTD is ignored and the reply says so), admins and players with
 * {@code ofatech.controlcore.restart} may restart.
 *
 * <pre>{@code
 * api.server().setMaintenance(true, new MaintenanceOptions("Back at 18:00", "", Optional.of(Duration.ofHours(1))));
 * }</pre>
 *
 * @since API 2.5.0
 */
public interface ServerApi {

    /**
     * Returns this API acting for a player. Players without the maintenance right get every change refused with
     * {@code Maintenance.Forbidden}; reading the state is always allowed.
     *
     * @param player the player whose rights apply
     * @return the server API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    ServerApi actingFor(PlayerId player);

    /**
     * Reads maintenance mode.
     *
     * @return whether it is on, its message and MOTD, since when and until when
     */
    Maintenance maintenance();

    /**
     * Turns maintenance mode on or off. Turning it on kicks every online player who may not join during
     * maintenance. Recorded in History, where rolling it back turns it the other way.
     *
     * @param on {@code true} to turn it on
     * @param options the message, MOTD and automatic end when turning it on; ignored when turning it off
     * @return a message saying what happened, or an error such as {@code Maintenance.AlreadyOn},
     *     {@code Maintenance.AlreadyOff}, {@code Maintenance.InvalidText} or {@code Maintenance.Forbidden}
     */
    Result<String> setMaintenance(boolean on, MaintenanceOptions options);

    /**
     * Starts a restart countdown, as the Restart button and {@code /cc-restart} do. Everyone is warned along the
     * way; at zero ControlCore makes a backup first if "Back up before restarting" is on, kicks the players with a
     * "back soon" message and stops the server. The host must start the server again: a server cannot start itself.
     * Admins and players with {@code ofatech.controlcore.restart} may restart.
     *
     * @param delay the countdown from 10 seconds to 24 hours; {@code null} for the "Restart countdown" setting
     * @param reason shown to players with the warnings; {@code null} or empty for none
     * @return a message, or an error such as {@code Restart.Running}, {@code Restart.BadDelay} or
     *     {@code Restart.Forbidden}
     */
    Result<String> restart(Duration delay, String reason);

    /**
     * Cancels the restart countdown and tells everyone.
     *
     * @return a message, or an error such as {@code Restart.NotRunning} or {@code Restart.TooLate}
     */
    Result<String> cancelRestart();

    /**
     * Reads the restart countdown.
     *
     * @return whether a restart counts down, how long is left and its reason
     */
    RestartStatus restartStatus();

    /**
     * The restart countdown as it is now.
     *
     * @param counting whether a restart is counting down or the server is stopping
     * @param remaining time left; empty when not counting
     * @param reason the reason shown to players; empty for none
     */
    record RestartStatus(boolean counting, Optional<Duration> remaining, String reason) {}

    /**
     * Maintenance mode as it is now.
     *
     * @param on whether it is on
     * @param message the text kicked and refused players see; empty for the default text
     * @param motd the MOTD shown while it is on; empty to keep the server's MOTD
     * @param since when it was turned on; empty when off
     * @param until when it turns itself off; empty for never or when off
     */
    record Maintenance(boolean on, String message, String motd, Optional<Instant> since, Optional<Instant> until) {}

    /**
     * How to turn maintenance mode on.
     *
     * @param message the text kicked and refused players see; {@code null} or empty for the default text
     * @param motd the MOTD to show while it is on; {@code null} or empty to keep the server's MOTD
     * @param offAfter turn it off by itself after this long; empty for never
     */
    record MaintenanceOptions(String message, String motd, Optional<Duration> offAfter) {

        /** Default options: the default message, the server's MOTD, no automatic end. */
        public static final MaintenanceOptions DEFAULT = new MaintenanceOptions("", "", Optional.empty());
    }
}
