package net.ofatech.controlcore.application.runtime;

import net.ofatech.controlcore.api.AnnouncementsApi;
import net.ofatech.controlcore.api.BackupsApi;
import net.ofatech.controlcore.api.DefaultsApi;
import net.ofatech.controlcore.api.HealthApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.api.ModsApi;
import net.ofatech.controlcore.api.PermissionsApi;
import net.ofatech.controlcore.api.PlayersApi;
import net.ofatech.controlcore.api.ProfilesApi;
import net.ofatech.controlcore.api.RulesApi;
import net.ofatech.controlcore.api.ServerApi;
import net.ofatech.controlcore.api.SnapshotsApi;

public record ServerApis(
        PermissionsApi permissions,
        ModsApi mods,
        HistoryApi history,
        SnapshotsApi snapshots,
        ProfilesApi profiles,
        HealthApi health,
        RulesApi rules,
        PlayersApi players,
        DefaultsApi defaults,
        ServerApi server,
        AnnouncementsApi announcements,
        BackupsApi backups) {}
