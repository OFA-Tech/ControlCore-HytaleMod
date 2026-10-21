package net.ofatech.controlcore.domain.ports.game;

import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.HudPart;

/** Shows and hides parts of the game's own HUD for one player. */
public interface HudDisplay {

    /**
     * @param hide parts to hide
     * @param show parts ControlCore hid before and that should come back; parts the game itself shows or hides
     *     (for example per game mode) are otherwise left alone
     */
    void update(PlayerId player, Set<HudPart> hide, Set<HudPart> show);
}
