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
 * {@code controlcore:toggle-hud} hides or shows the whole HUD.
 *
 * @since API 2.0.0
 */
public interface HudApi {

    /**
     * Registers a mod's HUD; its preference appears under the mod's name. Players already online get
     * {@code onShow} right away if it is visible for them. Registering the same id again replaces it.
     */
    Result<HudElement> register(HudElement element);

    /** @return the registered mod HUDs, sorted by id */
    List<HudElement> elements();

    /** @return whether {@code player} sees the mod HUD {@code id} now (false while their whole HUD is hidden) */
    boolean isVisible(PlayerId player, SettingId id);

    /** @return whether {@code player} sees that part of the game's HUD now */
    boolean isVisible(PlayerId player, HudPart part);

    /** @return whether {@code player} has hidden the whole HUD */
    boolean isHidden(PlayerId player);

    /** Hides or shows {@code player}'s whole HUD until they toggle it again or leave. */
    void setHidden(PlayerId player, boolean hidden);
}
