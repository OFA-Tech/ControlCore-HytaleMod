package net.ofatech.controlcore.application.settings;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.settings.SettingRights;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.config.SettingPermissionStore;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class SettingPermissionPolicy {

    public static final String SETTING = "setting:";
    public static final String CATEGORY = "category:";
    public static final String MOD = "mod:";

    private final SettingPermissionStore store;
    private final Permissions permissions;
    private final SettingsApi settings;

    public SettingPermissionPolicy(SettingPermissionStore store, Permissions permissions, SettingsApi settings) {
        this.store = store;
        this.permissions = permissions;
        this.settings = settings;
    }

    public record Rule(Optional<String> node, Text source) {

        public String describe(Translator tr, SettingScope scope) {
            String who = node.map(value ->
                            value.equals(SettingPermission.EVERYONE) ? tr.t("controlcore.permissions.everyone") : value)
                    .orElse(tr.t(
                            scope == SettingScope.SERVER
                                    ? "controlcore.permissions.admins"
                                    : "controlcore.permissions.everyone"));
            return tr.t("controlcore.permissions.who", "who", who, "source", tr.translate(source));
        }
    }

    public record Resolved(Rule view, Rule edit) {}

    public Resolved resolve(SettingDefinition<?> definition) {
        return new Resolved(resolve(definition, SettingPermission::view), resolve(definition, SettingPermission::edit));
    }

    public SettingRights rights(Optional<PlayerId> viewer, SettingDefinition<?> definition) {
        if (viewer.isEmpty() || isAdmin(viewer.get())) {
            return SettingRights.EDIT;
        }
        Resolved rule = resolve(definition);
        if (passes(viewer.get(), rule.edit(), definition.scope())) {
            return SettingRights.EDIT;
        }
        return passes(viewer.get(), rule.view(), definition.scope()) ? SettingRights.VIEW : SettingRights.NONE;
    }

    public PermittedSettingsEditor editor(SettingsEditor settings, Optional<PlayerId> viewer) {
        return new PermittedSettingsEditor(settings, this, viewer);
    }

    public boolean isAdmin(PlayerId player) {
        return permissions.hasOwn(player, SettingsPermissions.ADMIN) || permissions.hasOwn(player, "admin");
    }

    public Map<String, SettingPermission> rules() {
        return store.all();
    }

    public Result<SettingPermission> setRule(String target, String side, String node) {
        Result<String> key = target(target);
        if (key.isFailure()) {
            return Result.failure(key.error());
        }
        String sideText = side == null ? "" : side.strip().toLowerCase(Locale.ROOT);
        if (!sideText.equals("view") && !sideText.equals("edit") && !sideText.equals("both")) {
            return Result.failure(Error.validation(
                    "Settings.InvalidPermissionSide",
                    Text.key(
                                    "controlcore.errors.settings.invalid_permission_side",
                                    "Choose view, edit or both, not '{side}'.")
                            .with("side", side)));
        }
        String value = node == null ? "" : node.strip();
        if (value.isEmpty()) {
            return Result.failure(Error.validation(
                    "Settings.InvalidPermission",
                    Text.key(
                            "controlcore.errors.settings.invalid_permission",
                            "Give a permission node, everyone or default.")));
        }
        String newNode = value.equalsIgnoreCase("default") ? null : value;
        try {
            SettingPermission rule = store.all().getOrDefault(key.value(), SettingPermission.INHERIT);
            if (!sideText.equals("edit")) {
                rule = rule.withView(newNode);
            }
            if (!sideText.equals("view")) {
                rule = rule.withEdit(newNode);
            }
            store.put(key.value(), rule);
            return Result.success(rule);
        } catch (RuntimeException e) {
            return Result.failure(Error.validation("Settings.InvalidPermission", e.getMessage()));
        }
    }

    public static Result<String> target(String target) {
        String text = target == null ? "" : target.strip().toLowerCase(Locale.ROOT);
        for (String prefix : new String[] {SETTING, CATEGORY, MOD}) {
            if (text.startsWith(prefix) && text.length() > prefix.length()) {
                if (prefix.equals(SETTING)) {
                    return SettingId.parse(text.substring(prefix.length())).map(id -> SETTING + id);
                }
                return Result.success(prefix + text.substring(prefix.length()).strip());
            }
        }
        if (text.contains(":")) {
            return SettingId.parse(text).map(id -> SETTING + id);
        }
        return Result.failure(Error.validation(
                "Settings.InvalidPermissionTarget",
                Text.key(
                                "controlcore.errors.settings.invalid_permission_target",
                                "Name a setting id (homes:max-homes), category:<name> or mod:<mod id>, not '{target}'.")
                        .with("target", target)));
    }

    private Rule resolve(SettingDefinition<?> definition, Function<SettingPermission, Optional<String>> side) {
        Map<String, SettingPermission> rules = store.all();
        String category = definition.category().strip().toLowerCase(Locale.ROOT);
        String[] targets = {
            SETTING + definition.id(),
            CATEGORY + category,
            MOD + definition.id().namespace()
        };
        Text[] sources = {
            Text.key("controlcore.permissions.source.admin_setting", "admin rule for this setting"),
            Text.key("controlcore.permissions.source.admin_category", "admin rule for category {category}")
                    .with("category", definition.category()),
            Text.key("controlcore.permissions.source.admin_mod", "admin rule for mod {mod}")
                    .with("mod", definition.id().namespace())
        };
        for (int index = 0; index < targets.length; index++) {
            Optional<String> node =
                    Optional.ofNullable(rules.get(targets[index])).flatMap(side);
            if (node.isPresent()) {
                return new Rule(node, sources[index]);
            }
        }
        Optional<String> own = side.apply(definition.permission());
        if (own.isPresent()) {
            return new Rule(own, Text.key("controlcore.permissions.source.mod", "set by the mod"));
        }
        Optional<String> fromCategory = side.apply(settings.categoryPermission(definition.category()));
        if (fromCategory.isPresent()) {
            return new Rule(
                    fromCategory,
                    Text.key("controlcore.permissions.source.mod_category", "set by the mod for category {category}")
                            .with("category", definition.category()));
        }
        return new Rule(Optional.empty(), Text.key("controlcore.permissions.source.default", "default"));
    }

    private boolean passes(PlayerId player, Rule rule, SettingScope scope) {
        return rule.node()
                .map(node -> node.equals(SettingPermission.EVERYONE) || permissions.has(player, node))
                .orElse(scope == SettingScope.PLAYER);
    }
}
