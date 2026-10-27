package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.Overlay;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;

public interface OverlayDisplay {

    void render(PlayerId player, Overlay overlay);

    void hytaleToast(PlayerId player, String title, String text, ToastLevel level);
}
