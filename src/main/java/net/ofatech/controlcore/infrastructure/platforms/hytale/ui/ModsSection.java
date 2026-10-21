package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    String badges(ModsView.Entry entry) {
        ModInfo mod = entry.mod();
        List<String> badges = new ArrayList<>();
        switch (mod.kind()) {
            case ASSET_PACK -> badges.add(tr.t("controlcore.panel.mods.badge.asset_pack"));
            case PLUGIN_WITH_ASSETS -> badges.add(tr.t("controlcore.panel.mods.badge.assets"));
            case PLUGIN -> {}
        }
        switch (mod.state()) {
            case DISABLED, NOT_LOADED -> badges.add(tr.t("controlcore.panel.mods.badge.disabled"));
            case FAILED -> badges.add(tr.t("controlcore.panel.mods.badge.failed"));
            case ENABLED -> {}
        }
        if (mod.changesAfterRestart()) {
            badges.add(tr.t(
                    mod.enabledAfterRestart()
                            ? "controlcore.panel.mods.badge.on_after_restart"
                            : "controlcore.panel.mods.badge.off_after_restart"));
        }
        if (mod.core()) {
            badges.add(tr.t("controlcore.panel.mods.badge.hytale"));
        }
        return String.join(" · ", badges);
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
