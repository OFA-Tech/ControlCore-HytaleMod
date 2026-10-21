package net.ofatech.controlcore.domain.ports.capability;

import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/** Shows the control panel UI to a player; the Hytale platform provides it (native custom UI). */
public interface ControlPanelView {

    Result<Void> open(PlayerId player, ControlPanelRequest request, ControlPanelContent content);
}
