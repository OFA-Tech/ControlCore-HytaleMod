package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.mods.ModDependency;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;

final class ModsSection {

    enum Filter {
        MODS,
        ENABLED,
        DISABLED,
        WITH_SETTINGS,
        PROBLEMS,
        ASSET_PACKS,
        EVERYTHING;

        static Filter of(String name) {
            for (Filter filter : values()) {
                if (filter.name().equalsIgnoreCase(name == null ? "" : name.strip())) {
                    return filter;
                }
            }
            return MODS;
        }

        String translationKey() {
            return "controlcore.panel.mods_filter." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final ModsView view;
    private final Translator tr;
    private List<ModsView.Entry> entries = List.of();
    private Filter filter = Filter.MODS;
    private String query = "";

    ModsSection(ModsView view, Translator tr) {
        this.view = view;
        this.tr = tr;
    }

    List<ModsView.Entry> load() {
        entries = view.list();
        return entries;
    }

    List<ModsView.Entry> entries() {
        return entries;
    }

    ModsView view() {
        return view;
    }

    Filter filter() {
        return filter;
    }

    String query() {
        return query;
    }

    void search(String text, Filter newFilter) {
        query = text == null ? "" : text.strip();
        filter = newFilter;
    }

    boolean shown(ModsView.Entry entry) {
        ModInfo mod = entry.mod();
        boolean byFilter = switch (filter) {
            case MODS -> !mod.core();
            case ENABLED -> !mod.core() && mod.running();
            case DISABLED -> !mod.core() && !mod.running();
            case WITH_SETTINGS -> entry.hasServerSettings() || entry.hasPreferences() || entry.hasKeys();
            case PROBLEMS -> entry.hasProblems();
            case ASSET_PACKS -> mod.hasAssets();
            case EVERYTHING -> true;
        };
        if (!byFilter) {
            return false;
        }
        String wanted = query.toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return true;
        }
        for (String word : wanted.split("\\s+")) {
            String haystack = String.join(
                            " ",
                            mod.name(),
                            mod.id(),
                            mod.description(),
                            String.join(" ", mod.authors()),
                            mod.version(),
                            entry.namespace().orElse(""))
                    .toLowerCase(Locale.ROOT);
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    int shownCount() {
        return (int) entries.stream().filter(this::shown).count();
    }

    String version(ModInfo mod) {
        return mod.version().isEmpty() ? "" : "v" + mod.version();
    }

    record Badges(
            boolean code,
            boolean assets,
            String state,
            String stateText,
            boolean hytale,
            int missing,
            int wrongVersion,
            int neededBy) {}

    Badges badges(ModsView.Entry entry) {
        ModInfo mod = entry.mod();
        boolean code = mod.kind() != ModInfo.Kind.ASSET_PACK;
        boolean assets = mod.kind() != ModInfo.Kind.PLUGIN;
        String state = "";
        String stateText = "";
        switch (mod.state()) {
            case DISABLED, NOT_LOADED -> {
                state = "Off";
                stateText = tr.t("controlcore.panel.mods.badge.disabled");
            }
            case FAILED -> {
                state = "Failed";
                stateText = tr.t("controlcore.panel.mods.badge.failed");
            }
            case ENABLED -> {}
        }
        if (mod.changesAfterRestart()) {
            state = "Restart";
            stateText = tr.t(
                    mod.enabledAfterRestart()
                            ? "controlcore.panel.mods.badge.on_after_restart"
                            : "controlcore.panel.mods.badge.off_after_restart");
        }
        int missing = 0;
        int wrong = 0;
        for (ModDependency dependency : mod.required()) {
            switch (dependency.status()) {
                case MISSING, OFF -> missing++;
                case WRONG_VERSION -> wrong++;
                case OK -> {}
            }
        }
        if (!mod.serverVersionOk()) {
            wrong++;
        }
        return new Badges(
                code,
                assets,
                state,
                stateText,
                mod.core(),
                missing,
                wrong,
                entry.neededBy().size());
    }

    String needs(ModsView.Entry entry) {
        ModInfo mod = entry.mod();
        List<String> required = new ArrayList<>();
        List<String> optional = new ArrayList<>();
        for (ModDependency dependency : mod.dependencies()) {
            if (dependency.hytale() && dependency.status() == ModDependency.Status.OK) {
                continue;
            }
            String text = dependency.id() + (dependency.range().isEmpty() ? "" : " " + dependency.range());
            (dependency.optional() ? optional : required).add(text);
        }
        List<String> parts = new ArrayList<>();
        if (!required.isEmpty()) {
            parts.add(tr.t("controlcore.panel.mods.requires", "mods", String.join(", ", required)));
        }
        if (!optional.isEmpty()) {
            parts.add(tr.t("controlcore.panel.mods.optional", "mods", String.join(", ", optional)));
        }
        if (!mod.serverVersion().isEmpty()) {
            parts.add(tr.t("controlcore.panel.mods.hytale_version", "range", mod.serverVersion()));
        }
        if (!entry.neededBy().isEmpty()) {
            parts.add(tr.t("controlcore.panel.mods.needed_by", "mods", String.join(", ", entry.neededBy())));
        }
        return String.join(" · ", parts);
    }

    String problems(ModsView.Entry entry) {
        ModInfo mod = entry.mod();
        List<String> lines = new ArrayList<>();
        for (ModDependency dependency : mod.required()) {
            switch (dependency.status()) {
                case MISSING -> lines.add(tr.t("controlcore.panel.mods.problem.missing", "id", dependency.id()));
                case OFF -> lines.add(tr.t("controlcore.panel.mods.problem.off", "id", dependency.id()));
                case WRONG_VERSION ->
                    lines.add(tr.t(
                            "controlcore.panel.mods.problem.version",
                            "id",
                            dependency.id(),
                            "range",
                            dependency.range()));
                case OK -> {}
            }
        }
        if (!mod.serverVersionOk()) {
            lines.add(tr.t("controlcore.panel.mods.problem.hytale", "range", mod.serverVersion()));
        }
        return String.join("\n", lines);
    }

    String description(ModInfo mod) {
        return mod.description().isEmpty() ? tr.t("controlcore.panel.mods.no_description") : mod.description();
    }

    String details(ModInfo mod) {
        List<String> parts = new ArrayList<>();
        if (!mod.authors().isEmpty()) {
            parts.add(tr.t("controlcore.panel.mods.by", "authors", String.join(", ", mod.authors())));
        }
        if (!mod.website().isEmpty()) {
            parts.add(mod.website());
        }
        parts.add(mod.id());
        return String.join(" · ", parts);
    }

    String configText(String modId) {
        List<ModCatalog.ConfigFile> files = view.configFiles(modId);
        if (files.isEmpty()) {
            return tr.t("controlcore.panel.mods.no_config");
        }
        StringBuilder text = new StringBuilder(tr.t("controlcore.panel.mods.config_read_only"));
        for (ModCatalog.ConfigFile file : files) {
            text.append("\n\n").append(file.name()).append(':');
            for (String line : file.lines()) {
                text.append("\n  ").append(line);
            }
        }
        return text.toString();
    }

    Optional<ModsView.Entry> find(String modId) {
        return entries.stream()
                .filter(entry -> entry.mod().id().equalsIgnoreCase(modId == null ? "" : modId))
                .findFirst();
    }
}
