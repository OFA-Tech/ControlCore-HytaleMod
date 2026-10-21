package net.ofatech.controlcore.api;

import java.util.List;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.HudElement;
import net.ofatech.controlcore.domain.model.hud.HudPart;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Players' HUD: which parts of the game's HUD and which mods' HUDs they see. Each is a player preference (under
 * "HUD" in {@code /prefs}); admins can force the game's parts on or off for everyone. The key action
 * {@code controlcore:toggle-hud} hides or shows the whole HUD. Get it with {@link ControlCoreApi#huds()}.
 *
 * @since API 2.0.0
 */
public interface HudApi {

    /**
     * Registers a mod's HUD; its preference appears under the mod's name. Players already online get
     * {@code onShow} right away if it is visible for them. Registering the same id again replaces it.
     *
     * @param element the HUD to register
     * @return the HUD as registered, or the error of registering its preference (for example
     *     {@code Settings.Conflict} if {@link HudElement#preferenceId()} is already another kind of setting)
     */
    Result<HudElement> register(HudElement element);

    /**
     * Lists the registered mod HUDs.
     *
     * @return the registered mod HUDs, sorted by id
     */
    List<HudElement> elements();

    /**
     * Tells whether a player sees a mod's HUD now.
     *
     * @param player the player
     * @param id the HUD's id, {@code modId:key}
     * @return whether {@code player} sees the mod HUD {@code id} now (false while their whole HUD is hidden or if
     *     {@code id} is not registered)
     */
    boolean isVisible(PlayerId player, SettingId id);

    /**
     * Tells whether a player sees a part of the game's HUD now, after the admins' rule for that part and the
     * player's own preference.
     *
     * @param player the player
     * @param part the part of the game's HUD
     * @return whether {@code player} sees that part of the game's HUD now (false while their whole HUD is hidden)
     */
    boolean isVisible(PlayerId player, HudPart part);

    /**
     * Tells whether a player has hidden the whole HUD.
     *
     * @param player the player
     * @return whether {@code player} has hidden the whole HUD
     */
    boolean isHidden(PlayerId player);

    /**
     * Hides or shows {@code player}'s whole HUD until they toggle it again or leave. Mod HUDs get
     * {@code onHide} or {@code onShow} when this changes what the player sees.
     *
     * @param player the player
     * @param hidden {@code true} to hide the whole HUD, {@code false} to show it again
     */
    void setHidden(PlayerId player, boolean hidden);
}
