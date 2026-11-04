package net.ofatech.controlcore.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The server as a whole: maintenance mode, as in the control panel's Server Configs -> Players view (Whitelisted
 * filter) and {@code /cc-maintenance}. Get it with {@link ControlCoreApi#server()}.
 *
 * <p>While maintenance mode is on, only admins and players with {@code ofatech.controlcore.maintenance.join} may
 * join; turning it on kicks every other online player with the maintenance message. A maintenance MOTD, if given,
 * replaces config.json {@code MOTD} while it is on and the old one comes back when it is turned off.
 *
 * <p>The API acts with full rights, like the console. {@link #actingFor(PlayerId)} gives the same API with a
 * player's rights: admins and players with {@code ofatech.controlcore.maintenance} may change it.
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
