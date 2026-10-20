package net.ofatech.controlcore.application.controlpanel;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlPanelApi;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.keymaps.KeymapsEditor;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.PermittedSettingsEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
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
    private final SettingPermissionPolicy permissions;
    private final Texts texts;

    public ControlPanelService(
            ControlPanelSettings settings,
            Players players,
            ServerConfiguration server,
            ControlPanelView platformView,
            Capabilities capabilities,
            SettingsApi registry,
            ServerConfigEditor serverConfig,
            KeymapsEditor keymaps,
            SettingPermissionPolicy permissions,
            Texts texts) {
        this.settings = settings;
        this.players = players;
        this.server = server;
        this.platformView = platformView;
        this.capabilities = capabilities;
        this.registry = registry;
        this.serverConfig = serverConfig;
        this.keymaps = keymaps;
        this.permissions = permissions;
        this.texts = texts;
    }

    /**
     * @return whether the player may open the admin panel: ControlCore admins, and players an admin or mod let
     *     see at least one server setting or server config value
     */
    public boolean mayOpenAdminPanel(PlayerId player) {
        return permissions.isAdmin(player)
                || permitted(serverConfig, player).seesAny(SettingScope.SERVER)
                || permitted(registry, player).seesAny(SettingScope.SERVER);
    }

    private PermittedSettingsEditor permitted(SettingsEditor editor, PlayerId player) {
        return new PermittedSettingsEditor(editor, permissions, Optional.of(player));
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
                    Error.validation("ControlPanel.NothingToShow", "The request does not include any section.")
                            .localized("controlcore.errors.controlpanel.nothingtoshow"));
        }
        if (request.includesAdminSections() && !settings.panelEnabled()) {
            return Result.failure(Error.unavailable(
                            "ControlPanel.Disabled",
                            "The control panel is disabled (setting " + ControlPanelSettings.ENABLED_ID + ").")
                    .localized(
                            "controlcore.errors.controlpanel.disabled", "ENABLED_ID", ControlPanelSettings.ENABLED_ID));
        }
        if (players.find(player).isEmpty()) {
            return Result.failure(Error.notFound("ControlPanel.PlayerNotFound", "That player is not online.")
                    .localized("controlcore.errors.controlpanel.playernotfound"));
        }
        // The HyUI page is the full admin layout, so narrower requests always use the built-in panel.
        ControlPanelView view = capabilities
                .find(ControlPanelView.class)
                .filter(uiMod -> request.equals(ControlPanelRequest.ALL))
                .orElse(platformView);
        return view.open(
                player,
                request,
                new ControlPanelContent(
                        server.read(),
                        permitted(serverConfig, player),
                        permitted(registry, player),
                        keymaps.viewedBy(player),
                        keymaps.viewedBy(player),
                        settings.startTab(player),
                        permissions.isAdmin(player),
                        texts.forPlayer(Optional.of(player))));
    }
}
