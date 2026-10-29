package net.ofatech.controlcore.application.snapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.snapshot.ProfilesView;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot.Part;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.config.ActiveProfileStore;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class ProfileService {

    public static final String BUILT_IN = "@";

    enum Preset {
        VANILLA("vanilla"),
        PVP("pvp"),
        CREATIVE("creative"),
        EVENT("event");

        final String key;

        Preset(String key) {
            this.key = key;
        }

        String id() {
            return BUILT_IN + key;
        }

        static Optional<Preset> of(String id) {
            for (Preset preset : values()) {
                if (preset.id().equals(id)) {
                    return Optional.of(preset);
                }
            }
            return Optional.empty();
        }

        Map<String, String> worldValues() {
            Map<String, String> values = new LinkedHashMap<>();
            switch (this) {
                case PVP -> {
                    values.put("is-pvp-enabled", "true");
                    values.put("is-fall-damage-enabled", "true");
                    values.put("is-compass-updating", "true");
                }
                case CREATIVE -> {
                    values.put("game-mode", "Creative");
                    values.put("is-pvp-enabled", "false");
                    values.put("is-fall-damage-enabled", "false");
                    values.put("is-game-time-paused", "true");
                    values.put("is-spawning-npc", "false");
                }
                case EVENT -> {
                    values.put("is-pvp-enabled", "false");
                    values.put("is-fall-damage-enabled", "false");
                    values.put("is-spawning-npc", "false");
                    values.put("is-game-time-paused", "true");
                }
                case VANILLA -> {}
            }
            return values;
        }

        Map<String, String> settingValues() {
            return this == EVENT ? Map.of("controlcore:player-speed", "120") : Map.of();
        }
    }

    private final SnapshotService snapshots;
    private final ServerConfigEditor serverConfig;
    private final ActiveProfileStore active;
    private final Texts texts;
    private final Log log;

    public ProfileService(
            SnapshotService snapshots,
            ServerConfigEditor serverConfig,
            ActiveProfileStore active,
            Texts texts,
            Log log) {
        this.snapshots = snapshots;
        this.serverConfig = serverConfig;
        this.active = active;
        this.texts = texts;
        this.log = log;
    }

    public ProfilesView viewedBy(PlayerId viewer, String name) {
        Translator tr = texts.forPlayer(Optional.of(viewer));
        Optional<PlayerId> who = Optional.of(viewer);
        Actor actor = Actor.player(viewer, name);
        return new ProfilesView() {
            @Override
            public List<Entry> list() {
                return ProfileService.this.list(tr);
            }

            @Override
            public String status() {
                return ProfileService.this.status(who, tr);
            }

            @Override
            public Result<SnapshotsView.Preview> preview(String id) {
                return ProfileService.this.preview(id, who, tr);
            }

            @Override
            public Result<String> switchTo(String id) {
                return ProfileService.this.switchTo(id, who, actor, tr);
            }

            @Override
            public Result<String> unmark(String id) {
                return snapshots.setProfile(id, false, who).map(saved -> {
                    if (active.get().filter(saved.name()::equals).isPresent()) {
                        active.set(Optional.empty());
                    }
                    return tr.t("controlcore.profiles.unmarked", "name", saved.name());
                });
            }
        };
    }

    public List<ProfilesView.Entry> list(Translator tr) {
        Optional<String> current = active.get();
        List<ProfilesView.Entry> entries = new ArrayList<>();
        for (Preset preset : Preset.values()) {
            entries.add(new ProfilesView.Entry(
                    preset.id(),
                    name(preset, tr),
                    tr.t("controlcore.profiles.preset." + preset.key + ".desc"),
                    true,
                    current.filter(preset.id()::equals).isPresent()));
        }
        for (Snapshot snapshot : snapshots.all()) {
            if (snapshot.profile()) {
                entries.add(new ProfilesView.Entry(
                        snapshot.name(),
                        snapshot.name(),
                        tr.t(
                                "controlcore.panel.snapshots.details",
                                "when",
                                snapshots.when(snapshot),
                                "who",
                                snapshot.createdBy().isEmpty() ? "-" : snapshot.createdBy(),
                                "parts",
                                snapshots.partsText(snapshot, tr)),
                        false,
                        current.filter(snapshot.name()::equals).isPresent()));
            }
        }
        return entries;
    }

    public String status(Optional<PlayerId> who, Translator tr) {
        Optional<String> current = active.get();
        if (current.isEmpty() || !snapshots.mayUse(who)) {
            return tr.t("controlcore.profiles.status.none");
        }
        Result<SnapshotService.Plan> plan = plan(current.get(), Optional.empty(), tr);
        if (plan.isFailure()) {
            String code = plan.error().code();
            if (code.equals("Snapshots.Unknown") || code.equals("Profiles.NotAProfile")) {
                active.set(Optional.empty());
            }
            return tr.t("controlcore.profiles.status.none");
        }
        plan = Result.success(snapshots.forViewer(plan.value(), who));
        int changed = plan.value().changes().size();
        String shown = displayName(current.get(), tr);
        int adminOnly = plan.value().adminOnly();
        if (changed == 0 && adminOnly > 0) {
            return tr.t("controlcore.profiles.status.matches_yours", "name", shown, "count", adminOnly);
        }
        String text = changed == 0
                ? tr.t("controlcore.profiles.status.matches", "name", shown)
                : tr.t("controlcore.profiles.status.changed", "name", shown, "count", changed);
        return adminOnly == 0 ? text : text + " " + tr.t("controlcore.profiles.status.admin_only", "count", adminOnly);
    }

    public Result<SnapshotsView.Preview> preview(String id, Optional<PlayerId> who, Translator tr) {
        Result<Boolean> allowed = allowed(who);
        if (allowed.isFailure()) {
            return Result.failure(allowed.error());
        }
        return plan(id, who, tr).map(plan -> {
            SnapshotsView.Preview preview = snapshots.preview(plan, tr);
            return new SnapshotsView.Preview(
                    displayName(id, tr), preview.lines(), preview.changes(), preview.skipped(), preview.adminOnly());
        });
    }

    public Result<String> switchTo(String id, Optional<PlayerId> who, Actor actor, Translator tr) {
        Result<Boolean> allowed = allowed(who);
        if (allowed.isFailure()) {
            return Result.failure(allowed.error());
        }
        Result<SnapshotService.Plan> planned = plan(id, who, tr);
        if (planned.isFailure()) {
            return Result.failure(planned.error());
        }
        String base = Preset.of(id).map(preset -> preset.key).orElse(id);
        Result<String> applied = snapshots.apply(planned.value(), Optional.of(base), who, actor, tr);
        if (applied.isSuccess()) {
            active.set(Optional.of(id));
            log.info(actor.name() + " switched to profile " + displayName(id, texts.forServer()));
            return Result.success(
                    tr.t("controlcore.profiles.switched", "name", displayName(id, tr)) + " " + applied.value());
        }
        return applied;
    }

    public Optional<String> activeId() {
        return active.get();
    }

    public List<String> ids() {
        return list(texts.forServer()).stream().map(ProfilesView.Entry::id).toList();
    }

    Result<SnapshotService.Plan> plan(String id, Optional<PlayerId> who, Translator tr) {
        Optional<Preset> preset = Preset.of(id == null ? "" : id.strip().toLowerCase(java.util.Locale.ROOT));
        if (preset.isPresent()) {
            Snapshot snapshot = presetSnapshot(preset.get());
            return Result.success(
                    preset.get() == Preset.VANILLA
                            ? snapshots.defaultsPlan(snapshot, tr)
                            : snapshots.differences(snapshot, tr, who));
        }
        return snapshots
                .plan(id, who, tr)
                .flatMap(plan -> plan.snapshot().profile()
                        ? Result.success(plan)
                        : Result.failure(Error.validation(
                                "Profiles.NotAProfile",
                                Text.key("controlcore.errors.profiles.not_profile", "{name} is not a profile.")
                                        .with("name", plan.snapshot().name()))));
    }

    Snapshot presetSnapshot(Preset preset) {
        Map<String, String> config = new TreeMap<>();
        Map<String, String> wanted = preset.worldValues();
        if (!wanted.isEmpty()) {
            for (SettingSnapshot current : serverConfig.snapshot(SettingScope.SERVER, Optional.empty())) {
                if (!ServerConfigEditor.isWorld(current.id()) || current.access() == SettingAccess.READ_ONLY) {
                    continue;
                }
                String key = current.id().key();
                String path = key.substring(key.indexOf('.') + 1);
                String value = wanted.get(path);
                if (value != null) {
                    config.put(current.id().toString(), value);
                }
            }
        }
        Set<Part> parts = preset == Preset.VANILLA
                ? EnumSet.of(Part.CONFIG, Part.WORLDS, Part.SETTINGS)
                : EnumSet.of(Part.WORLDS, Part.SETTINGS);
        return new Snapshot(
                preset.key,
                Instant.EPOCH,
                "ControlCore",
                parts,
                Set.of(),
                config,
                preset.settingValues(),
                Map.of(),
                List.of(),
                Optional.empty(),
                Map.of(),
                Map.of(),
                Map.of(),
                true,
                true);
    }

    private String displayName(String id, Translator tr) {
        return Preset.of(id).map(preset -> name(preset, tr)).orElse(id);
    }

    private static String name(Preset preset, Translator tr) {
        return tr.t("controlcore.profiles.preset." + preset.key + ".name");
    }

    private Result<Boolean> allowed(Optional<PlayerId> who) {
        if (snapshots.mayUse(who)) {
            return Result.success(true);
        }
        return Result.failure(Error.forbidden(
                "Snapshots.Forbidden",
                Text.key("controlcore.errors.snapshots.forbidden", "Only admins can use snapshots.")));
    }
}
