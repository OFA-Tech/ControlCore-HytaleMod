package net.ofatech.controlcore.application.controlpanel;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlPanelApi;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.keymaps.KeymapsEditor;
import net.ofatech.controlcore.application.mods.ModsEditor;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.PermittedSettingsEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ControlPanelApi.class)
public final class ControlPanelService implements ControlPanelApi {

    private final ControlPanelSettings settings;
    private final Players players;
    private final ServerConfiguration server;
    private final ControlPanelView view;
    private final SettingsApi registry;
    private final ServerConfigEditor serverConfig;
    private final KeymapsEditor keymaps;
    private final SettingPermissionPolicy permissions;
    private final Texts texts;
    private final ModsEditor mods;

    public ControlPanelService(
            ControlPanelSettings settings,
            Players players,
            ServerConfiguration server,
            ControlPanelView view,
            SettingsApi registry,
            ServerConfigEditor serverConfig,
            KeymapsEditor keymaps,
            SettingPermissionPolicy permissions,
            Texts texts,
            ModsEditor mods) {
        this.settings = settings;
        this.players = players;
        this.server = server;
        this.view = view;
        this.registry = registry;
        this.serverConfig = serverConfig;
        this.keymaps = keymaps;
        this.permissions = permissions;
        this.texts = texts;
        this.mods = mods;
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

    @Override
    public Result<Void> open(PlayerId player, ControlPanelRequest request) {
        return open(player, request, Optional.empty());
    }

    /**
     * Opens the panel on one mod's settings: the admin panel for players who may open it, else their preferences.
     *
     * @param mod the mod's id ({@code Group:Name}), name or the first part of its setting ids
     */
    public Result<Void> openForMod(PlayerId player, String mod) {
        String wanted = mod == null ? "" : mod.strip();
        var entry = mods.viewedBy(Optional.of(player)).list().stream()
                .filter(candidate -> candidate.mod().id().equalsIgnoreCase(wanted)
                        || candidate.mod().name().equalsIgnoreCase(wanted)
                        || candidate
                                .namespace()
                                .filter(wanted::equalsIgnoreCase)
                                .isPresent())
                .findFirst();
        if (entry.isEmpty()) {
            return Result.failure(Error.notFound(
                    "Mods.Unknown",
                    Text.key("controlcore.errors.mods.unknown", "There is no mod {id}.")
                            .with("id", wanted)));
        }
        if (entry.get().namespace().isEmpty()) {
            return Result.failure(Error.notFound(
                    "Mods.NoSettings",
                    Text.key("controlcore.errors.mods.no_settings", "{name} has no settings in ControlCore.")
                            .with("name", entry.get().mod().name())));
        }
        ControlPanelRequest request = mayOpenAdminPanel(player) && settings.panelEnabled()
                ? ControlPanelRequest.ALL
                : ControlPanelRequest.PLAYER_PREFERENCES;
        return open(player, request, entry.get().namespace());
    }

    private Result<Void> open(PlayerId player, ControlPanelRequest request, Optional<String> focus) {
        if (request.isEmpty()) {
            return Result.failure(Error.validation(
                    "ControlPanel.NothingToShow",
                    Text.key(
                            "controlcore.errors.controlpanel.nothing_to_show",
                            "The request does not include any section.")));
        }
        if (request.includesAdminSections() && !settings.panelEnabled()) {
            return Result.failure(Error.unavailable(
                    "ControlPanel.Disabled",
                    Text.key(
                                    "controlcore.errors.controlpanel.disabled",
                                    "The control panel is disabled (setting {id}).")
                            .with("id", ControlPanelSettings.ENABLED_ID)));
        }
        if (players.find(player).isEmpty()) {
            return Result.failure(Error.notFound(
                    "ControlPanel.PlayerNotFound",
                    Text.key("controlcore.errors.controlpanel.player_offline", "That player is not online.")));
        }
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
                        texts.forPlayer(Optional.of(player)),
                        () -> {
                            List<Text> names = new java.util.ArrayList<>(serverConfig.pendingRestart());
                            names.addAll(mods.pendingRestart());
                            return names;
                        },
                        mods.viewedBy(Optional.of(player)),
                        focus));
    }
}
