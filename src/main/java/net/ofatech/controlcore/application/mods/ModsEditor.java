package net.ofatech.controlcore.application.mods;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.api.KeymapsApi;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.history.ChangeRecorder;
import net.ofatech.controlcore.application.settings.ModNames;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.mods.ModDependency;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class ModsEditor {

    static final String SELF = "OFATech:ControlCore";

    private final ModCatalog catalog;
    private final SettingsApi settings;
    private final KeymapsApi keymaps;
    private final ModNames modNames;
    private final SettingPermissionPolicy permissions;
    private final Log log;
    private final ChangeRecorder history;
    private final Setting<List<String>> hidden;

    public ModsEditor(
            ModCatalog catalog,
            SettingsApi settings,
            KeymapsApi keymaps,
            ModNames modNames,
            SettingPermissionPolicy permissions,
            Log log,
            ChangeRecorder history) {
        this.catalog = catalog;
        this.settings = settings;
        this.keymaps = keymaps;
        this.modNames = modNames;
        this.permissions = permissions;
        this.log = log;
        this.history = history;
        this.hidden = settings.register(SettingDefinition.server(
                                "controlcore", "mods-hidden", SettingType.textList(200, 128), List.<String>of())
                        .named(Text.key("controlcore.settings.mods_hidden.name", "Mods hidden from players"))
                        .describedAs(Text.key(
                                "controlcore.settings.mods_hidden.desc",
                                "Mods (Group:Name or name) that players do not see in the Mods tab. Admins see"
                                        + " every mod."))
                        .inCategory(Text.key("controlcore.mods.category", "Mods tab")))
                .value();
    }

    public ModsView viewedBy(Optional<PlayerId> viewer) {
        boolean admin = viewer.isEmpty() || permissions.isAdmin(viewer.get());
        return new View(admin);
    }

    public List<Text> pendingRestart() {
        List<Text> names = new ArrayList<>();
        for (ModInfo mod : safeMods()) {
            if (mod.changesAfterRestart()) {
                names.add(Text.of(mod.name()));
            }
        }
        return names;
    }

    private List<ModInfo> safeMods() {
        try {
            return catalog.mods();
        } catch (RuntimeException e) {
            log.error("Could not list the server's mods", e);
            return List.of();
        }
    }

    private final class View implements ModsView {

        private final boolean admin;

        View(boolean admin) {
            this.admin = admin;
        }

        @Override
        public List<Entry> list() {
            Set<String> serverNamespaces = new HashSet<>();
            Set<String> preferenceNamespaces = new HashSet<>();
            for (SettingDefinition<?> definition : settings.definitions()) {
                (definition.scope() == SettingScope.SERVER ? serverNamespaces : preferenceNamespaces)
                        .add(definition.id().namespace());
            }
            Set<String> keyNamespaces = new HashSet<>();
            for (KeyAction action : keymaps.actions()) {
                keyNamespaces.add(action.id().namespace());
            }
            Set<String> namespaces = new HashSet<>(serverNamespaces);
            namespaces.addAll(preferenceNamespaces);
            namespaces.addAll(keyNamespaces);
            Set<String> hiddenMods = new HashSet<>();
            hidden.get().forEach(entry -> hiddenMods.add(entry.toLowerCase(Locale.ROOT)));

            List<ModInfo> all = withLiveStatus(safeMods());
            List<Entry> entries = new ArrayList<>();
            for (ModInfo mod : all) {
                if (!admin
                        && (hiddenMods.contains(mod.id().toLowerCase(Locale.ROOT))
                                || hiddenMods.contains(mod.name().toLowerCase(Locale.ROOT)))) {
                    continue;
                }
                Optional<String> namespace = namespaceOf(mod, namespaces);
                entries.add(new Entry(
                        mod,
                        namespace,
                        namespace.filter(serverNamespaces::contains).isPresent(),
                        namespace.filter(preferenceNamespaces::contains).isPresent(),
                        namespace.filter(keyNamespaces::contains).isPresent(),
                        admin && !mod.core() && !mod.id().equalsIgnoreCase(SELF),
                        neededBy(mod, all)));
            }
            entries.sort(
                    Comparator.comparing((Entry entry) -> entry.mod().name().toLowerCase(Locale.ROOT))
                            .thenComparing(entry -> entry.mod().id()));
            return entries;
        }

        @Override
        public boolean canManage() {
            return admin;
        }

        @Override
        public Result<Entry> setEnabled(String modId, boolean enabled) {
            Optional<Entry> entry = list().stream()
                    .filter(candidate -> candidate.mod().id().equalsIgnoreCase(modId))
                    .findFirst();
            if (entry.isEmpty()) {
                return Result.failure(Error.notFound(
                        "Mods.Unknown",
                        Text.key("controlcore.errors.mods.unknown", "There is no mod {id}.")
                                .with("id", modId)));
            }
            if (!entry.get().manageable()) {
                return Result.failure(Error.forbidden(
                        "Mods.Forbidden",
                        Text.key("controlcore.errors.mods.forbidden", "You may not turn {name} on or off.")
                                .with("name", entry.get().mod().name())));
            }
            List<Entry> others = alsoChanged(modId, enabled);
            for (Entry other : others) {
                if (!other.manageable()) {
                    return Result.failure(Error.forbidden(
                            "Mods.Required",
                            Text.key(
                                            "controlcore.errors.mods.required",
                                            "{name} is needed by {other}, which can't be turned off here.")
                                    .with(
                                            "name",
                                            entry.get().mod().name(),
                                            "other",
                                            other.mod().name())));
                }
            }
            for (Entry other : others) {
                Result<ModInfo> changed = change(other, enabled);
                if (changed.isFailure()) {
                    return Result.failure(changed.error());
                }
            }
            return change(entry.get(), enabled)
                    .map(changed -> new Entry(
                            changed,
                            entry.get().namespace(),
                            entry.get().hasServerSettings(),
                            entry.get().hasPreferences(),
                            entry.get().hasKeys(),
                            true,
                            entry.get().neededBy()));
        }

        private Result<ModInfo> change(Entry entry, boolean enabled) {
            boolean before = entry.mod().enabledAfterRestart();
            if (before == enabled) {
                return Result.success(entry.mod());
            }
            return catalog.setEnabled(entry.mod().id(), enabled).onSuccess(changed -> {
                log.info((enabled ? "Enabled " : "Disabled ") + changed.id() + " from the next start");
                history.record(Kind.MOD, changed.id(), Boolean.toString(before), Boolean.toString(enabled), true);
            });
        }

        @Override
        public List<Entry> alsoChanged(String modId, boolean enabled) {
            List<Entry> entries = list();
            Map<String, Entry> byId = new HashMap<>();
            entries.forEach(entry -> byId.put(key(entry.mod().id()), entry));
            Set<String> seen = new LinkedHashSet<>();
            Deque<String> todo = new ArrayDeque<>();
            todo.add(key(modId));
            while (!todo.isEmpty()) {
                String current = todo.poll();
                if (enabled) {
                    Entry entry = byId.get(current);
                    if (entry == null) {
                        continue;
                    }
                    for (ModDependency dependency : entry.mod().required()) {
                        Entry needed = byId.get(key(dependency.id()));
                        if (needed != null
                                && !needed.mod().enabledAfterRestart()
                                && !needed.mod().core()
                                && seen.add(key(needed.mod().id()))) {
                            todo.add(key(needed.mod().id()));
                        }
                    }
                } else {
                    for (Entry candidate : entries) {
                        boolean requires = candidate.mod().required().stream()
                                .anyMatch(dependency -> key(dependency.id()).equals(current));
                        if (requires
                                && candidate.mod().enabledAfterRestart()
                                && seen.add(key(candidate.mod().id()))) {
                            todo.add(key(candidate.mod().id()));
                        }
                    }
                }
            }
            seen.remove(key(modId));
            List<Entry> result = new ArrayList<>();
            for (String id : seen) {
                result.add(byId.get(id));
            }
            return result;
        }

        @Override
        public List<ModCatalog.ConfigFile> configFiles(String modId) {
            boolean visible = list().stream().anyMatch(entry -> entry.mod().id().equalsIgnoreCase(modId));
            if (!visible) {
                return List.of();
            }
            try {
                return catalog.configFiles(modId);
            } catch (RuntimeException e) {
                log.error("Could not read the config files of " + modId, e);
                return List.of();
            }
        }

        private Optional<String> namespaceOf(ModInfo mod, Set<String> namespaces) {
            List<String> candidates = List.of(
                    slug(mod.name()),
                    slug(mod.group() + "_" + mod.name()),
                    slug(mod.group() + "-" + mod.name()),
                    mod.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
            for (String candidate : candidates) {
                if (namespaces.contains(candidate)) {
                    return Optional.of(candidate);
                }
            }
            for (String namespace : namespaces) {
                if (modNames.of(namespace).fallbackText().equalsIgnoreCase(mod.name())) {
                    return Optional.of(namespace);
                }
            }
            return Optional.empty();
        }
    }

    static List<ModInfo> withLiveStatus(List<ModInfo> mods) {
        Map<String, ModInfo> byId = new HashMap<>();
        mods.forEach(mod -> byId.put(key(mod.id()), mod));
        List<ModInfo> result = new ArrayList<>();
        for (ModInfo mod : mods) {
            List<ModDependency> dependencies = new ArrayList<>();
            for (ModDependency dependency : mod.dependencies()) {
                ModInfo found = byId.get(key(dependency.id()));
                boolean off = found != null
                        && dependency.status() == ModDependency.Status.OK
                        && !found.core()
                        && !found.enabledAfterRestart();
                dependencies.add(off ? dependency.withStatus(ModDependency.Status.OFF) : dependency);
            }
            result.add(mod.withDependencies(dependencies));
        }
        return result;
    }

    static List<String> neededBy(ModInfo mod, List<ModInfo> all) {
        List<String> names = new ArrayList<>();
        for (ModInfo other : all) {
            if (other.enabledAfterRestart()
                    && other.required().stream()
                            .anyMatch(dependency -> key(dependency.id()).equals(key(mod.id())))) {
                names.add(other.name());
            }
        }
        return names;
    }

    private static String key(String id) {
        return id.strip().toLowerCase(Locale.ROOT);
    }

    private static String slug(String text) {
        return text.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-|-$", "");
    }
}
