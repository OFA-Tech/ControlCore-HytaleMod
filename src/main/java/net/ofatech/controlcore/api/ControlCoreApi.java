package net.ofatech.controlcore.api;

import java.util.Optional;

/**
 * Entry point for other mods. Available once ControlCore finished {@code setup()}; declare
 * {@code OFATech:ControlCore} in your manifest {@code Dependencies} (or {@code OptionalDependencies}) so it
 * loads first.
 *
 * <pre>{@code
 * ControlCoreApi.find().ifPresent(api -> api.controlPanel().open(playerId, ControlPanelRequest.ALL));
 * }</pre>
 */
public interface ControlCoreApi {

    /** Version of this API contract (semantic versioning), for example {@code "2.0.0"}. */
    String VERSION = "2.2.0";

    /**
     * Returns the running API; use it when ControlCore is a required dependency.
     *
     * @return the running API
     * @throws IllegalStateException if the mod is not loaded or not set up yet
     */
    static ControlCoreApi get() {
        return ControlCoreApiProvider.get();
    }

    /**
     * Returns the running API if there is one; use it when ControlCore is an optional dependency.
     *
     * @return the running API, or empty if the mod is not loaded or not set up yet
     */
    static Optional<ControlCoreApi> find() {
        return ControlCoreApiProvider.find();
    }

    /**
     * Returns the API version of the running ControlCore, which may be newer than the one you compiled against.
     *
     * @return the running API's version, for example {@code "2.0.0"}
     */
    default String apiVersion() {
        return VERSION;
    }

    /**
     * The control panel: open it for a player with chosen sections.
     *
     * @return the control panel API
     */
    ControlPanelApi controlPanel();

    /**
     * ControlCore's events, for example {@link net.ofatech.controlcore.domain.event.SettingChanged}.
     *
     * @return the events API
     */
    ControlCoreEvents events();

    /**
     * The shared settings registry.
     *
     * @return the settings API
     * @since API 1.1.0
     */
    SettingsApi settings();

    /**
     * Key actions: commands and code players run with keys, rebindable per player.
     *
     * @return the key actions API
     * @since API 1.3.0
     */
    KeymapsApi keymaps();

    /**
     * Players' HUD: the game's HUD parts and mods' HUDs players turn on and off.
     *
     * @return the HUD API
     * @since API 2.0.0
     */
    HudApi huds();

    /**
     * Chat messages and on-screen titles.
     *
     * @return the notifications API
     * @since API 2.1.0
     */
    NotificationsApi notifications();

    /**
     * Running code later, repeatedly or daily.
     *
     * @return the scheduler API
     * @since API 2.1.0
     */
    SchedulerApi scheduler();

    /**
     * Players' first join, last seen and playtime.
     *
     * @return the sessions API
     * @since API 2.1.0
     */
    SessionsApi sessions();

    /**
     * Actions and triggers for automation rules.
     *
     * @return the actions API
     * @since API 2.1.0
     */
    ActionsApi actions();
}
