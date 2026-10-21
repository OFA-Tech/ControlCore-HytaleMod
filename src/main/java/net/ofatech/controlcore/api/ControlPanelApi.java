package net.ofatech.controlcore.api;

import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/** The ControlCore admin control panel. */
public interface ControlPanelApi {

    /** Whether the admin panel can be opened: the setting {@code controlcore:control-panel-enabled} is on. */
    boolean isAvailable();

    /**
     * Opens the panel for an online player, showing only the sections in {@code request}. Does not check
     * permissions; callers decide who may open it. {@link ControlPanelRequest#PLAYER_PREFERENCES} is safe for
     * any player and works even when the panel is disabled.
     *
     * @return success, or an error such as {@code ControlPanel.Disabled}, {@code ControlPanel.PlayerNotFound},
     *     or {@code ControlPanel.NothingToShow}
     */
    Result<Void> open(PlayerId player, ControlPanelRequest request);
}
