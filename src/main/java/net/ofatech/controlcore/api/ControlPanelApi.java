package net.ofatech.controlcore.api;

import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/** The ControlCore control panel: admin settings, player preferences and key bindings. */
public interface ControlPanelApi {

    /**
     * Tells whether the admin panel can be opened.
     *
     * @return whether the setting {@code controlcore:control-panel-enabled} is on
     */
    boolean isAvailable();

    /**
     * Opens the panel for an online player, showing only the sections in {@code request}. Does not check
     * permissions; callers decide who may open it. {@link ControlPanelRequest#PLAYER_PREFERENCES} is safe for
     * any player and works even when the panel is disabled.
     *
     * @param player the online player who sees the panel
     * @param request the sections to show, for example {@link ControlPanelRequest#ALL}
     * @return success, or an error such as {@code ControlPanel.Disabled}, {@code ControlPanel.PlayerNotFound},
     *     or {@code ControlPanel.NothingToShow}
     */
    Result<Void> open(PlayerId player, ControlPanelRequest request);

    /**
     * Opens the panel for an online player on a mod's settings, like {@code /cc-config <mod>}. The player sees the
     * admin panel if they may open it (and it is enabled), else only their preferences.
     *
     * @param player the online player who sees the panel
     * @param modId the mod's id ({@code Group:Name}), name or settings namespace; case does not matter
     * @return success, or an error such as {@code Mods.Unknown}, {@code Mods.NoSettings} or
     *     {@code ControlPanel.PlayerNotFound}
     * @since API 2.4.0
     */
    Result<Void> openForMod(PlayerId player, String modId);
}
