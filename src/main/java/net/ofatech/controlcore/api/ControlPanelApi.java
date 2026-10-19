package net.ofatech.controlcore.api;

import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/** The ControlCore admin control panel. */
public interface ControlPanelApi {

    /** Whether the panel can be opened: enabled in the settings and a UI mod (HyUI) is available. */
    boolean isAvailable();

    /**
     * Opens the panel for an online player. Does not check permissions; callers decide who may open it.
     *
     * @return success, or an error such as {@code ControlPanel.Disabled}, {@code ControlPanel.PlayerNotFound}
     *     or {@code ControlPanel.UiUnavailable}
     */
    Result<Void> open(PlayerId player, ControlPanelRequest request);
}
