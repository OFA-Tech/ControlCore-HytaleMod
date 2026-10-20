package net.ofatech.controlcore.application.controlpanel;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.api.ControlPanelApi;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.keymaps.KeymapsEditor;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
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
    private final SettingsApi registry;
    private final ServerConfigEditor serverConfig;
    private final KeymapsEditor keymaps;

    public ControlPanelService(
            ControlPanelSettings settings,
            Players players,
            ServerConfiguration server,
            ControlPanelView platformView,
            Capabilities capabilities,
            SettingsApi registry,
            ServerConfigEditor serverConfig,
            KeymapsEditor keymaps) {
        this.settings = settings;
        this.players = players;
        this.server = server;
        this.platformView = platformView;
        this.capabilities = capabilities;
        this.registry = registry;
        this.serverConfig = serverConfig;
        this.keymaps = keymaps;
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
        if (request.isEmpty()) {
            return Result.failure(
                    Error.validation("ControlPanel.NothingToShow", "The request does not include any section."));
        }
        if (request.includesAdminSections() && !settings.panelEnabled()) {
            return Result.failure(Error.unavailable(
                    "ControlPanel.Disabled",
                    "The control panel is disabled (setting " + ControlPanelSettings.ENABLED_ID + ")."));
        }
        if (players.find(player).isEmpty()) {
            return Result.failure(Error.notFound("ControlPanel.PlayerNotFound", "That player is not online."));
        }
        // The HyUI page is the full admin layout, so narrower requests always use the built-in panel.
        ControlPanelView view = capabilities
                .find(ControlPanelView.class)
                .filter(uiMod -> request.equals(ControlPanelRequest.ALL))
                .orElse(platformView);
        return view.open(
                player,
                request,
                new ControlPanelContent(server.read(), serverConfig, registry, keymaps, settings.startTab(player)));
    }
}
