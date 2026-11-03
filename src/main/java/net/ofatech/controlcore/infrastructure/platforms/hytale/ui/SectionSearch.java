package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingSearch;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Translator;

final class SectionSearch {

    private static final String SEARCH = "Search";
    private static final String LEGEND_PREFIX = "Legend:";
    private static final String LEGEND_ALL = "#LegendAll";
    private static final String LAYOUT = ControlPanelPage.LAYOUT;

    interface Host {
        void send(UICommandBuilder update);

        Optional<PanelSection> currentSection();

        boolean inScope(PanelSection key, SettingId id);

        boolean headingInScope(PanelSection key, List<SettingsSection.Entry> entries, int heading);

        SettingRows.State state(PanelSection key, SettingSnapshot snapshot);

        Optional<PermissionsTab> permissionsTab();

        boolean hasTab(ControlPanelTab tab);

        void select(ControlPanelTab tab);

        void showTab(UICommandBuilder update);

        PanelSection keymapSection();
    }

    private final Map<PanelSection, SettingsSection> sections;
    private final Map<PanelSection, SectionState> states;
    private final Translator tr;
    private final Host host;

    SectionSearch(
            Map<PanelSection, SettingsSection> sections,
            Map<PanelSection, SectionState> states,
            Translator tr,
            Host host) {
        this.sections = sections;
        this.states = states;
        this.tr = tr;
        this.host = host;
    }

    SettingSearch of(PanelSection key) {
        return states.get(key).search();
    }

