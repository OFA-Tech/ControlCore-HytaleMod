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

    /** Version of this API contract (semantic versioning). */
    String VERSION = "2.0.0";

    /**
     * @return the running API
     * @throws IllegalStateException if the mod is not loaded or not set up yet
     */
    static ControlCoreApi get() {
        return ControlCoreApiProvider.get();
    }

    /** @return the running API, or empty if the mod is not loaded or not set up yet */
    static Optional<ControlCoreApi> find() {
        return ControlCoreApiProvider.find();
    }

    default String apiVersion() {
        return VERSION;
    }

    ControlPanelApi controlPanel();

    ControlCoreEvents events();

    /**
     * The shared settings registry.
     *
     * @since 1.1.0
     */
    SettingsApi settings();

    /**
     * Key actions: commands and code players run with keys, rebindable per player.
     *
     * @since 1.3.0
     */
    KeymapsApi keymaps();

    /**
     * Players' HUD: the game's HUD parts and mods' HUDs players turn on and off.
     *
     * @since 2.0.0
     */
    HudApi huds();
}
