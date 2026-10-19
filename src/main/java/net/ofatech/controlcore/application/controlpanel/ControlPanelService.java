package net.ofatech.controlcore.application.controlpanel;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.api.ControlPanelApi;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.platform.Capabilities;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ControlPanelApi.class)
public final class ControlPanelService implements ControlPanelApi {

    private final SettingsProvider settings;
    private final Players players;
    private final Capabilities capabilities;

    public ControlPanelService(SettingsProvider settings, Players players, Capabilities capabilities) {
        this.settings = settings;
        this.players = players;
        this.capabilities = capabilities;
    }

    @Override
    public boolean isAvailable() {
        return settings.current().controlPanelEnabled()
                && capabilities.find(ControlPanelView.class).isPresent();
    }

    @Override
    public Result<Void> open(PlayerId player, ControlPanelRequest request) {
        if (!settings.current().controlPanelEnabled()) {
            return Result.failure(
                    Error.unavailable("ControlPanel.Disabled", "The control panel is disabled in Settings.json."));
        }
        if (players.find(player).isEmpty()) {
            return Result.failure(Error.notFound("ControlPanel.PlayerNotFound", "That player is not online."));
        }
        return capabilities
                .find(ControlPanelView.class)
                .map(view -> view.open(player, request))
                .orElseGet(() -> Result.failure(Error.unavailable(
                        "ControlPanel.UiUnavailable",
                        "The control panel needs the HyUI mod, which is not installed or failed to load.")));
    }
}
