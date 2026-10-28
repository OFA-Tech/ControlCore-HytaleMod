package net.ofatech.controlcore.application.controlpanel;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlPanelApi;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.automation.AutomationService;
import net.ofatech.controlcore.application.diagnostics.DiagnosticsService;
import net.ofatech.controlcore.application.history.HistoryEditor;
import net.ofatech.controlcore.application.keymaps.InputProbeCommand;
import net.ofatech.controlcore.application.keymaps.KeymapsEditor;
import net.ofatech.controlcore.application.mods.ModsEditor;
import net.ofatech.controlcore.application.players.PlayersService;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.PermissionsEditor;
import net.ofatech.controlcore.application.settings.PermittedSettingsEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.snapshot.ProfileService;
import net.ofatech.controlcore.application.snapshot.SnapshotService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.ports.game.Permissions;
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
    private final PlayersService playerAdmin;
    private final KeymapsEditor keymaps;
    private final SettingPermissionPolicy permissions;
    private final Texts texts;
    private final ModsEditor mods;
    private final PermissionsEditor permissionRules;
    private final InputProbe probe;
    private final Permissions nodes;
    private final HistoryEditor history;
    private final SnapshotService snapshots;
    private final ProfileService profiles;
    private final DiagnosticsService diagnostics;
    private final AutomationService automation;

    public ControlPanelService(
            ControlPanelSettings settings,
            Players players,
            ServerConfiguration server,
            ControlPanelView view,
            SettingsApi registry,
            ServerConfigEditor serverConfig,
            PlayersService playerAdmin,
            KeymapsEditor keymaps,
            SettingPermissionPolicy permissions,
            Texts texts,
            ModsEditor mods,
            PermissionsEditor permissionRules,
            InputProbe probe,
            Permissions nodes,
            HistoryEditor history,
            SnapshotService snapshots,
            ProfileService profiles,
            DiagnosticsService diagnostics,
            AutomationService automation) {
        this.settings = settings;
        this.players = players;
        this.server = server;
        this.view = view;
        this.registry = registry;
        this.serverConfig = serverConfig;
        this.playerAdmin = playerAdmin;
        this.keymaps = keymaps;
        this.permissions = permissions;
        this.texts = texts;
        this.mods = mods;
        this.permissionRules = permissionRules;
        this.probe = probe;
        this.nodes = nodes;
        this.history = history;
        this.snapshots = snapshots;
        this.profiles = profiles;
        this.diagnostics = diagnostics;
        this.automation = automation;
    }

    public boolean mayOpenAdminPanel(PlayerId player) {
        return permissions.isAdmin(player)
                || permitted(serverConfig, player).seesAny(SettingScope.SERVER)
                || permitted(registry, player).seesAny(SettingScope.SERVER)
                || playerAdmin.maySee(Optional.of(player));
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

    public Result<Void> openBestFor(PlayerId player) {
        ControlPanelRequest request = mayOpenAdminPanel(player) && settings.panelEnabled()
                ? ControlPanelRequest.ALL
                : ControlPanelRequest.PLAYER_PREFERENCES;
        return open(player, request);
    }

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
                            List<Text> names = new ArrayList<>(serverConfig.pendingRestart());
                            names.addAll(mods.pendingRestart());
                            return names;
                        },
                        mods.viewedBy(Optional.of(player)),
                        permissions.isAdmin(player)
                                ? Optional.of(permissionRules.viewedBy(texts.forPlayer(Optional.of(player))))
                                : Optional.empty(),
                        history.maySee(Optional.of(player))
                                ? Optional.of(history.viewedBy(player, nameOf(player)))
                                : Optional.empty(),
                        snapshots.mayUse(Optional.of(player))
                                ? Optional.of(snapshots.viewedBy(player, nameOf(player)))
                                : Optional.empty(),
                        nodes.hasOwn(player, InputProbeCommand.PERMISSION) ? Optional.of(probe) : Optional.empty(),
                        focus,
                        serverConfig.worldNames(),
                        playerAdmin.maySee(Optional.of(player))
                                ? Optional.of(playerAdmin.viewedBy(player))
                                : Optional.empty(),
                        snapshots.mayUse(Optional.of(player))
                                ? Optional.of(profiles.viewedBy(player, nameOf(player)))
                                : Optional.empty(),
                        diagnostics.maySee(Optional.of(player))
                                ? Optional.of(diagnostics.viewedBy(player))
                                : Optional.empty(),
                        automation.mayManage(Optional.of(player))
                                ? players.find(player).map(automation::viewedBy)
                                : Optional.empty()));
    }

    private String nameOf(PlayerId player) {
        return players.find(player).map(info -> info.name()).orElse(player.toString());
    }
}