    void build(PanelSection key, UICommandBuilder commands, UIEventBuilder events) {
        SettingSearch current = of(key);
        String filter = key.search + "Filter";
        String match = key.search + "Match";
        commands.set(key.search + ".PlaceholderText", tr.t("controlcore.panel.search_hint"));
        commands.set(key.search + ".Value", current.text());
        commands.set(
                filter + ".Entries",
                Arrays.stream(SettingSearch.Filter.values())
                        .map(option -> dropdownEntry("controlcore.panel.filter.", option.name(), option.label()))
                        .toList());
        commands.set(filter + ".Value", current.filter().label());
        commands.set(
                match + ".Entries",
                Arrays.stream(SettingSearch.Match.values())
                        .map(option -> dropdownEntry("controlcore.panel.match.", option.name(), option.label()))
                        .toList());
        commands.set(match + ".Value", current.match().label());
        EventData search = new EventData()
                .append(PanelAction.KEY, SEARCH)
                .append(PanelAction.SECTION, key.name())
                .append(PanelAction.VALUE, key.search + ".Value")
                .append(PanelAction.FILTER, filter + ".Value")
                .append(PanelAction.MATCH, match + ".Value");
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, key.search, search);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, filter, search);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, match, search);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                key.search + "Defaults",
                new EventData()
                        .append(PanelAction.KEY, SettingsRows.RESTORE_DEFAULTS)
                        .append(PanelAction.SECTION, key.name()));
    }

    void bindLegend(UIEventBuilder events) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating, LEGEND_ALL, EventData.of(PanelAction.KEY, LEGEND_PREFIX));
        for (SettingRows.State state : SettingRows.State.values()) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    state.legendButton(),
                    EventData.of(PanelAction.KEY, LEGEND_PREFIX + state.name()));
        }
    }

    boolean handle(PanelAction action) {
        String name = action.name();
        if (SEARCH.equals(name) && PermissionsTab.SECTION.name().equals(action.section)) {
            host.permissionsTab().ifPresent(tab -> {
                SettingSearch search = SettingSearch.parse(
                        action.value, SettingSearch.Filter.of(action.filter), SettingSearch.Match.of(action.match));
                states.get(PermissionsTab.SECTION).search(search);
                UICommandBuilder update = new UICommandBuilder();
                tab.search(search, update);
                host.send(update);
            });
        } else if (SEARCH.equals(name)) {
            sectionOf(action.section).ifPresent(key -> {
                states.get(key)
                        .search(SettingSearch.parse(
                                action.value,
                                SettingSearch.Filter.of(action.filter),
                                SettingSearch.Match.of(action.match)));
                UICommandBuilder update = new UICommandBuilder();
                apply(key, sections.get(key), update, true);
                showLegend(update);
                host.send(update);
            });
        } else if (name.startsWith(LEGEND_PREFIX)) {
            filterByState(name.substring(LEGEND_PREFIX.length()));
        } else {
            return false;
        }
        return true;
    }

    Optional<PanelSection> sectionOf(String name) {
        for (PanelSection candidate : PanelSection.values()) {
            if (candidate.name().equals(name) && sections.containsKey(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    void showLegend(UICommandBuilder update) {
        Optional<PanelSection> current = host.currentSection();
        update.set("#Legend.Visible", current.isPresent());
        if (current.isEmpty()) {
            return;
        }
        PanelSection key = current.get();
        SettingsSection section = sections.get(key);
        SectionState state = states.get(key);
        SettingSearch search = state.search();
        Map<SettingRows.State, Integer> counts = new EnumMap<>(SettingRows.State.class);
        int total = 0;
        for (SettingsSection.Entry entry : state.entries()) {
            if (entry instanceof SettingsSection.Row row) {
                SettingSnapshot snapshot = state.latestOr(row.snapshot());
                if (host.inScope(key, row.id()) && matchesSearch(section, search, snapshot)) {
                    total++;
                    counts.merge(host.state(key, snapshot), 1, Integer::sum);
                }
            }
        }
        SettingRows.State active = state.legend();
        update.set(LEGEND_ALL + ".Text", " " + tr.t("controlcore.panel.legend.all", "count", total));
        update.set(LEGEND_ALL + ".Style", Value.ref(LAYOUT, active == null ? "LegendOnStyle" : "LegendStyle"));
        for (SettingRows.State legend : SettingRows.State.values()) {
            update.set(
                    legend.legendButton() + ".Text",
                    " "
                            + tr.t(
                                    "controlcore.panel.legend." + legend.name().toLowerCase(Locale.ROOT),
                                    "count",
                                    counts.getOrDefault(legend, 0)));
            update.set(
                    legend.legendButton() + ".Style",
                    Value.ref(LAYOUT, legend == active ? "LegendOnStyle" : "LegendStyle"));
        }
    }

    private void filterByState(String stateName) {
        Optional<PanelSection> current = host.currentSection();
        if (current.isEmpty()) {
            return;
        }
        PanelSection key = current.get();
        SectionState sectionState = states.get(key);
        SettingRows.State state = null;
        for (SettingRows.State candidate : SettingRows.State.values()) {
            if (candidate.name().equals(stateName)) {
                state = candidate;
            }
        }
        if (state == null || state == sectionState.legend()) {
            sectionState.legend(null);
        } else {
            sectionState.legend(state);
        }
        UICommandBuilder update = new UICommandBuilder();
        apply(key, sections.get(key), update, true);
        showLegend(update);
        host.send(update);
    }

    private static boolean matchesSearch(SettingsSection section, SettingSearch search, SettingSnapshot snapshot) {
        return search.matches(
                snapshot, section.name(snapshot), section.category(snapshot), section.description(snapshot));
    }

    Set<String> shownIds(PanelSection key) {
        SettingsSection section = sections.get(key);
        if (section == null) {
            return Set.of();
        }
        SectionState state = states.get(key);
        SettingSearch search = state.search();
        SettingRows.State legend = state.legend();
        Set<String> found = new HashSet<>();
        for (SettingsSection.Entry entry : state.entries()) {
            if (!(entry instanceof SettingsSection.Row row) || row.kind() == SettingsSection.Kind.INFO) {
                continue;
            }
            SettingSnapshot snapshot = state.latestOr(row.snapshot());
            if (!host.inScope(key, row.id())
                    || !matchesSearch(section, search, snapshot)
                    || (legend != null && host.state(key, snapshot) != legend)) {
                continue;
            }
            found.add(row.id().toString());
            row.color().ifPresent(color -> found.add(color.id().toString()));
        }
        return found;
    }

    private DropdownEntryInfo dropdownEntry(String keyPrefix, String option, String value) {
        return new DropdownEntryInfo(
                LocalizableString.fromString(tr.t(keyPrefix + option.toLowerCase(Locale.ROOT))), value);
    }

    void apply(PanelSection key, SettingsSection section, UICommandBuilder commands, boolean report) {
        SectionState state = states.get(key);
        List<SettingsSection.Entry> entries = state.entries();
        SettingSearch search = state.search();
        SettingRows.State legend = state.legend();
        boolean filtered = !search.isEmpty() || legend != null;
        boolean[] visible = new boolean[entries.size()];
        int rows = 0;
        int matching = 0;
        int heading = -1;
        for (int index = 0; index < entries.size(); index++) {
            switch (entries.get(index)) {
                case SettingsSection.Heading ignored -> {
                    heading = index;
                    visible[index] = !filtered && host.headingInScope(key, entries, index);
                }
                case SettingsSection.Row row -> {
                    rows++;
                    SettingSnapshot snapshot = state.latestOr(row.snapshot());
                    visible[index] = host.inScope(key, row.id())
                            && matchesSearch(section, search, snapshot)
                            && (legend == null || host.state(key, snapshot) == legend);
                    if (visible[index]) {
                        matching++;
                        if (heading >= 0) {
                            visible[heading] = true;
                        }
                    }
                }
            }
        }
        for (int index = 0; index < entries.size(); index++) {
            commands.set(key.row(index) + ".Visible", visible[index]);
        }
        if (report) {
            String status;
            if (!filtered) {
                status = "";
            } else if (matching == 0) {
                status = search.text().isEmpty()
                        ? tr.t("controlcore.panel.search.nothing_filtered")
                        : tr.t("controlcore.panel.search.nothing", "text", search.text());
            } else {
                status = tr.t("controlcore.panel.search.shown", "count", matching, "total", rows);
            }
            commands.set(key.status + ".Text", status);
        }
    }

    void focusMod(String namespace) {
        boolean serverSettings = sections.containsKey(PanelSection.SETTINGS)
                && sections.get(PanelSection.SETTINGS).entries().stream()
                        .anyMatch(entry -> entry instanceof SettingsSection.Row row
                                && row.id().namespace().equals(namespace));
        PanelSection section = serverSettings ? PanelSection.SETTINGS : PanelSection.PREFS;
        ControlPanelTab tab = serverSettings ? ControlPanelTab.MOD_SETTINGS : ControlPanelTab.PLAYER_PREFS;
        if (sections.containsKey(section) && host.hasTab(tab)) {
            states.get(section).search(modSearch(namespace));
            host.select(tab);
        }
    }

    void showSettingsOf(ModsView.Entry entry, boolean keys) {
        ControlPanelTab tab;
        PanelSection section;
        if (keys) {
            tab = ControlPanelTab.KEYMAPS;
            section = host.keymapSection();
        } else if (entry.hasServerSettings() && host.hasTab(ControlPanelTab.MOD_SETTINGS)) {
            tab = ControlPanelTab.MOD_SETTINGS;
            section = PanelSection.SETTINGS;
        } else {
            tab = ControlPanelTab.PLAYER_PREFS;
            section = PanelSection.PREFS;
        }
        if (!host.hasTab(tab)
                || !sections.containsKey(section)
                || entry.namespace().isEmpty()) {
            return;
        }
        SettingSearch search = modSearch(entry.namespace().get());
        states.get(section).search(search);
        host.select(tab);
        UICommandBuilder update = new UICommandBuilder();
        update.set(section.search + ".Value", search.text());
        update.set(section.search + "Filter.Value", search.filter().label());
        update.set(section.search + "Match.Value", search.match().label());
        apply(section, sections.get(section), update, true);
        host.showTab(update);
        host.send(update);
    }

    private static SettingSearch modSearch(String namespace) {
        return SettingSearch.parse("mod:" + namespace, SettingSearch.Filter.ALL, SettingSearch.Match.CONTAINS);
    }
}
