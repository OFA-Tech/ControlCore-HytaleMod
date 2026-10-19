package net.ofatech.controlcore.application.controlpanel;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.api.ControlPanelApi;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.ports.platform.Capabilities;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ControlPanelApi.class)
public final class ControlPanelService implements ControlPanelApi {

    private final ControlPanelSettings settings;
    private final Players players;
    private final ServerConfiguration server;
    private final ControlPanelView platformView;
    private final Capabilities capabilities;

    public ControlPanelService(
            ControlPanelSettings settings,
            Players players,
            ServerConfiguration server,
            ControlPanelView platformView,
            Capabilities capabilities) {
        this.settings = settings;
        this.players = players;
        this.server = server;
        this.platformView = platformView;
        this.capabilities = capabilities;
    }

    @Override
    public boolean isAvailable() {
        return settings.panelEnabled();
    }

    /** Whether a UI mod (HyUI) renders the panel instead of the built-in Hytale UI. */
    public boolean usesUiMod() {
        return capabilities.find(ControlPanelView.class).isPresent();
    }

    @Override
    public Result<Void> open(PlayerId player, ControlPanelRequest request) {
        if (!settings.panelEnabled()) {
            return Result.failure(Error.unavailable(
                    "ControlPanel.Disabled",
                    "The control panel is disabled (setting " + ControlPanelSettings.ENABLED_ID + ")."));
        }
        if (players.find(player).isEmpty()) {
            return Result.failure(Error.notFound("ControlPanel.PlayerNotFound", "That player is not online."));
        }
        ControlPanelView view = capabilities.find(ControlPanelView.class).orElse(platformView);
        return view.open(player, request, server.read());
    }
}
