package net.ofatech.controlcore.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.diagnostics.DiagnosticsView;
import net.ofatech.controlcore.domain.model.history.HistoryView;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBindEditor;
import net.ofatech.controlcore.domain.model.keys.KeyDetection;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.settings.PermissionsView;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.snapshot.ProfilesView;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.InputProbe;

public record ControlPanelContent(
        ServerSettings server,
        SettingsEditor serverConfig,
        SettingsEditor settings,
        SettingsEditor keymaps,
        CustomKeyBindEditor customKeyBinds,
        ControlPanelTab startTab,
        boolean manageKeys,
        Translator translator,
        Supplier<List<Text>> pendingRestart,
        ModsView mods,
        Optional<PermissionsView> permissions,
        Optional<HistoryView> history,
        Optional<SnapshotsView> snapshots,
        Optional<InputProbe> debug,
        Optional<String> focus,
        List<String> worlds,
        Optional<PlayersView> players,
        Optional<ProfilesView> profiles,
        Optional<DiagnosticsView> diagnostics,
        Optional<AutomationView> automation,
        KeyDetection keyDetection) {

    public ControlPanelContent {
        Guard.notNull(server, "server");
        Guard.notNull(serverConfig, "serverConfig");
        Guard.notNull(settings, "settings");
        Guard.notNull(keymaps, "keymaps");
        Guard.notNull(customKeyBinds, "customKeyBinds");
        Guard.notNull(startTab, "startTab");
        Guard.notNull(translator, "translator");
        Guard.notNull(pendingRestart, "pendingRestart");
        Guard.notNull(mods, "mods");
        Guard.notNull(permissions, "permissions");
        Guard.notNull(history, "history");
        Guard.notNull(snapshots, "snapshots");
        Guard.notNull(debug, "debug");
        Guard.notNull(focus, "focus");
        worlds = List.copyOf(worlds);
        Guard.notNull(players, "players");
        Guard.notNull(profiles, "profiles");
        Guard.notNull(diagnostics, "diagnostics");
        Guard.notNull(automation, "automation");
        Guard.notNull(keyDetection, "keyDetection");
    }
}
