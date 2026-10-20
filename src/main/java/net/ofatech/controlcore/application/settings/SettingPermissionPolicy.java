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

/**
 * Decides who may see and change each setting. For each side (see, change) the first rule that has one wins:
 *
 * <ol>
 *   <li>an admin's rule for the setting ({@code setting:<id>}),
 *   <li>an admin's rule for its category ({@code category:<name>}),
 *   <li>an admin's rule for its mod ({@code mod:<mod id>}),
 *   <li>the mod's rule on the setting ({@link SettingDefinition#permission()}),
 *   <li>the mod's rule for the category ({@link SettingsApi#setCategoryPermission}),
 *   <li>ControlCore's default: server settings need the {@code settings} permission, preferences are everyone's.
 * </ol>
 *
 * Changing implies seeing. Players with ControlCore's {@code settings} or {@code admin} permission, and the
 * console, may always see and change everything.
 */
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

    /** One side of a resolved rule: the node ({@code empty} = ControlCore's default) and where it came from. */
    public record Rule(Optional<String> node, Text source) {

        /** @return the rule as players read it */
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

    /** Both sides of the rule that applies to a setting. */
    public record Resolved(Rule view, Rule edit) {}

    /** @return the rule that applies to {@code definition}, side by side */
    public Resolved resolve(SettingDefinition<?> definition) {
        return new Resolved(resolve(definition, SettingPermission::view), resolve(definition, SettingPermission::edit));
    }

    /**
     * @param viewer the player, or empty for the console
     * @return what {@code viewer} may do with the setting
     */
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

    /**
     * @param viewer who uses the editor; empty for the console
     * @return {@code settings} as {@code viewer} may use it
     */
    public PermittedSettingsEditor editor(SettingsEditor settings, Optional<PlayerId> viewer) {
        return new PermittedSettingsEditor(settings, this, viewer);
    }

    /** @return whether the player may see and change every setting */
    public boolean isAdmin(PlayerId player) {
        return permissions.hasOwn(player, SettingsPermissions.ADMIN) || permissions.hasOwn(player, "admin");
    }

    /** @return admins' rules, by target */
    public Map<String, SettingPermission> rules() {
        return store.all();
    }

    /**
     * Sets one side of an admin's rule.
     *
     * @param target {@code setting:<id>} (or just the id), {@code category:<name>} or {@code mod:<mod id>}
     * @param side {@code view}, {@code edit} or {@code both}
     * @param node a permission node, {@code everyone}, or {@code default} to remove the rule for that side
     * @return the target's rule now
     */
    public Result<SettingPermission> setRule(String target, String side, String node) {
        Result<String> key = target(target);
        if (key.isFailure()) {
            return Result.failure(key.error());
        }
        String sideText = side == null ? "" : side.strip().toLowerCase(Locale.ROOT);
        if (!sideText.equals("view") && !sideText.equals("edit") && !sideText.equals("both")) {
            return Result.failure(
                    Error.validation("Settings.InvalidPermissionSide", "Choose view, edit or both, not '" + side + "'.")
                            .localized("controlcore.errors.settings.invalidpermissionside", "side", side));
        }
        String value = node == null ? "" : node.strip();
        if (value.isEmpty()) {
            return Result.failure(
                    Error.validation("Settings.InvalidPermission", "Give a permission node, everyone or default.")
                            .localized("controlcore.errors.settings.invalidpermission"));
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
            return Result.failure(Error.validation("Settings.InvalidPermission", e.getMessage())
                    .localized("controlcore.errors.settings.invalidpermission_2", "reason", e.getMessage()));
        }
    }

    /** @return the target as stored: lower case with its prefix ({@code homes:max-homes} -> {@code setting:...}) */
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
                        "Name a setting id (homes:max-homes), category:<name> or mod:<mod id>, not '" + target + "'.")
                .localized("controlcore.errors.settings.invalidpermissiontarget", "target", target));
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
