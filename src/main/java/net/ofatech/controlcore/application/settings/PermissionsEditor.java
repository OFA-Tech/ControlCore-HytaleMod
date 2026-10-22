package net.ofatech.controlcore.application.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.domain.model.settings.PermissionsView;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

public final class PermissionsEditor {

    private final SettingsRegistry settings;
    private final ServerConfigEditor serverConfig;
    private final SettingPermissionPolicy policy;

    public PermissionsEditor(
            SettingsRegistry settings, ServerConfigEditor serverConfig, SettingPermissionPolicy policy) {
        this.settings = settings;
        this.serverConfig = serverConfig;
        this.policy = policy;
    }

    public PermissionsView viewedBy(Translator tr) {
        return new View(tr);
    }

    private Map<String, List<SettingDefinition<?>>> definitionsByMod() {
        Map<String, List<SettingDefinition<?>>> byMod = new LinkedHashMap<>();
        serverConfig.snapshot(SettingScope.SERVER, Optional.empty()).stream()
                .map(SettingSnapshot::definition)
                .forEach(definition -> add(byMod, definition));
        List<SettingDefinition<?>> registered = new ArrayList<>();
        settings.snapshot(SettingScope.SERVER, Optional.empty()).forEach(s -> registered.add(s.definition()));
        settings.snapshot(SettingScope.PLAYER, Optional.empty()).forEach(s -> registered.add(s.definition()));
        registered.sort(Comparator.comparing((SettingDefinition<?> definition) -> definition.heading())
                .thenComparing(definition -> definition.id().toString()));
        registered.forEach(definition -> add(byMod, definition));
        return byMod;
    }

    private static void add(Map<String, List<SettingDefinition<?>>> byMod, SettingDefinition<?> definition) {
        byMod.computeIfAbsent(definition.id().namespace(), ignored -> new ArrayList<>())
                .add(definition);
    }

    private final class View implements PermissionsView {

        private final Translator tr;

        private View(Translator tr) {
            this.tr = tr;
        }

        @Override
        public List<Entry> list() {
            List<Entry> entries = new ArrayList<>();
            definitionsByMod().forEach((mod, definitions) -> {
                String modLabel = modLabel(mod);
                entries.add(entry(
                        Level.MOD,
                        SettingPermissionPolicy.MOD + mod,
                        modLabel,
                        tr.t("controlcore.permissions.ui.whole_mod"),
                        tr.t("controlcore.permissions.ui.whole_mod_desc", "target", SettingPermissionPolicy.MOD + mod),
                        definitions.getFirst()));
                String category = null;
                for (SettingDefinition<?> definition : definitions) {
                    String heading = heading(modLabel, definition);
                    String own = definition.category();
                    if (!own.isEmpty() && !own.equalsIgnoreCase(category == null ? "" : category)) {
                        String target = SettingPermissionPolicy.CATEGORY + own.toLowerCase(Locale.ROOT);
                        entries.add(entry(
                                Level.CATEGORY,
                                target,
                                heading,
                                tr.t("controlcore.permissions.ui.whole_category"),
                                tr.t(
                                        "controlcore.permissions.ui.whole_category_desc",
                                        "category",
                                        tr.translate(definition.categoryText()),
                                        "target",
                                        target),
                                definition));
                    }
                    category = own;
                    entries.add(entry(
                            Level.SETTING,
                            SettingPermissionPolicy.SETTING + definition.id(),
                            heading,
                            tr.translate(definition.nameText()),
                            settingDescription(definition),
                            definition));
                }
            });
            return entries;
        }

        @Override
        public Result<String> save(String target, String view, String edit) {
            return policy.replaceRule(target, view, edit)
                    .flatMap(rule -> SettingPermissionPolicy.target(target)
                            .map(key -> tr.t(
                                    "controlcore.permissions.saved",
                                    "rule",
                                    SettingPermissionsText.rule(tr, key, rule))));
        }

        @Override
        public Result<String> reset(String target) {
            return save(target, "", "");
        }

        private Entry entry(
                Level level,
                String target,
                String heading,
                String name,
                String description,
                SettingDefinition<?> definition) {
            SettingPermission rule = policy.rule(target);
            return new Entry(
                    level,
                    target,
                    heading,
                    name,
                    description,
                    rule.view().orElse(""),
                    rule.edit().orElse(""),
                    !rule.isInherit(),
                    definition);
        }

        private String settingDescription(SettingDefinition<?> definition) {
            SettingPermissionPolicy.Resolved resolved = policy.resolve(definition);
            SettingScope scope = definition.scope();
            return tr.t(
                    "controlcore.permissions.ui.setting_desc",
                    "scope",
                    tr.t(
                            scope == SettingScope.PLAYER
                                    ? "controlcore.permissions.ui.preference"
                                    : "controlcore.permissions.ui.server_setting"),
                    "id",
                    definition.id(),
                    "see",
                    resolved.view().describe(tr, scope),
                    "change",
                    resolved.edit().describe(tr, scope));
        }

        private String modLabel(String mod) {
            return mod.equals(ServerConfigEditor.NAMESPACE)
                    ? tr.t("controlcore.permissions.ui.server_config")
                    : tr.translate(settings.modName(mod));
        }

        private String heading(String modLabel, SettingDefinition<?> definition) {
            String category = tr.translate(definition.categoryText()).strip();
            return category.isEmpty() ? modLabel : modLabel + ": " + category;
        }
    }
}
