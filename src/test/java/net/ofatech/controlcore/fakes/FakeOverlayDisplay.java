package net.ofatech.controlcore.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.Overlay;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.ports.game.OverlayDisplay;

public final class FakeOverlayDisplay implements OverlayDisplay {

    public final Map<PlayerId, Overlay> shown = new ConcurrentHashMap<>();
    public final List<String> hytaleToasts = new ArrayList<>();
    public final List<String> everToasted = new ArrayList<>();

    @Override
    public void render(PlayerId player, Overlay overlay) {
        shown.put(player, overlay);
        overlay.toasts().stream()
                .map(line -> line.level().id() + ":" + line.text())
                .filter(text -> !everToasted.contains(text))
                .forEach(everToasted::add);
    }

    @Override
    public void hytaleToast(PlayerId player, String title, String text, ToastLevel level) {
        hytaleToasts.add(level.id() + ":" + text);
    }
}
