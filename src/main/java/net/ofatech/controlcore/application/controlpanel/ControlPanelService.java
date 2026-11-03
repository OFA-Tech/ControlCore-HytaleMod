package net.ofatech.controlcore.application.controlpanel;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlPanelApi;
import net.ofatech.controlcore.application.automation.AutomationService;
import net.ofatech.controlcore.application.diagnostics.DiagnosticsService;
import net.ofatech.controlcore.application.history.HistoryEditor;
import net.ofatech.controlcore.application.hud.HudPrefs;
import net.ofatech.controlcore.application.keymaps.InputProbeCommand;
import net.ofatech.controlcore.application.keymaps.KeymapsEditor;
import net.ofatech.controlcore.application.mods.ModsEditor;
import net.ofatech.controlcore.application.players.PlayersService;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.DefaultsService;
import net.ofatech.controlcore.application.settings.PermissionsEditor;
import net.ofatech.controlcore.application.settings.PermittedSettingsEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.application.snapshot.ProfileService;
import net.ofatech.controlcore.application.snapshot.SnapshotService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PanelRights;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.ports.game.KeyDetections;
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
    private final SettingsRegistry registry;
    private final ServerConfigEditor serverConfig;
    private final PlayersService playerAdmin;
    private final KeymapsEditor keymaps;
    private final SettingPermissionPolicy permissions;
    private final Texts texts;
    private final ModsEditor mods;
    private final PermissionsEditor permissionRules;
    private final InputProbe probe;
    private final KeyDetections keyDetection;
    private final Permissions nodes;
    private final HistoryEditor history;
    private final SnapshotService snapshots;
    private final ProfileService profiles;
    private final DiagnosticsService diagnostics;
    private final AutomationService automation;
    private final DefaultsService defaults;
    private final HudPrefs hudPrefs;

    public ControlPanelService(
            ControlPanelSettings settings,
            Players players,
            ServerConfiguration server,
            ControlPanelView view,
            SettingsRegistry registry,
            ServerConfigEditor serverConfig,
            PlayersService playerAdmin,
            KeymapsEditor keymaps,
            SettingPermissionPolicy permissions,
            Texts texts,
            ModsEditor mods,
            PermissionsEditor permissionRules,
            InputProbe probe,
            KeyDetections keyDetection,
            Permissions nodes,
            HistoryEditor history,
            SnapshotService snapshots,
            ProfileService profiles,
            DiagnosticsService diagnostics,
            AutomationService automation,
            DefaultsService defaults,
            HudPrefs hudPrefs) {
        this.hudPrefs = hudPrefs;
        this.settings = settings;
        this.defaults = defaults;
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
        this.keyDetection = keyDetection;
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
        return open(player, bestRequest(player));
    }

    private ControlPanelRequest bestRequest(PlayerId player) {
        return mayOpenAdminPanel(player) && settings.panelEnabled()
                ? ControlPanelRequest.ALL
                : ControlPanelRequest.PLAYER_PREFERENCES;
    }

    @Override
    public Result<Void> openForMod(PlayerId player, String mod) {
        return mods.viewedBy(Optional.of(player))
                .find(mod)
                .flatMap(entry -> entry.namespace().isEmpty()
                        ? Result.failure(Error.notFound(
                                "Mods.NoSettings",
                                Text.key(
                                                "controlcore.errors.mods.no_settings",
                                                "{name} has no settings in ControlCore.")
                                        .with("name", entry.mod().name())))
                        : open(player, bestRequest(player), entry.namespace()));
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
        KeymapsEditor keys = keymaps.viewedBy(player, permissions);
        Result<Void> opened = view.open(
                player,
                request,
                new ControlPanelContent(
                        server.read(),
                        permitted(serverConfig, player),
                        hudPrefs.around(permitted(registry, player)),
                        keys,
                        keys,
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
                                ? Optional.of(permissionRules.viewedBy(
                                        texts.forPlayer(Optional.of(player)), () -> permissions.isAdmin(player)))
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
                                : Optional.empty(),
                        keyDetection.viewedBy(player),
                        () -> rights(player),
                        () -> permissions.isAdmin(player),
                        defaults.viewedBy(player, nameOf(player))));
        opened.onSuccess(nothing -> keyDetection.cancel(player));
        return opened;
    }

    private PanelRights rights(PlayerId player) {
        Optional<PlayerId> who = Optional.of(player);
        return new PanelRights(
                settings.panelEnabled(),
                permissions.isAdmin(player),
                mayOpenAdminPanel(player),
                history.maySee(who),
                history.mayRollBack(who),
                snapshots.mayUse(who),
                nodes.hasOwn(player, InputProbeCommand.PERMISSION),
                playerAdmin.maySee(who),
                diagnostics.maySee(who),
                automation.mayManage(who));
    }

    private String nameOf(PlayerId player) {
        return players.find(player).map(info -> info.name()).orElse(player.toString());
    }
}
