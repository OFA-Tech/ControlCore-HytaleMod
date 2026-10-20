package net.ofatech.controlcore.api;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Key actions: mods register what a key combination runs, ControlCore detects the keys, lets players and admins
 * rebind them and runs the command or code.
 *
 * <pre>{@code
 * api.keymaps().register(
 *         KeyAction.of("homes", "go-home")
 *                 .displayName("Go home")
 *                 .command("home")
 *                 .defaultKeys(Key.CTRL, Key.NUM_3));
 * }</pre>
 *
 * <p>The server only notices some keys during play (Q, E, R, F, O, 1-9, mouse buttons, a tap of Alt, and Ctrl,
 * Alt, Shift or Space held with them). Bindings to other keys are kept and shown as not available until Hytale
 * reports them.
 *
 * @since API 1.3.0
 */
public interface KeymapsApi {

    /**
     * Registers a key action. Registering the same id again (for example after a plugin reload) replaces it and
     * keeps players' bindings.
     *
     * @return the action as registered, or {@code Keys.NothingToRun} if it has neither a command nor onPress
     */
    Result<KeyAction> register(KeyAction action);

    /** Removes a key action; players' saved bindings are kept for when it comes back. */
    void unregister(SettingId id);

    /** @return the action registered under {@code id} */
    Optional<KeyAction> find(SettingId id);

    /** @return every registered action, sorted by id */
    List<KeyAction> actions();

    /** @return the bindings that apply to {@code player}: their own, else the server's, else the defaults */
    KeyBinds keysFor(SettingId id, PlayerId player);

    /** @return the server-wide bindings (an admin's choice, else the defaults) */
    KeyBinds serverKeys(SettingId id);

    /**
     * Sets a player's own bindings; {@code none} unbinds.
     *
     * @return the new bindings, or {@code Keys.UnknownAction} / {@code Keys.InvalidCombo}
     */
    Result<KeyBinds> bind(SettingId id, PlayerId player, String keys);

    /** Removes a player's own bindings so the server's apply again. */
    Result<KeyBinds> resetBind(SettingId id, PlayerId player);

    /** Sets the server-wide bindings, used by players who did not choose their own. */
    Result<KeyBinds> bindServer(SettingId id, String keys);

    /** Removes the server-wide bindings so the mod's defaults apply again. */
    Result<KeyBinds> resetServerBind(SettingId id);
}
