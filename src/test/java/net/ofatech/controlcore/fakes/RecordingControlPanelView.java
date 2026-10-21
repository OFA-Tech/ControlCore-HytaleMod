package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.result.Result;

public final class RecordingControlPanelView implements ControlPanelView {

    public record Opened(PlayerId player, ServerSettings server) {}

    public final List<Opened> opened = new CopyOnWriteArrayList<>();
    public volatile ControlPanelContent lastContent;
    public volatile ControlPanelRequest lastRequest;

    @Override
    public Result<Void> open(PlayerId player, ControlPanelRequest request, ControlPanelContent content) {
        opened.add(new Opened(player, content.server()));
        lastContent = content;
        lastRequest = request;
        return Result.success();
    }

    public List<PlayerId> players() {
        return opened.stream().map(Opened::player).toList();
    }
}
