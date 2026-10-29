package net.ofatech.controlcore.application.snapshot;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.history.HistoryEditor;
import net.ofatech.controlcore.application.history.HistoryValues;
import net.ofatech.controlcore.application.keymaps.KeymapRegistry;
import net.ofatech.controlcore.application.mods.ModsEditor;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot.Part;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;
import net.ofatech.controlcore.domain.ports.config.SettingPermissionStore;
import net.ofatech.controlcore.domain.ports.config.SnapshotStore;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class SnapshotService {

    public static final String NODE = "snapshots";
    static final int PREVIEW_LINES = 80;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SnapshotStore store;
    private final SettingsRegistry settings;
    private final ServerConfigEditor serverConfig;
    private final KeymapRegistry keymaps;
    private final CustomKeyBindStore customBinds;
    private final SettingPermissionStore permissionStore;
    private final SettingPermissionPolicy policy;
    private final ModsEditor mods;
    private final PlayerData playerData;
    private final Players players;
    private final Permissions permissions;
    private final HistoryEditor history;
    private final Texts texts;
    private final Clock clock;
    private final Log log;

    public SnapshotService(
            SnapshotStore store,
            SettingsRegistry settings,
            ServerConfigEditor serverConfig,
            KeymapRegistry keymaps,
            CustomKeyBindStore customBinds,
            SettingPermissionStore permissionStore,
            SettingPermissionPolicy policy,
            ModsEditor mods,
            PlayerData playerData,
            Players players,
            Permissions permissions,
            HistoryEditor history,
            Texts texts,
            Clock clock,
            Log log) {
        this.store = store;
        this.settings = settings;
        this.serverConfig = serverConfig;
        this.keymaps = keymaps;
        this.customBinds = customBinds;
        this.permissionStore = permissionStore;
        this.policy = policy;
        this.mods = mods;
        this.playerData = playerData;
        this.players = players;
        this.permissions = permissions;
        this.history = history;
        this.texts = texts;
        this.clock = clock;
        this.log = log;
    }

    record Change(String what, String current, String target, Supplier<Result<?>> apply) {}

    public record Plan(Snapshot snapshot, List<Change> changes, int skipped) {}

    public boolean mayUse(Optional<PlayerId> who) {
        return who.isEmpty() || policy.isAdmin(who.get()) || permissions.hasOwn(who.get(), NODE);
    }

    public List<Snapshot> all() {
        return store.all();
    }

    public SnapshotsView viewedBy(PlayerId viewer, String name) {
        Translator tr = texts.forPlayer(Optional.of(viewer));
        Optional<PlayerId> who = Optional.of(viewer);
        Actor actor = Actor.player(viewer, name);
        return new SnapshotsView() {
            @Override
            public List<Entry> list() {
                return store.all().stream()
                        .map(snapshot -> new Entry(
                                snapshot.name(),
                                when(snapshot),
                                snapshot.createdBy(),
                                partsText(snapshot, tr),
                                snapshot.profile()))
                        .toList();
            }

            @Override
            public List<ModChoice> mods() {
                return SnapshotService.this.modChoices(tr);
            }

            @Override
            public Result<String> save(String snapshotName, Set<Part> parts, Optional<String> mod) {
                return SnapshotService.this
                        .save(snapshotName, parts, mod.map(Set::of).orElse(Set.of()), who, actor)
                        .map(saved -> savedText(saved, tr));
            }

            @Override
            public Result<Preview> preview(String snapshotName) {
                return plan(snapshotName, who, tr).map(plan -> SnapshotService.this.preview(plan, tr));
            }

            @Override
            public Result<String> restore(String snapshotName, boolean snapshotFirst) {
                return SnapshotService.this.restore(snapshotName, snapshotFirst, who, actor, tr);
            }

            @Override
            public Result<String> delete(String snapshotName) {
                return SnapshotService.this
                        .delete(snapshotName, who)
                        .map(deleted -> tr.t("controlcore.snapshots.deleted", "name", deleted));
            }

            @Override
            public Result<String> setProfile(String snapshotName, boolean profile) {
                return SnapshotService.this
                        .setProfile(snapshotName, profile, who)
                        .map(saved -> tr.t(
                                profile ? "controlcore.profiles.marked" : "controlcore.profiles.unmarked",
                                "name",
                                saved.name()));
            }

            @Override
            public Result<String> deleteAll() {
                return SnapshotService.this
                        .deleteAll(who)
                        .map(count -> tr.t("controlcore.snapshots.deleted_all", "count", count));
            }

            @Override
            public Result<String> saveBefore(String base, Set<Part> parts) {
                return SnapshotService.this
                        .saveBefore(base, parts, Set.of(), who, actor)
                        .map(Snapshot::name);
            }
        };
    }

    public Result<Snapshot> setProfile(String rawName, boolean profile, Optional<PlayerId> who) {
        Result<Boolean> allowed = allowed(who);
        if (allowed.isFailure()) {
            return Result.failure(allowed.error());
        }
        return find(rawName).flatMap(snapshot -> store.save(snapshot.asProfile(profile)));
    }

    public Result<Integer> deleteAll(Optional<PlayerId> who) {
        Result<Boolean> allowed = allowed(who);
        if (allowed.isFailure()) {
            return Result.failure(allowed.error());
        }
        int deleted = 0;
        for (Snapshot snapshot : store.all()) {
            if (!snapshot.profile() && store.delete(snapshot.name())) {
                deleted++;
            }
        }
        log.info("Deleted " + deleted + " snapshot(s); profiles were kept");
        return Result.success(deleted);
    }

    public Result<Snapshot> saveBefore(
            String base, Set<Part> parts, Set<String> onlyMods, Optional<PlayerId> who, Actor actor) {
        return save(safetyName(base), parts, onlyMods, who, actor);
    }

    public Result<Snapshot> save(
            String rawName, Set<Part> parts, Set<String> onlyMods, Optional<PlayerId> who, Actor actor) {
        Result<Boolean> allowed = allowed(who);
        if (allowed.isFailure()) {
            return Result.failure(allowed.error());
        }
        if (parts.isEmpty()) {
            return Result.failure(Error.validation(
                    "Snapshots.NoParts",
                    Text.key("controlcore.errors.snapshots.no_parts", "Choose at least one part to save.")));
        }
        return Snapshot.validName(rawName).flatMap(name -> {
            Snapshot snapshot = capture(name, parts, onlyMods, actor);
            return store.save(snapshot).map(saved -> {
                log.info(actor.name() + " saved snapshot " + name + " (" + partsList(saved) + ")");
                return saved;
            });
        });
    }

    public Result<Plan> plan(String rawName, Optional<PlayerId> who, Translator tr) {
        Result<Boolean> allowed = allowed(who);
        if (allowed.isFailure()) {
            return Result.failure(allowed.error());
        }
        boolean admin = who.isEmpty() || policy.isAdmin(who.get());
        return find(rawName).map(snapshot -> differences(snapshot, tr, admin));
    }

    public SnapshotsView.Preview preview(Plan plan, Translator tr) {
        List<String> lines = new ArrayList<>();
        for (Change change : plan.changes()) {
            if (lines.size() == PREVIEW_LINES) {
                lines.add(tr.t(
                        "controlcore.snapshots.preview.more",
                        "count",
                        plan.changes().size() - PREVIEW_LINES));
                break;
            }
            lines.add(tr.t(
                    "controlcore.snapshots.preview.line",
                    "what",
                    change.what(),
                    "current",
                    change.current(),
                    "target",
                    change.target()));
        }
        return new SnapshotsView.Preview(
                plan.snapshot().name(), lines, plan.changes().size(), plan.skipped());
    }

    public Result<String> restore(
            String rawName, boolean snapshotFirst, Optional<PlayerId> who, Actor actor, Translator tr) {
        Result<Plan> planned = plan(rawName, who, tr);
        if (planned.isFailure()) {
            return Result.failure(planned.error());
        }
        Plan plan = planned.value();
        return apply(plan, snapshotFirst ? Optional.of(plan.snapshot().name()) : Optional.empty(), who, actor, tr);
    }

    public Result<String> apply(
            Plan plan, Optional<String> saveFirstAs, Optional<PlayerId> who, Actor actor, Translator tr) {
        String safety = "";
        if (saveFirstAs.isPresent()) {
            Result<Snapshot> saved = saveBefore(
                    saveFirstAs.get(), plan.snapshot().parts(), plan.snapshot().mods(), who, actor);
            if (saved.isFailure()) {
                return Result.failure(saved.error());
            }
            safety = " "
                    + tr.t(
                            "controlcore.snapshots.safety_saved",
                            "name",
                            saved.value().name());
        }
        List<String> failures = new ArrayList<>();
        int applied = ChangeContext.restoringSnapshot(actor, () -> {
            int done = 0;
            for (Change change : plan.changes()) {
                Result<?> result;
                try {
                    result = change.apply().get();
                } catch (RuntimeException e) {
                    result = Result.failure(Error.failure("Snapshots.ApplyFailed", String.valueOf(e.getMessage())));
                }
                if (result.isSuccess()) {
                    done++;
                } else {
                    failures.add(change.what() + ": " + tr.error(result.error()));
                }
            }
            return done;
        });
        playerData.flush();
        log.info(actor.name() + " restored snapshot " + plan.snapshot().name() + ": " + applied + " change(s), "
                + failures.size() + " failed");
        failures.forEach(failure -> log.warn("Snapshot " + plan.snapshot().name() + ": " + failure));
        String text = tr.t(
                "controlcore.snapshots.restored",
                "name",
                plan.snapshot().name(),
                "applied",
                applied,
                "failed",
                failures.size());
        if (!failures.isEmpty()) {
            text += " " + tr.t("controlcore.snapshots.first_failure", "failure", failures.getFirst());
        }
        return Result.success(text + safety);
    }

    public Result<String> delete(String rawName, Optional<PlayerId> who) {
        Result<Boolean> allowed = allowed(who);
        if (allowed.isFailure()) {
            return Result.failure(allowed.error());
        }
        return find(rawName).map(snapshot -> {
            store.delete(snapshot.name());
            log.info("Deleted snapshot " + snapshot.name());
            return snapshot.name();
        });
    }

    public String when(Snapshot snapshot) {
        return WHEN.format(snapshot.createdAt().atZone(ZoneId.systemDefault()));
    }

    public String partsText(Snapshot snapshot, Translator tr) {
        List<String> names = new ArrayList<>();
        for (Part part : snapshot.parts()) {
            String name = tr.t("controlcore.snapshots.part." + part.id());
            if (part == Part.SETTINGS && !snapshot.mods().isEmpty()) {
                name += " (" + String.join(", ", new TreeSet<>(snapshot.mods())) + ")";
            }
            names.add(name);
        }
        return String.join(", ", names);
    }

    public String savedText(Snapshot snapshot, Translator tr) {
        return tr.t("controlcore.snapshots.saved", "name", snapshot.name(), "parts", partsText(snapshot, tr));
    }

    public List<SnapshotsView.ModChoice> modChoices(Translator tr) {
        Map<String, String> found = new TreeMap<>();
        for (SettingDefinition<?> definition : settings.definitions()) {
            if (definition.scope() == SettingScope.SERVER) {
                String namespace = definition.id().namespace();
                found.putIfAbsent(namespace, tr.translate(settings.modName(namespace)));
            }
        }
        return found.entrySet().stream()
                .map(entry -> new SnapshotsView.ModChoice(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Result<Snapshot> find(String rawName) {
        return Snapshot.validName(rawName)
                .flatMap(name -> store.find(name)
                        .<Result<Snapshot>>map(Result::success)
                        .orElseGet(() -> Result.failure(Error.notFound(
                                "Snapshots.Unknown",
                                Text.key("controlcore.errors.snapshots.unknown", "There is no snapshot {name}.")
                                        .with("name", name)))));
    }

    public Plan defaultsPlan(Snapshot label, Translator tr) {
        List<Change> changes = new ArrayList<>();
        int skipped = 0;
        if (label.has(Part.CONFIG) || label.has(Part.WORLDS)) {
            for (SettingSnapshot current : serverConfig.snapshot(SettingScope.SERVER, Optional.empty())) {
                Part part = ServerConfigEditor.isWorld(current.id()) ? Part.WORLDS : Part.CONFIG;
                if (!label.has(part) || current.access() == SettingAccess.READ_ONLY || !current.overridden()) {
                    continue;
                }
                if (!serverConfig.hasDefault(current.id())) {
                    skipped++;
                    continue;
                }
                String id = current.id().toString();
                changes.add(new Change(
                        history.what(Kind.SERVER_CONFIG, id, tr),
                        shown(current.value(), tr),
                        tr.t(
                                "controlcore.snapshots.value.default",
                                "value",
                                current.definition().formattedDefault()),
                        () -> serverConfig.reset(current.id(), Optional.empty())));
            }
        }
        if (label.has(Part.SETTINGS)) {
            for (SettingSnapshot current : settings.snapshot(SettingScope.SERVER, Optional.empty())) {
                if (!current.overridden()) {
                    continue;
                }
                String id = current.id().toString();
                changes.add(new Change(
                        history.what(Kind.SETTING, id, tr),
                        shown(current.value(), tr),
                        tr.t(
                                "controlcore.snapshots.value.default",
                                "value",
                                current.definition().formattedDefault()),
                        () -> settings.reset(current.id(), Optional.empty())));
            }
        }
        return new Plan(label, changes, skipped);
    }

    private Result<Boolean> allowed(Optional<PlayerId> who) {
        if (mayUse(who)) {
            return Result.success(true);
        }
        return Result.failure(Error.forbidden(
                "Snapshots.Forbidden",
                Text.key("controlcore.errors.snapshots.forbidden", "Only admins can use snapshots.")));
    }

    private String safetyName(String name) {
        String base = "before-" + name;
        base = base.length() > 33 ? base.substring(0, 33) : base;
        String candidate = base;
        for (int number = 2; store.find(candidate).isPresent(); number++) {
            candidate = base + "-" + number;
        }
        return candidate;
    }

    private static String partsList(Snapshot snapshot) {
        return snapshot.parts().stream().map(Part::id).collect(Collectors.joining(", "));
    }

    Snapshot capture(String name, Set<Part> parts, Set<String> onlyMods, Actor actor) {
        Map<String, String> config = new TreeMap<>();
        Map<String, String> settingValues = new TreeMap<>();
        Set<String> namespaces = new TreeSet<>();
        Map<String, String> serverKeys = new TreeMap<>();
        List<CustomKeyBind> binds = new ArrayList<>();
        Map<String, SettingPermission> rules = new TreeMap<>();
        Map<String, Boolean> enabled = new TreeMap<>();
        Map<String, Map<String, String>> playerValues = new TreeMap<>();
        if (parts.contains(Part.CONFIG) || parts.contains(Part.WORLDS)) {
            for (SettingSnapshot snapshot : serverConfig.snapshot(SettingScope.SERVER, Optional.empty())) {
                Part part = ServerConfigEditor.isWorld(snapshot.id()) ? Part.WORLDS : Part.CONFIG;
                if (parts.contains(part) && snapshot.access() != SettingAccess.READ_ONLY) {
                    config.put(snapshot.id().toString(), snapshot.value());
                }
            }
        }
        if (parts.contains(Part.SETTINGS)) {
            Set<String> wanted = lower(onlyMods);
            for (SettingSnapshot snapshot : settings.snapshot(SettingScope.SERVER, Optional.empty())) {
                String namespace = snapshot.id().namespace();
                if (!wanted.isEmpty() && !wanted.contains(namespace)) {
                    continue;
                }
                namespaces.add(namespace);
                if (snapshot.overridden()) {
                    settingValues.put(snapshot.id().toString(), snapshot.value());
                }
            }
        }
        Map<String, String> gameActionModes = new TreeMap<>();
        if (parts.contains(Part.KEYS)) {
            for (KeyAction action : keymaps.actions()) {
                if (keymaps.hasServerKeys(action.id())) {
                    serverKeys.put(
                            action.id().toString(),
                            keymaps.serverKeys(action.id()).toString());
                }
            }
            binds.addAll(customBinds.all());
            for (KeyAction action : keymaps.actions()) {
                GameActionMode mode = keymaps.gameAction(action.id());
                if (mode != GameActionMode.SERVER_SETTING) {
                    gameActionModes.put(action.id().toString(), mode.id());
                }
            }
        }
        if (parts.contains(Part.PERMISSIONS)) {
            permissionStore.all().forEach((target, rule) -> {
                if (!rule.isInherit()) {
                    rules.put(target, rule);
                }
            });
        }
        if (parts.contains(Part.MODS)) {
            for (ModsView.Entry entry : mods.viewedBy(Optional.empty()).list()) {
                if (entry.manageable()) {
                    enabled.put(entry.mod().id(), entry.mod().enabledAfterRestart());
                }
            }
        }
        if (parts.contains(Part.PLAYERS)) {
            playerData.all().forEach((player, values) -> {
                Map<String, String> kept = new TreeMap<>();
                values.forEach((key, value) -> {
                    if (playerKey(key)) {
                        kept.put(key, value);
                    }
                });
                if (!kept.isEmpty()) {
                    playerValues.put(player.value().toString(), kept);
                }
            });
        }
        return new Snapshot(
                name,
                clock.now(),
                actor.isMod() ? "" : actor.name(),
                parts.isEmpty() ? EnumSet.noneOf(Part.class) : EnumSet.copyOf(parts),
                parts.contains(Part.SETTINGS) ? namespaces : Set.of(),
                config,
                settingValues,
                serverKeys,
                binds,
                parts.contains(Part.KEYS) ? Optional.of(gameActionModes) : Optional.empty(),
                rules,
                enabled,
                playerValues,
                false,
                false);
    }

    Plan differences(Snapshot snapshot, Translator tr) {
        return differences(snapshot, tr, true);
    }

    private Plan differences(Snapshot snapshot, Translator tr, boolean adminParts) {
        List<Change> changes = new ArrayList<>();
        List<Change> adminOnly = adminParts ? changes : new ArrayList<>();
        int[] skipped = {0};
        if (snapshot.has(Part.CONFIG) || snapshot.has(Part.WORLDS)) {
            snapshot.config().forEach((id, value) -> {
                boolean world =
                        SettingId.parse(id).map(ServerConfigEditor::isWorld).orElse(false);
                if (!snapshot.has(world ? Part.WORLDS : Part.CONFIG)) {
                    return;
                }
                Result<SettingSnapshot> current =
                        SettingId.parse(id).flatMap(parsed -> serverConfig.read(parsed, Optional.empty()));
                if (current.isFailure() || current.value().access() == SettingAccess.READ_ONLY) {
                    skipped[0]++;
                } else if (!current.value().value().equals(value)) {
                    SettingId parsed = current.value().id();
                    changes.add(new Change(
                            history.what(Kind.SERVER_CONFIG, id, tr),
                            shown(current.value().value(), tr),
                            shown(value, tr),
                            () -> serverConfig.write(parsed, Optional.empty(), value)));
                }
            });
        }
        if (snapshot.has(Part.SETTINGS) && snapshot.partial()) {
            snapshot.settings().forEach((id, target) -> {
                Result<SettingSnapshot> current =
                        SettingId.parse(id).flatMap(parsed -> settings.read(parsed, Optional.empty()));
                if (current.isFailure()) {
                    skipped[0]++;
                } else if (!current.value().value().equals(target)) {
                    SettingId parsed = current.value().id();
                    changes.add(new Change(
                            history.what(Kind.SETTING, id, tr),
                            shown(current.value().value(), tr),
                            shown(target, tr),
                            () -> settings.write(parsed, Optional.empty(), target)));
                }
            });
        } else if (snapshot.has(Part.SETTINGS)) {
            Set<String> known = new LinkedHashSet<>();
            for (SettingSnapshot current : settings.snapshot(SettingScope.SERVER, Optional.empty())) {
                String id = current.id().toString();
                if (!snapshot.mods().contains(current.id().namespace())) {
                    continue;
                }
                known.add(id);
                String target = snapshot.settings().get(id);
                if (target != null && !target.equals(current.value())) {
                    changes.add(new Change(
                            history.what(Kind.SETTING, id, tr),
                            shown(current.value(), tr),
                            shown(target, tr),
                            () -> settings.write(current.id(), Optional.empty(), target)));
                } else if (target == null && current.overridden()) {
                    changes.add(new Change(
                            history.what(Kind.SETTING, id, tr),
                            shown(current.value(), tr),
                            tr.t(
                                    "controlcore.snapshots.value.default",
                                    "value",
                                    current.definition().formattedDefault()),
                            () -> settings.reset(current.id(), Optional.empty())));
                }
            }
            skipped[0] += (int) snapshot.settings().keySet().stream()
                    .filter(id -> !known.contains(id))
                    .count();
        }
        if (snapshot.has(Part.KEYS)) {
            keyChanges(snapshot, tr, adminOnly);
        }
        if (snapshot.has(Part.PERMISSIONS)) {
            Map<String, SettingPermission> current = permissionStore.all();
            Set<String> targets = new TreeSet<>(snapshot.permissions().keySet());
            current.forEach((target, rule) -> {
                if (!rule.isInherit()) {
                    targets.add(target);
                }
            });
            for (String target : targets) {
                SettingPermission now = current.getOrDefault(target, SettingPermission.INHERIT);
                SettingPermission then = snapshot.permissions().getOrDefault(target, SettingPermission.INHERIT);
                if (!now.equals(then)) {
                    adminOnly.add(new Change(
                            history.what(Kind.PERMISSION, target, tr),
                            history.shown(Kind.PERMISSION, HistoryValues.rule(now), tr),
                            history.shown(Kind.PERMISSION, HistoryValues.rule(then), tr),
                            () -> policy.replaceRule(
                                    target, then.view().orElse(""), then.edit().orElse(""))));
                }
            }
        }
        if (snapshot.has(Part.MODS)) {
            ModsView view = mods.viewedBy(Optional.empty());
            Map<String, ModsView.Entry> entries = new HashMap<>();
            view.list().forEach(entry -> entries.put(entry.mod().id(), entry));
            snapshot.modsEnabled().forEach((id, wanted) -> {
                ModsView.Entry entry = entries.get(id);
                if (entry == null || !entry.manageable()) {
                    skipped[0]++;
                } else if (entry.mod().enabledAfterRestart() != wanted) {
                    changes.add(new Change(
                            entry.mod().name(),
                            history.shown(Kind.MOD, Boolean.toString(!wanted), tr),
                            history.shown(Kind.MOD, Boolean.toString(wanted), tr),
                            () -> view.setEnabled(id, wanted)));
                }
            });
        }
        if (snapshot.has(Part.PLAYERS)) {
            playerChanges(snapshot, tr, changes, skipped);
        }
        if (!adminParts) {
            skipped[0] += adminOnly.size();
        }
        return new Plan(snapshot, changes, skipped[0]);
    }

    private void keyChanges(Snapshot snapshot, Translator tr, List<Change> changes) {
        Map<String, CustomKeyBind> wanted = new LinkedHashMap<>();
        snapshot.customBinds().forEach(bind -> wanted.put(bind.name(), bind));
        Map<String, CustomKeyBind> current = new LinkedHashMap<>();
        customBinds.all().forEach(bind -> current.put(bind.name(), bind));
        for (CustomKeyBind bind : wanted.values()) {
            CustomKeyBind now = current.get(bind.name());
            if (!bind.equals(now)) {
                changes.add(new Change(
                        history.what(Kind.CUSTOM_BIND, KeymapRegistry.CUSTOM + ":" + bind.name(), tr),
                        history.shown(Kind.CUSTOM_BIND, HistoryValues.bind(Optional.ofNullable(now)), tr),
                        history.shown(Kind.CUSTOM_BIND, HistoryValues.bind(Optional.of(bind)), tr),
                        () -> keymaps.addCustom(bind.name(), bind.keys(), bind.command(), bind.runAsServer())));
            }
        }
        for (CustomKeyBind bind : current.values()) {
            if (!wanted.containsKey(bind.name())) {
                changes.add(new Change(
                        history.what(Kind.CUSTOM_BIND, KeymapRegistry.CUSTOM + ":" + bind.name(), tr),
                        history.shown(Kind.CUSTOM_BIND, HistoryValues.bind(Optional.of(bind)), tr),
                        history.shown(Kind.CUSTOM_BIND, "", tr),
                        () -> keymaps.removeCustom(bind.name())));
            }
        }
        Set<SettingId> ids = new LinkedHashSet<>();
        for (KeyAction action : keymaps.actions()) {
            boolean custom = KeymapRegistry.CUSTOM.equals(action.id().namespace());
            if (!custom || wanted.containsKey(action.id().key())) {
                ids.add(action.id());
            }
        }
        wanted.keySet().forEach(name -> ids.add(new SettingId(KeymapRegistry.CUSTOM, name)));
        for (SettingId id : ids) {
            String target = snapshot.serverKeys().get(id.toString());
            String now = keymaps.find(id).isPresent() && keymaps.hasServerKeys(id)
                    ? keymaps.serverKeys(id).toString()
                    : null;
            if (Objects.equals(target, now)) {
                continue;
            }
            String what = history.what(Kind.SERVER_KEYS, id.toString(), tr);
            String nowText = now == null ? tr.t("controlcore.snapshots.value.default_keys") : now;
            if (target != null) {
                changes.add(new Change(what, nowText, target, () -> keymaps.bindServer(id, target)));
            } else if (keymaps.find(id).isPresent()) {
                changes.add(new Change(
                        what,
                        nowText,
                        tr.t("controlcore.snapshots.value.default_keys"),
                        () -> keymaps.resetServerBind(id)));
            }
        }
        snapshot.gameActions().ifPresent(modes -> {
            for (KeyAction action : keymaps.actions()) {
                GameActionMode now = keymaps.gameAction(action.id());
                GameActionMode then =
                        GameActionMode.parse(modes.get(action.id().toString())).orElse(GameActionMode.SERVER_SETTING);
                if (now != then) {
                    changes.add(new Change(
                            history.what(Kind.GAME_ACTION, action.id().toString(), tr),
                            history.shown(Kind.GAME_ACTION, now.id(), tr),
                            history.shown(Kind.GAME_ACTION, then.id(), tr),
                            () -> keymaps.setGameAction(action.id(), then)));
                }
            }
        });
    }

    private void playerChanges(Snapshot snapshot, Translator tr, List<Change> changes, int[] skipped) {
        Map<PlayerId, Map<String, String>> stored = playerData.all();
        Map<String, Map<String, String>> wanted = new TreeMap<>(snapshot.players());
        stored.forEach((player, values) -> {
            if (values.keySet().stream().anyMatch(SnapshotService::playerKey)) {
                wanted.putIfAbsent(player.value().toString(), Map.of());
            }
        });
        wanted.forEach((uuid, values) -> {
            PlayerId player;
            try {
                player = PlayerId.of(UUID.fromString(uuid));
            } catch (IllegalArgumentException e) {
                skipped[0]++;
                return;
            }
            String playerName = players.find(player).map(info -> info.name()).orElse(uuid.substring(0, 8));
            Map<String, String> now = stored.getOrDefault(player, Map.of());
            Set<String> keys = new TreeSet<>(values.keySet());
            now.keySet().stream().filter(SnapshotService::playerKey).forEach(keys::add);
            for (String key : keys) {
                String then = values.get(key);
                String current = now.get(key);
                if (Objects.equals(then, current)) {
                    continue;
                }
                Optional<Change> change = playerChange(player, playerName, key, current, then, tr);
                if (change.isPresent()) {
                    changes.add(change.get());
                } else {
                    skipped[0]++;
                }
            }
        });
    }

    private Optional<Change> playerChange(
            PlayerId player, String playerName, String key, String current, String then, Translator tr) {
        String defaultText = tr.t("controlcore.snapshots.value.player_default");
        if (key.startsWith(SettingsRegistry.PLAYER_KEY_PREFIX)) {
            Result<SettingId> id = SettingId.parse(key.substring(SettingsRegistry.PLAYER_KEY_PREFIX.length()));
            if (id.isFailure() || settings.definition(id.value()).isEmpty()) {
                return Optional.empty();
            }
            SettingId setting = id.value();
            String name = tr.translate(settings.definition(setting).get().nameText());
            return Optional.of(new Change(
                    tr.t("controlcore.snapshots.what.player", "player", playerName, "name", name),
                    current == null ? defaultText : shown(current, tr),
                    then == null ? defaultText : shown(then, tr),
                    () -> then == null
                            ? settings.reset(setting, Optional.of(player))
                            : settings.write(setting, Optional.of(player), then)));
        }
        if (key.startsWith(KeymapRegistry.PLAYER_KEY_PREFIX)) {
            Result<SettingId> id = SettingId.parse(key.substring(KeymapRegistry.PLAYER_KEY_PREFIX.length()));
            if (id.isFailure() || keymaps.find(id.value()).isEmpty()) {
                return Optional.empty();
            }
            SettingId action = id.value();
            String name = tr.translate(keymaps.find(action).get().nameText());
            return Optional.of(new Change(
                    tr.t("controlcore.snapshots.what.player", "player", playerName, "name", name),
                    current == null ? defaultText : current,
                    then == null ? defaultText : then,
                    () -> then == null ? keymaps.resetBind(action, player) : keymaps.bind(action, player, then)));
        }
        return Optional.empty();
    }

    private static boolean playerKey(String key) {
        return key.startsWith(SettingsRegistry.PLAYER_KEY_PREFIX) || key.startsWith(KeymapRegistry.PLAYER_KEY_PREFIX);
    }

    private static String shown(String value, Translator tr) {
        return value == null || value.isEmpty() ? tr.t("controlcore.history.value.empty") : value;
    }

    private static Set<String> lower(Set<String> values) {
        return values.stream()
                .map(value -> value.strip().toLowerCase(java.util.Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }
}
