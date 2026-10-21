package net.ofatech.controlcore.domain.ports.game;

import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.HudPart;

public interface HudDisplay {

    void update(PlayerId player, Set<HudPart> hide, Set<HudPart> show);
}
