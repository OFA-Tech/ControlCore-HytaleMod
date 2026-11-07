package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.api.ActionsApi;
import net.ofatech.controlcore.api.AnnouncementsApi;
import net.ofatech.controlcore.api.BackupsApi;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.ControlCoreEvents;
import net.ofatech.controlcore.api.ControlPanelApi;
import net.ofatech.controlcore.api.DefaultsApi;
import net.ofatech.controlcore.api.HealthApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.api.HudApi;
import net.ofatech.controlcore.api.KeymapsApi;
import net.ofatech.controlcore.api.ModsApi;
import net.ofatech.controlcore.api.NotificationsApi;
import net.ofatech.controlcore.api.PermissionsApi;
import net.ofatech.controlcore.api.PlayersApi;
import net.ofatech.controlcore.api.ProfilesApi;
import net.ofatech.controlcore.api.RulesApi;
import net.ofatech.controlcore.api.SchedulerApi;
import net.ofatech.controlcore.api.ServerApi;
import net.ofatech.controlcore.api.SessionsApi;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.api.SnapshotsApi;

@AutoService(ControlCoreApi.class)
public final class ControlCoreApiService implements ControlCoreApi {

    private final ControlPanelApi controlPanel;
    private final ControlCoreEvents events;
    private final SettingsApi settings;
    private final KeymapsApi keymaps;
    private final HudApi huds;
    private final NotificationsApi notifications;
    private final SchedulerApi scheduler;
    private final SessionsApi sessions;
    private final ActionsApi actions;
    private final ServerApis server;

    public ControlCoreApiService(
            ControlPanelApi controlPanel,
            ControlCoreEvents events,
            SettingsApi settings,
            KeymapsApi keymaps,
            HudApi huds,
            NotificationsApi notifications,
            SchedulerApi scheduler,
            SessionsApi sessions,
            ActionsApi actions,
            ServerApis server) {
        this.controlPanel = controlPanel;
        this.events = events;
        this.settings = settings;
        this.keymaps = keymaps;
        this.huds = huds;
        this.notifications = notifications;
        this.scheduler = scheduler;
        this.sessions = sessions;
        this.actions = actions;
        this.server = server;
    }

    @Override
    public ControlPanelApi controlPanel() {
        return controlPanel;
    }

    @Override
    public ControlCoreEvents events() {
        return events;
    }

    @Override
    public SettingsApi settings() {
        return settings;
    }

    @Override
    public KeymapsApi keymaps() {
        return keymaps;
    }

    @Override
    public HudApi huds() {
        return huds;
    }

    @Override
    public NotificationsApi notifications() {
        return notifications;
    }

    @Override
    public SchedulerApi scheduler() {
        return scheduler;
    }

    @Override
    public SessionsApi sessions() {
        return sessions;
    }

    @Override
    public ActionsApi actions() {
        return actions;
    }

    @Override
    public PermissionsApi permissions() {
        return server.permissions();
    }

    @Override
    public ModsApi mods() {
        return server.mods();
    }

    @Override
    public HistoryApi history() {
        return server.history();
    }

    @Override
    public SnapshotsApi snapshots() {
        return server.snapshots();
    }

    @Override
    public ProfilesApi profiles() {
        return server.profiles();
    }

    @Override
    public HealthApi health() {
        return server.health();
    }

    @Override
    public RulesApi rules() {
        return server.rules();
    }

    @Override
    public PlayersApi players() {
        return server.players();
    }

    @Override
    public DefaultsApi defaults() {
        return server.defaults();
    }

    @Override
    public ServerApi server() {
        return server.server();
    }

    @Override
    public AnnouncementsApi announcements() {
        return server.announcements();
    }

    @Override
    public BackupsApi backups() {
        return server.backups();
    }
}
