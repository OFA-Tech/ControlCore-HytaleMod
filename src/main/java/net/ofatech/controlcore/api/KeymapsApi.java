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
 * rebind them and runs the command or code. Get it with {@link ControlCoreApi#keymaps()}.
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
 * <p>Key texts use the format of {@link KeyBinds#parse(String)}: for example {@code ctrl+f},
 * {@code alt+3 | mouse_middle} or {@code none}.
 *
 * @since API 1.3.0
 */
public interface KeymapsApi {

    /**
     * Registers a key action. Registering the same id again (for example after a plugin reload) replaces it and
     * keeps players' bindings.
     *
     * @param action the action to register; it needs a command or onPress code
     * @return the action as registered, {@code Keys.NothingToRun} if it is {@code null} or has neither a command
     *     nor onPress, or {@code Keys.ReservedId} if its mod id is {@code custom} (reserved for admins' actions)
     */
    Result<KeyAction> register(KeyAction action);

    /**
     * Removes a key action; players' saved bindings are kept for when it comes back. Does nothing for an unknown
     * id, {@code null}, or an admin-created {@code custom:} action.
     *
     * @param id the action's id, {@code modId:action}
     */
    void unregister(SettingId id);

    /**
     * Finds a registered key action.
     *
     * @param id the action's id, {@code modId:action}
     * @return the action registered under {@code id}, or empty if there is none or {@code id} is {@code null}
     */
    Optional<KeyAction> find(SettingId id);

    /**
     * Lists the registered key actions, including admin-created {@code custom:} ones.
     *
     * @return every registered action, sorted by id
     */
    List<KeyAction> actions();

    /**
     * Returns the bindings that run an action for a player.
     *
     * @param id the action's id
     * @param player the player
     * @return the bindings that apply to {@code player}: their own, else the server's, else the defaults
     *     ({@link KeyBinds#UNBOUND} for an unknown action)
     */
    KeyBinds keysFor(SettingId id, PlayerId player);

    /**
     * Returns the server-wide bindings of an action.
     *
     * @param id the action's id
     * @return the server-wide bindings (an admin's choice, else the defaults; {@link KeyBinds#UNBOUND} for an
     *     unknown action)
     */
    KeyBinds serverKeys(SettingId id);

    /**
     * Sets a player's own bindings; {@code none} unbinds. Saved right away; listeners of the change are told.
     *
     * @param id the action's id
     * @param player the player whose bindings change
     * @param keys the bindings as text, for example {@code ctrl+f} or {@code none}
     * @return the new bindings, or {@code Keys.UnknownAction} (also when the player may not use the action) /
     *     {@code Keys.InvalidCombo}
     */
    Result<KeyBinds> bind(SettingId id, PlayerId player, String keys);

    /**
     * Removes a player's own bindings so the server's apply again.
     *
     * @param id the action's id
     * @param player the player whose bindings are removed
     * @return the bindings that now apply to the player, or {@code Keys.UnknownAction} (also when the player may
     *     not use the action)
     */
    Result<KeyBinds> resetBind(SettingId id, PlayerId player);

    /**
     * Sets the server-wide bindings, used by players who did not choose their own.
     *
     * @param id the action's id
     * @param keys the bindings as text, for example {@code ctrl+f} or {@code none}
     * @return the new bindings, or {@code Keys.UnknownAction} / {@code Keys.InvalidCombo}
     */
    Result<KeyBinds> bindServer(SettingId id, String keys);

    /**
     * Removes the server-wide bindings so the mod's defaults apply again.
     *
     * @param id the action's id
     * @return the mod's default bindings, or {@code Keys.UnknownAction}
     */
    Result<KeyBinds> resetServerBind(SettingId id);
}
