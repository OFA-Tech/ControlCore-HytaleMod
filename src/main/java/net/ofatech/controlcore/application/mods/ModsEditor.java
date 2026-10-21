package net.ofatech.controlcore.application.mods;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.api.KeymapsApi;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.settings.ModNames;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
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

/**
 * The Mods tab: every mod with its settings and keys, and (for admins) turning mods on and off from the next start.
 */
public final class ModsEditor {

    /** ControlCore itself; it cannot be turned off from its own panel. */
    static final String SELF = "OFATech:ControlCore";

    private final ModCatalog catalog;
    private final SettingsApi settings;
    private final KeymapsApi keymaps;
    private final ModNames modNames;
    private final SettingPermissionPolicy permissions;
    private final Log log;
    private final Setting<List<String>> hidden;

    public ModsEditor(
            ModCatalog catalog,
            SettingsApi settings,
            KeymapsApi keymaps,
            ModNames modNames,
            SettingPermissionPolicy permissions,
            Log log) {
        this.catalog = catalog;
        this.settings = settings;
        this.keymaps = keymaps;
        this.modNames = modNames;
        this.permissions = permissions;
        this.log = log;
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

    /** @return the Mods tab for {@code viewer} (empty: the console, may do everything) */
    public ModsView viewedBy(Optional<PlayerId> viewer) {
        boolean admin = viewer.isEmpty() || permissions.isAdmin(viewer.get());
        return new View(admin);
    }

    /** @return the names of mods that run differently after the next restart */
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

            List<Entry> entries = new ArrayList<>();
            for (ModInfo mod : safeMods()) {
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
                        admin && !mod.core() && !mod.id().equalsIgnoreCase(SELF)));
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
            return catalog.setEnabled(entry.get().mod().id(), enabled).map(changed -> {
                log.info((enabled ? "Enabled " : "Disabled ") + changed.id() + " from the next start");
                return new Entry(
                        changed,
                        entry.get().namespace(),
                        entry.get().hasServerSettings(),
                        entry.get().hasPreferences(),
                        entry.get().hasKeys(),
                        true);
            });
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

    private static String slug(String text) {
        return text.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-|-$", "");
    }
}
