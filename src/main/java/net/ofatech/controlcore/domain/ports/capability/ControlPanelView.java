package net.ofatech.controlcore.domain.ports.capability;

import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/** Shows the control panel UI to a player. Offered by a UI mod (HyUI). */
public interface ControlPanelView {

    Result<Void> open(PlayerId player, ControlPanelRequest request);
}
