package net.ofatech.controlcore.domain.ports.capability;

import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Shows the control panel UI to a player. The Hytale platform always provides one (native custom UI);
 * a UI mod such as HyUI may offer another as a capability, which is preferred when present.
 */
public interface ControlPanelView {

    Result<Void> open(PlayerId player, ControlPanelRequest request, ServerSettings server);
}
