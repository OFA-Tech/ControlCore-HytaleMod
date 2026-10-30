package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;
import net.ofatech.controlcore.infrastructure.platforms.hytale.pages.HytalePages;

public final class HytaleControlPanelView implements ControlPanelView {

    private final Log log;

    public HytaleControlPanelView(Log log) {
        this.log = log;
    }

    @Override
    public Result<Void> open(PlayerId player, ControlPanelRequest request, ControlPanelContent content) {
        boolean opening = HytalePages.inWorld(player.value())
                .map(playerRef -> HytalePages.open(
                        playerRef,
                        ref -> new ControlPanelPage(ref, request, content, log),
                        log,
                        "the control panel",
                        () -> {}))
                .orElse(false);
        if (!opening) {
            return Result.failure(Error.notFound(
                    "ControlPanel.PlayerNotFound",
                    Text.key("controlcore.errors.controlpanel.player_not_in_world", "That player is not in a world.")));
        }
        return Result.success();
    }
}
