package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBindEditor;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSearch;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

final class ControlPanelPage extends InteractiveCustomUIPage<PanelAction> {

    static final String LAYOUT = "Pages/ControlCore/ControlPanel.ui";

    private static final String CLOSE = "Close";
    private static final String TAB_PREFIX = "Tab:";
    private static final String HISTORY_LINK = "HistoryOf";
    private static final String HISTORY_MODE_CHANGES = "HistoryModeChanges";
    private static final String HISTORY_MODE_SNAPSHOTS = "HistoryModeSnapshots";
    private static final String HISTORY_MODE_PROFILES = "HistoryModeProfiles";
    private static final String RESTORE_DEFAULTS = "RestoreDefaults";
    private static final String SEARCH = "Search";
    private static final String KEYMAP_MODE_SERVER = "KeymapMode:Server";
    private static final String KEYMAP_MODE_MINE = "KeymapMode:Mine";
    private static final String CUSTOM_NAMESPACE = "custom";
    private static final String PROBE_TOGGLE = "ProbeToggle";
    private static final String KEY_TEST = "KeyTest";
    private static final String LEGEND_PREFIX = "Legend:";
    private static final String LEGEND_ALL = "#LegendAll";
    private static final String SERVER_MODE_CONFIG = "ServerMode:Config";
    private static final String SERVER_MODE_WORLDS = "ServerMode:Worlds";
    private static final String SERVER_MODE_BANS = "ServerMode:Bans";
    private static final String SERVER_WORLD = "ServerWorld";

    private enum Tab {
        SERVER(ControlPanelTab.SERVER_CONFIGS, "#ServerTab", "#ServerTabButton"),
        SETTINGS(ControlPanelTab.MOD_SETTINGS, "#SettingsTab", "#SettingsTabButton"),
        PLAYER(ControlPanelTab.PLAYER_PREFS, "#PlayerTab", "#PlayerTabButton"),
        KEYMAP(ControlPanelTab.KEYMAPS, "#KeymapTab", "#KeymapTabButton"),
        MODS(ControlPanelTab.MODS, "#ModsTab", "#ModsTabButton"),
        PERMISSIONS(ControlPanelTab.PERMISSIONS, "#PermissionsTab", "#PermissionsTabButton"),
        HISTORY(ControlPanelTab.HISTORY, "#HistoryTab", "#HistoryTabButton"),
        HEALTH(ControlPanelTab.HEALTH, "#HealthTab", "#HealthTabButton"),
        AUTOMATION(ControlPanelTab.AUTOMATION, "#AutomationTab", "#AutomationTabButton");

        private final ControlPanelTab tab;
        private final String group;
        private final String button;

        Tab(ControlPanelTab tab, String group, String button) {
            this.tab = tab;
            this.group = group;
            this.button = button;
        }

        static Tab of(ControlPanelTab tab) {
            for (Tab candidate : values()) {
                if (candidate.tab == tab) {
                    return candidate;
                }
            }
            return SERVER;
        }

        String slot() {
            return button.replace("Button", "Slot");
        }

        String edge() {
            return button.replace("Button", "Edge");
        }

        boolean shownFor(ControlPanelRequest request) {
            return switch (this) {
                case SERVER, SETTINGS, PERMISSIONS, HISTORY, HEALTH, AUTOMATION -> request.includeServerSettings();
                case PLAYER -> request.includePlayerPreferences();
                case KEYMAP -> request.includeRegisteredActions();
                case MODS -> true;
            };
        }
    }

    private final Log log;
    private final Translator tr;
    private final CustomKeyBindEditor customKeyBinds;
    private final Supplier<List<Text>> pendingRestart;
    private final Set<Tab> tabs = EnumSet.noneOf(Tab.class);
    private final Map<PanelSection, SettingsSection> sections = new EnumMap<>(PanelSection.class);
    private final Map<PanelSection, Map<SettingId, Integer>> rowIndexes = new EnumMap<>(PanelSection.class);
    private final Set<String> inlineColors = new HashSet<>();
    private final Map<String, String> shown = new HashMap<>();
    private final Map<String, SettingSnapshot> latest = new HashMap<>();
    private final Map<PanelSection, List<SettingsSection.Entry>> built = new EnumMap<>(PanelSection.class);
    private final Map<PanelSection, SettingSearch> searches = new EnumMap<>(PanelSection.class);
    private final Map<String, String> errors = new HashMap<>();
    private final Set<String> unsaved = new HashSet<>();
    private final Map<String, String> undo = new HashMap<>();
    private final Set<String> undoRows = new HashSet<>();
    private Set<String> restartNames = Set.of();
    private final Map<PanelSection, SettingRows.State> legendFilters = new EnumMap<>(PanelSection.class);

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ModsTab modsTab;
    private final Optional<KeyBindForm> bindForm;
    private final Optional<PermissionsTab> permissionsTab;
    private final Optional<HistoryTab> historyTab;
    private final Optional<SnapshotsTab> snapshotsTab;
    private final Optional<ProfilesTab> profilesTab;
    private final Optional<HealthTab> healthTab;
    private final Optional<AutomationTab> automationTab;
    private final Optional<SnapshotsView> snapshotsView;
    private HistoryMode historyMode = HistoryMode.CHANGES;
    private Optional<PanelSection> armedDefaults = Optional.empty();

    private enum HistoryMode {
        CHANGES,
        SNAPSHOTS,
        PROFILES
    }

    private final Optional<InputProbe> probe;
    private final PlayerId viewerId;
    private final String viewerName;

    private Tab selected;
    private final List<String> worlds;
    private boolean showingWorlds;
    private boolean showingBans;
    private final Optional<BansTab> bansTab;
    private boolean replied;
    private String world;
    private boolean editingServerKeys = true;

    ControlPanelPage(PlayerRef player, ControlPanelRequest request, ControlPanelContent content, Log log) {
        super(player, CustomPageLifetime.CanDismiss, PanelAction.CODEC);
        this.log = log;
        this.tr = content.translator();
        this.pendingRestart = content.pendingRestart();
        Hooks hooks = new Hooks();
        this.modsTab = new ModsTab(content.mods(), tr, hooks);
        ControlPanelRequest shownSections = request.isEmpty() ? ControlPanelRequest.PLAYER_PREFERENCES : request;
        this.customKeyBinds = content.customKeyBinds();
        boolean manageKeys = content.manageKeys() && shownSections.includeRegisteredActions();
        this.bindForm =
                manageKeys ? Optional.of(new KeyBindForm(content.customKeyBinds(), tr, hooks)) : Optional.empty();
        this.permissionsTab = shownSections.includeServerSettings()
                ? content.permissions().map(view -> new PermissionsTab(view, tr, hooks))
                : Optional.empty();
        this.historyTab = shownSections.includeServerSettings()
                ? content.history().map(view -> new HistoryTab(view, tr, hooks))
                : Optional.empty();
        this.snapshotsTab = shownSections.includeServerSettings()
                ? content.snapshots().map(view -> new SnapshotsTab(view, tr, hooks))
                : Optional.empty();
        this.profilesTab = shownSections.includeServerSettings()
                ? content.profiles().map(view -> new ProfilesTab(view, tr, hooks))
                : Optional.empty();
        this.healthTab = shownSections.includeServerSettings()
                ? content.diagnostics().map(view -> new HealthTab(view, tr, hooks))
                : Optional.empty();
        this.snapshotsView = shownSections.includeServerSettings() ? content.snapshots() : Optional.empty();
        this.automationTab = shownSections.includeServerSettings()
                ? content.automation().map(view -> new AutomationTab(view, tr, hooks))
                : Optional.empty();
        this.historyMode = historyTab.isPresent() ? HistoryMode.CHANGES : HistoryMode.SNAPSHOTS;
        this.bansTab = shownSections.includeServerSettings()
                ? content.bans().map(view -> new BansTab(view, tr, hooks))
                : Optional.empty();
        this.probe = manageKeys ? content.debug() : Optional.empty();
        this.viewerId = PlayerId.of(player.getUuid());
        this.viewerName = player.getUsername();
        this.worlds = content.worlds();
        this.world = worlds.contains("default")
                ? "default"
                : worlds.stream().findFirst().orElse("");
        for (Tab tab : Tab.values()) {
            if (tab.shownFor(shownSections)
                    && (tab != Tab.PERMISSIONS || permissionsTab.isPresent())
                    && (tab != Tab.HISTORY || historyTab.isPresent() || snapshotsTab.isPresent())
                    && (tab != Tab.HEALTH || healthTab.isPresent())
                    && (tab != Tab.AUTOMATION || automationTab.isPresent())) {
                tabs.add(tab);
            }
        }
        Tab start = Tab.of(content.startTab());
        this.selected = tabs.contains(start) ? start : tabs.iterator().next();
        Optional<PlayerId> viewer = Optional.of(PlayerId.of(player.getUuid()));
        if (shownSections.includeServerSettings()) {
            sections.put(
                    PanelSection.SERVER_CONFIG,
                    new SettingsSection(
                            content.serverConfig(),
                            SettingScope.SERVER,
                            viewer,
                            tr.t("controlcore.panel.empty.server_config"),
                            tr));
            sections.put(
                    PanelSection.SETTINGS,
                    new SettingsSection(
                            content.settings(),
                            SettingScope.SERVER,
                            viewer,
                            tr.t("controlcore.panel.empty.settings"),
                            tr));
        }
        if (shownSections.includePlayerPreferences()) {
            sections.put(
                    PanelSection.PREFS,
                    new SettingsSection(
                            content.settings(),
                            SettingScope.PLAYER,
                            viewer,
                            tr.t("controlcore.panel.empty.prefs"),
                            tr));
        }
        if (shownSections.includeRegisteredActions()) {
            sections.put(
                    PanelSection.KEYMAPS,
                    SettingsSection.keymaps(
                            content.keymaps(),
                            SettingScope.PLAYER,
                            viewer,
                            tr.t("controlcore.panel.empty.keymaps"),
                            tr));
        }
        if (manageKeys) {
            sections.put(
                    PanelSection.SERVER_KEYMAPS,
                    SettingsSection.keymaps(
                            content.keymaps(),
                            SettingScope.SERVER,
                            viewer,
                            tr.t("controlcore.panel.empty.server_keymaps"),
                            tr));
        }
        content.focus().ifPresent(this::focusMod);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        showRestartNotice(commands);
        sections.forEach((key, section) -> {
            buildSection(key, section, commands, events);
            buildSearch(key, commands, events);
            applySearch(key, section, commands, false);
        });
        modsTab.build(
                commands, events, tabs.contains(Tab.SETTINGS), tabs.contains(Tab.PLAYER), tabs.contains(Tab.KEYMAP));
        permissionsTab.ifPresent(tab -> {
            tab.build(commands, events, searches.getOrDefault(PermissionsTab.SECTION, SettingSearch.ALL));
            buildSearch(PermissionsTab.SECTION, commands, events);
        });
        historyTab.ifPresent(tab -> tab.build(commands, events));
        snapshotsTab.ifPresent(tab -> tab.build(commands, events));
        profilesTab.ifPresent(tab -> tab.build(commands, events));
        healthTab.ifPresent(tab -> tab.build(commands, events));
        automationTab.ifPresent(tab -> tab.build(commands, events));
        bansTab.ifPresent(tab -> tab.build(commands, events));
        armedDefaults = Optional.empty();
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#HistoryModeProfiles",
                EventData.of(PanelAction.KEY, HISTORY_MODE_PROFILES));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#HistoryModeChanges",
                EventData.of(PanelAction.KEY, HISTORY_MODE_CHANGES));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#HistoryModeSnapshots",
                EventData.of(PanelAction.KEY, HISTORY_MODE_SNAPSHOTS));
        showHistoryMode(commands);
        buildServerMode(commands, events);
        buildDebugTools(commands, events);
        commands.set("#TabButtons.Visible", tabs.size() > 1);
        bindForm.ifPresent(form -> {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#KeymapModeServer",
                    EventData.of(PanelAction.KEY, KEYMAP_MODE_SERVER));
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#KeymapModeMine",
                    EventData.of(PanelAction.KEY, KEYMAP_MODE_MINE));
            form.build(commands, events);
        });
        showTab(commands);
        showKeymapMode(commands);

        for (Tab tab : tabs) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    tab.button,
                    EventData.of(PanelAction.KEY, TAB_PREFIX + tab.name()));
        }
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#CloseButton", EventData.of(PanelAction.KEY, CLOSE));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, LEGEND_ALL, EventData.of(PanelAction.KEY, LEGEND_PREFIX));
        for (SettingRows.State state : SettingRows.State.values()) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    state.legendButton(),
                    EventData.of(PanelAction.KEY, LEGEND_PREFIX + state.name()));
        }
        if (subscriptions.isEmpty()) {
            sections.forEach((key, section) ->
                    subscriptions.add(section.listen(change -> onSettingChanged(key, section, change))));
            bansTab.ifPresent(tab -> subscriptions.add(tab.view().onChange(this::onBansChanged)));
        }
    }

    private void buildSection(
            PanelSection key, SettingsSection section, UICommandBuilder commands, UIEventBuilder events) {
        Map<SettingId, Integer> indexes = new HashMap<>();
        rowIndexes.put(key, indexes);
        inlineColors.removeIf(shownKey -> shownKey.startsWith(key.name() + "|"));
        commands.set(key.status + ".Text", "");
        List<SettingsSection.Entry> entries = section.entries();
        built.put(key, entries);
        for (int index = 0; index < entries.size(); index++) {
            String element = key.row(index);
            switch (entries.get(index)) {
                case SettingsSection.Heading heading ->
                    SettingRows.heading(key, element, heading.text(), headingCount(entries, index), commands);
                case SettingsSection.Row row -> {
                    indexes.put(row.id(), index);
                    remember(key, row.snapshot());
                    int rowIndex = index;
                    row.color().ifPresent(color -> {
                        indexes.put(color.id(), rowIndex);
                        remember(key, color);
                        inlineColors.add(shownKey(key, color.id()));
                    });
                    boolean customBind = bindForm.isPresent()
                            && key == PanelSection.SERVER_KEYMAPS
                            && CUSTOM_NAMESPACE.equals(row.id().namespace());
                    Optional<GameActionMode> gameAction = bindForm.isPresent() && key == PanelSection.SERVER_KEYMAPS
                            ? Optional.of(customKeyBinds.gameAction(row.id()))
                            : Optional.empty();
                    SettingRows.build(key, section, row, element, customBind, gameAction, commands, events);
                    showHistoryLink(key, element, row.id(), commands, events);
                    if (SettingRows.canUndo(row)) {
                        undoRows.add(shownKey(key, row.id()));
                    }
                    showState(key, index, latest.getOrDefault(shownKey(key, row.id()), row.snapshot()), commands);
                }
            }
        }
    }

    private void showHistoryLink(
            PanelSection key, String element, SettingId id, UICommandBuilder commands, UIEventBuilder events) {
        boolean serverWide =
                key == PanelSection.SERVER_CONFIG || key == PanelSection.SETTINGS || key == PanelSection.SERVER_KEYMAPS;
        if (!serverWide || !historyTab.map(tab -> tab.has(id.toString())).orElse(false)) {
            return;
        }
        commands.set(element + " #History.Visible", true);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                element + " #History",
                new EventData().append(PanelAction.KEY, HISTORY_LINK).append(PanelAction.ID, id.toString()));
    }

    private void showHistoryMode(UICommandBuilder commands) {
        int modes = (historyTab.isPresent() ? 1 : 0)
                + (snapshotsTab.isPresent() ? 1 : 0)
                + (profilesTab.isPresent() ? 1 : 0);
        boolean several = modes > 1;
        commands.set("#HistoryModeButtons.Visible", several);
        commands.set("#HistoryModeChanges.Visible", historyTab.isPresent());
        commands.set("#HistoryModeSnapshots.Visible", snapshotsTab.isPresent());
        commands.set("#HistoryModeProfiles.Visible", profilesTab.isPresent());
        commands.set("#HistoryChanges.Visible", historyMode == HistoryMode.CHANGES);
        commands.set("#SnapshotsPanel.Visible", historyMode == HistoryMode.SNAPSHOTS);
        commands.set("#ProfilesPanel.Visible", historyMode == HistoryMode.PROFILES);
        commands.set("#HistoryModeChanges.Style", segment(historyMode == HistoryMode.CHANGES));
        commands.set("#HistoryModeSnapshots.Style", segment(historyMode == HistoryMode.SNAPSHOTS));
        commands.set("#HistoryModeProfiles.Style", segment(historyMode == HistoryMode.PROFILES));
        commands.setObject("#HistoryList.Anchor", height(several ? 538 : 580));
    }

    private static Value<String> segment(boolean on) {
        return Value.ref(LAYOUT, on ? "SegmentOnStyle" : "SegmentStyle");
    }

    private void buildServerMode(UICommandBuilder commands, UIEventBuilder events) {
        commands.set(
                "#ServerModeButtons.Visible",
                sections.containsKey(PanelSection.SERVER_CONFIG) && (!worlds.isEmpty() || bansTab.isPresent()));
        commands.set("#ServerModeWorlds.Visible", !worlds.isEmpty());
        commands.set("#ServerModeBans.Visible", bansTab.isPresent());
        commands.set(
                "#ServerWorld.Entries",
                worlds.stream()
                        .map(name -> new DropdownEntryInfo(LocalizableString.fromString(name), name))
                        .toList());
        commands.set("#ServerWorld.Value", world);
        if (!worlds.isEmpty() || bansTab.isPresent()) {
            commands.setObject("#ServerList.Anchor", height(540));
        }
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ServerModeBans",
                EventData.of(PanelAction.KEY, SERVER_MODE_BANS));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ServerModeConfig",
                EventData.of(PanelAction.KEY, SERVER_MODE_CONFIG));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ServerModeWorlds",
                EventData.of(PanelAction.KEY, SERVER_MODE_WORLDS));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#ServerWorld",
                new EventData().append(PanelAction.KEY, SERVER_WORLD).append(PanelAction.VALUE, "#ServerWorld.Value"));
        showServerMode(commands);
    }

    private void showServerMode(UICommandBuilder commands) {
        boolean config = !showingWorlds && !showingBans;
        commands.set("#ServerModeConfig.Style", Value.ref(LAYOUT, config ? "SegmentOnStyle" : "SegmentStyle"));
        commands.set("#ServerModeWorlds.Style", Value.ref(LAYOUT, showingWorlds ? "SegmentOnStyle" : "SegmentStyle"));
        commands.set("#ServerModeBans.Style", Value.ref(LAYOUT, showingBans ? "SegmentOnStyle" : "SegmentStyle"));
        commands.set("#ServerWorld.Visible", showingWorlds);
        commands.set("#ServerIntro.Visible", config);
        commands.set("#WorldsIntro.Visible", showingWorlds);
        commands.set("#ServerRows.Visible", !showingBans);
        commands.set("#BansPanel.Visible", showingBans);
    }

    private void refreshServerScope() {
        UICommandBuilder update = new UICommandBuilder();
        showServerMode(update);
        SettingsSection section = sections.get(PanelSection.SERVER_CONFIG);
        if (section != null) {
            applySearch(PanelSection.SERVER_CONFIG, section, update, true);
        }
        showLegend(update);
        sendUpdate(update, false);
    }

    private boolean inScope(PanelSection key, SettingId id) {
        if (key != PanelSection.SERVER_CONFIG) {
            return true;
        }
        if (!showingWorlds) {
            return !ServerConfigEditor.isWorld(id);
        }
        return ServerConfigEditor.isWorld(id) && id.key().startsWith(ServerConfigEditor.worldPrefix(world));
    }

    private boolean headingInScope(PanelSection key, List<SettingsSection.Entry> entries, int heading) {
        for (int index = heading + 1; index < entries.size(); index++) {
            if (entries.get(index) instanceof SettingsSection.Row row) {
                return inScope(key, row.id());
            }
            return false;
        }
        return false;
    }

    private void showHistoryOf(String target) {
        if (historyTab.isEmpty() || !tabs.contains(Tab.HISTORY) || target == null) {
            return;
        }
        HistoryTab tab = historyTab.get();
        selected = Tab.HISTORY;
        historyMode = HistoryMode.CHANGES;
        if (tab.stale()) {
            rebuild();
        }
        UICommandBuilder update = new UICommandBuilder();
        tab.focus(target, update);
        showHistoryMode(update);
        showTab(update);
        sendUpdate(update, false);
    }

    private String headingCount(List<SettingsSection.Entry> entries, int heading) {
        int count = 0;
        for (int index = heading + 1; index < entries.size(); index++) {
            if (entries.get(index) instanceof SettingsSection.Heading) {
                break;
            }
            count++;
        }
        return count == 1
                ? tr.t("controlcore.panel.heading_count_one")
                : tr.t("controlcore.panel.heading_count", "count", count);
    }

    private SettingRows.State state(PanelSection key, SettingSnapshot snapshot) {
        String shownKey = shownKey(key, snapshot.id());
        if (snapshot.access() == SettingAccess.READ_ONLY) {
            return SettingRows.State.LOCKED;
        }
        if (snapshot.access() == SettingAccess.APPLIES_AFTER_RESTART
                && restartNames.contains(sections.get(key).name(snapshot))) {
            return SettingRows.State.RESTART;
        }
        if (errors.containsKey(shownKey)) {
            return SettingRows.State.ERROR;
        }
        if (unsaved.contains(shownKey)) {
            return SettingRows.State.UNSAVED;
        }
        return snapshot.value().equals(snapshot.definition().formattedDefault())
                ? SettingRows.State.DEFAULT
                : SettingRows.State.CHANGED;
    }

    private void showState(PanelSection key, int index, SettingSnapshot snapshot, UICommandBuilder update) {
        String shownKey = shownKey(key, snapshot.id());
        String row = key.row(index);
        String error = errors.get(shownKey);
        update.set(row + " #State.Background", state(key, snapshot).icon());
        update.set(row + " #Error.Visible", error != null);
        update.set(row + " #Error.Text", error == null ? "" : error);
        if (undoRows.contains(shownKey)) {
            update.set(row + " #Undo.Visible", undo.containsKey(shownKey));
        }
    }

    private Optional<PanelSection> currentSection() {
        PanelSection key = switch (selected) {
            case SERVER -> showingBans ? null : PanelSection.SERVER_CONFIG;
            case SETTINGS -> PanelSection.SETTINGS;
            case PLAYER -> PanelSection.PREFS;
            case KEYMAP ->
                editingServerKeys && bindForm.isPresent() ? PanelSection.SERVER_KEYMAPS : PanelSection.KEYMAPS;
            case MODS, PERMISSIONS, HISTORY, HEALTH, AUTOMATION -> null;
        };
        return key != null && sections.containsKey(key) ? Optional.of(key) : Optional.empty();
    }

    private void showLegend(UICommandBuilder update) {
        Optional<PanelSection> current = currentSection();
        update.set("#Legend.Visible", current.isPresent());
        if (current.isEmpty()) {
            return;
        }
        PanelSection key = current.get();
        SettingsSection section = sections.get(key);
        SettingSearch search = searches.getOrDefault(key, SettingSearch.ALL);
        Map<SettingRows.State, Integer> counts = new EnumMap<>(SettingRows.State.class);
        int total = 0;
        for (SettingsSection.Entry entry : built.getOrDefault(key, List.of())) {
            if (entry instanceof SettingsSection.Row row) {
                SettingSnapshot snapshot = latest.getOrDefault(shownKey(key, row.id()), row.snapshot());
                if (inScope(key, row.id()) && matchesSearch(section, search, snapshot)) {
                    total++;
                    counts.merge(state(key, snapshot), 1, Integer::sum);
                }
            }
        }
        SettingRows.State active = legendFilters.get(key);
        update.set(LEGEND_ALL + ".Text", " " + tr.t("controlcore.panel.legend.all", "count", total));
        update.set(LEGEND_ALL + ".Style", Value.ref(LAYOUT, active == null ? "LegendOnStyle" : "LegendStyle"));
        for (SettingRows.State state : SettingRows.State.values()) {
            update.set(
                    state.legendButton() + ".Text",
                    " "
                            + tr.t(
                                    "controlcore.panel.legend." + state.name().toLowerCase(Locale.ROOT),
                                    "count",
                                    counts.getOrDefault(state, 0)));
            update.set(
                    state.legendButton() + ".Style",
                    Value.ref(LAYOUT, state == active ? "LegendOnStyle" : "LegendStyle"));
        }
    }

    private void filterByState(String stateName) {
        Optional<PanelSection> current = currentSection();
        if (current.isEmpty()) {
            return;
        }
        PanelSection key = current.get();
        SettingRows.State state = null;
        for (SettingRows.State candidate : SettingRows.State.values()) {
            if (candidate.name().equals(stateName)) {
                state = candidate;
            }
        }
        if (state == null || state == legendFilters.get(key)) {
            legendFilters.remove(key);
        } else {
            legendFilters.put(key, state);
        }
        UICommandBuilder update = new UICommandBuilder();
        applySearch(key, sections.get(key), update, true);
        showLegend(update);
        sendUpdate(update, false);
    }

    private static boolean matchesSearch(SettingsSection section, SettingSearch search, SettingSnapshot snapshot) {
        return search.matches(
                snapshot, section.name(snapshot), section.category(snapshot), section.description(snapshot));
    }

    private static void footer(UICommandBuilder update, String text) {
        update.set("#FooterStatus.Text", text);
    }

    private void typed(PanelSection key, PanelAction action) {
        SettingSnapshot snapshot = latestOf(key, action.id);
        Integer index = indexOf(key, action.id);
        if (snapshot == null || index == null) {
            return;
        }
        String shownKey = shownKey(key, snapshot.id());
        boolean changed = !snapshot.value().equals(action.value == null ? "" : action.value);
        if (changed == unsaved.contains(shownKey)) {
            return;
        }
        if (changed) {
            unsaved.add(shownKey);
        } else {
            unsaved.remove(shownKey);
        }
        UICommandBuilder update = new UICommandBuilder();
        showState(key, index, snapshot, update);
        showLegend(update);
        sendUpdate(update, false);
    }

    private void undoChange(PanelSection key, SettingsSection section, PanelAction action) {
        SettingSnapshot current = latestOf(key, action.id);
        if (current == null) {
            return;
        }
        String shownKey = shownKey(key, current.id());
        String previous = undo.remove(shownKey);
        if (previous == null) {
            return;
        }
        Result<SettingSnapshot> result = section.save(action.id, previous);
        UICommandBuilder update = new UICommandBuilder();
        if (key == PanelSection.SERVER_CONFIG) {
            showRestartNotice(update);
        }
        Integer index = indexOf(key, action.id);
        if (result.isSuccess()) {
            SettingSnapshot snapshot = result.value();
            remember(key, snapshot);
            errors.remove(shownKey);
            unsaved.remove(shownKey);
            footer(
                    update,
                    tr.t(
                            "controlcore.panel.undone",
                            "name",
                            section.name(snapshot),
                            "value",
                            section.shownValue(snapshot)));
            if (SettingsSection.kindOf(snapshot) == SettingsSection.Kind.LIST) {
                rebuild();
            } else if (index != null) {
                showRowValue(key, section, index, snapshot, update);
                showState(key, index, snapshot, update);
            }
        } else {
            undo.put(shownKey, previous);
            errors.put(shownKey, tr.error(result.error()));
            footer(update, tr.error(result.error()));
            if (index != null) {
                showState(key, index, current, update);
            }
        }
        showLegend(update);
        sendUpdate(update, false);
    }

    private void failed(PanelSection key, String idText, String message, UICommandBuilder update) {
        footer(update, message);
        SettingSnapshot snapshot = latestOf(key, idText);
        Integer index = indexOf(key, idText);
        if (snapshot != null && index != null) {
            errors.put(shownKey(key, snapshot.id()), message);
            showState(key, index, snapshot, update);
        }
        showLegend(update);
    }

    private void succeeded(
            PanelSection key,
            SettingSnapshot before,
            SettingSnapshot after,
            boolean keepUndo,
            UICommandBuilder update) {
        String shownKey = shownKey(key, after.id());
        errors.remove(shownKey);
        unsaved.remove(shownKey);
        if (keepUndo && before != null && !before.value().equals(after.value())) {
            undo.put(shownKey, before.value());
        }
        remember(key, after);
        Integer index = rowIndexes.getOrDefault(key, Map.of()).get(after.id());
        if (index != null) {
            showState(key, index, after, update);
        }
        showLegend(update);
        if (key == PanelSection.SERVER_CONFIG && ServerConfigEditor.isWorld(after.id())) {
            refreshWorldStatus(after.id());
        }
    }

    private void refreshWorldStatus(SettingId changed) {
        String path = changed.key();
        int dot = path.indexOf('.');
        SettingsSection section = sections.get(PanelSection.SERVER_CONFIG);
        Ref<EntityStore> ref = playerRef.getReference();
        if (dot < 0 || section == null || ref == null || !ref.isValid()) {
            return;
        }
        SettingId status = new SettingId(ServerConfigEditor.WORLD_NAMESPACE, path.substring(0, dot + 1) + "status");
        Store<EntityStore> store = ref.getStore();
        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
                .execute(() -> store.getExternalData()
                        .getWorld()
                        .execute(() -> refresh(ref, store, PanelSection.SERVER_CONFIG, section, status)));
    }

    private void buildSearch(PanelSection key, UICommandBuilder commands, UIEventBuilder events) {
        SettingSearch current = searches.getOrDefault(key, SettingSearch.ALL);
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
                new EventData().append(PanelAction.KEY, RESTORE_DEFAULTS).append(PanelAction.SECTION, key.name()));
    }

    private void restoreDefaults(String sectionName) {
        Optional<PanelSection> found = Arrays.stream(PanelSection.values())
                .filter(candidate -> candidate.name().equals(sectionName))
                .findFirst();
        if (found.isEmpty()) {
            return;
        }
        PanelSection key = found.get();
        boolean permissions = key == PanelSection.PERMISSIONS;
        if (permissions && permissionsTab.isEmpty()) {
            return;
        }
        List<SettingSnapshot> rows = permissions ? List.of() : resettable(key);
        int count = permissions ? permissionsTab.get().ruledShown() : rows.size();
        UICommandBuilder update = new UICommandBuilder();
        if (count == 0) {
            armedDefaults = Optional.empty();
            footer(update, tr.t("controlcore.panel.defaults.nothing"));
            sendUpdate(update, false);
            return;
        }
        boolean serverWide = key != PanelSection.PREFS && key != PanelSection.KEYMAPS;
        if (armedDefaults.filter(key::equals).isEmpty()) {
            armedDefaults = Optional.of(key);
            footer(
                    update,
                    tr.t(
                            serverWide && snapshotsView.isPresent()
                                    ? "controlcore.panel.defaults.confirm_safe"
                                    : "controlcore.panel.defaults.confirm",
                            "count",
                            count));
            update.set(key.search + "Defaults.Text", tr.t("controlcore.panel.confirm"));
            sendUpdate(update, false);
            return;
        }
        armedDefaults = Optional.empty();
        String safety = "";
        if (serverWide && snapshotsView.isPresent()) {
            Result<String> saved = snapshotsView.get().saveBefore("defaults", defaultsParts(key));
            if (saved.isFailure()) {
                footer(update, tr.error(saved.error()));
                sendUpdate(update, false);
                return;
            }
            safety = " " + tr.t("controlcore.snapshots.safety_saved", "name", saved.value());
        }
        int done = 0;
        if (permissions) {
            done = permissionsTab.get().restoreShown();
        } else {
            SettingsSection section = sections.get(key);
            for (SettingSnapshot row : rows) {
                if (section.reset(row.id().toString()).isSuccess()) {
                    done++;
                }
            }
        }
        rebuild();
        UICommandBuilder after = new UICommandBuilder();
        footer(after, tr.t("controlcore.panel.defaults.done", "count", done) + safety);
        showRestartNotice(after);
        sendUpdate(after, false);
    }

    private static Set<Snapshot.Part> defaultsParts(PanelSection key) {
        return switch (key) {
            case SERVER_CONFIG -> EnumSet.of(Snapshot.Part.CONFIG, Snapshot.Part.WORLDS);
            case SERVER_KEYMAPS -> EnumSet.of(Snapshot.Part.KEYS);
            case PERMISSIONS -> EnumSet.of(Snapshot.Part.PERMISSIONS);
            default -> EnumSet.of(Snapshot.Part.SETTINGS);
        };
    }

    private List<SettingSnapshot> resettable(PanelSection key) {
        SettingsSection section = sections.get(key);
        if (section == null) {
            return List.of();
        }
        SettingSearch search = searches.getOrDefault(key, SettingSearch.ALL);
        SettingRows.State legend = legendFilters.get(key);
        List<SettingSnapshot> found = new ArrayList<>();
        for (SettingsSection.Entry entry : built.getOrDefault(key, List.of())) {
            if (!(entry instanceof SettingsSection.Row row) || row.kind() == SettingsSection.Kind.INFO) {
                continue;
            }
            SettingSnapshot snapshot = latest.getOrDefault(shownKey(key, row.id()), row.snapshot());
            if (!inScope(key, row.id())
                    || !matchesSearch(section, search, snapshot)
                    || (legend != null && state(key, snapshot) != legend)) {
                continue;
            }
            if (snapshot.overridden() && snapshot.access() != SettingAccess.READ_ONLY) {
                found.add(snapshot);
            }
            row.color()
                    .map(color -> latest.getOrDefault(shownKey(key, color.id()), color))
                    .filter(color -> color.overridden() && color.access() != SettingAccess.READ_ONLY)
                    .ifPresent(found::add);
        }
        return found;
    }

    private DropdownEntryInfo dropdownEntry(String keyPrefix, String option, String value) {
        return new DropdownEntryInfo(
                LocalizableString.fromString(tr.t(keyPrefix + option.toLowerCase(Locale.ROOT))), value);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        stopListening();
        super.onDismiss(ref, store);
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PanelAction action) {
        replied = false;
        ChangeContext.fromPanel(viewerId, viewerName, () -> handle(action));
        if (!replied) {
            sendUpdate(new UICommandBuilder(), false);
        }
    }

    @Override
    protected void sendUpdate(UICommandBuilder commands, boolean clear) {
        replied = true;
        super.sendUpdate(commands, clear);
    }

    @Override
    protected void sendUpdate(UICommandBuilder commands, UIEventBuilder events, boolean clear) {
        replied = true;
        super.sendUpdate(commands, events, clear);
    }

    @Override
    protected void rebuild() {
        replied = true;
        super.rebuild();
    }

    private void handle(PanelAction action) {
        String name = action.name();
        if (bindForm.map(form -> form.handle(action)).orElse(false)
                || modsTab.handle(action)
                || permissionsTab.map(tab -> tab.handle(action)).orElse(false)
                || historyTab.map(tab -> tab.handle(action)).orElse(false)
                || snapshotsTab.map(tab -> tab.handle(action)).orElse(false)
                || profilesTab.map(tab -> tab.handle(action)).orElse(false)
                || healthTab.map(tab -> tab.handle(action)).orElse(false)
                || automationTab.map(tab -> tab.handle(action)).orElse(false)
                || bansTab.map(tab -> tab.handle(action)).orElse(false)) {
            return;
        }
        if (CLOSE.equals(name)) {
            close();
        } else if (RESTORE_DEFAULTS.equals(name)) {
            restoreDefaults(action.section);
        } else if (HISTORY_MODE_CHANGES.equals(name)
                || HISTORY_MODE_SNAPSHOTS.equals(name)
                || HISTORY_MODE_PROFILES.equals(name)) {
            if (HISTORY_MODE_PROFILES.equals(name) && profilesTab.isPresent()) {
                historyMode = HistoryMode.PROFILES;
            } else if (HISTORY_MODE_SNAPSHOTS.equals(name) && snapshotsTab.isPresent()) {
                historyMode = HistoryMode.SNAPSHOTS;
            } else if (historyTab.isPresent()) {
                historyMode = HistoryMode.CHANGES;
            }
            UICommandBuilder update = new UICommandBuilder();
            showHistoryMode(update);
            sendUpdate(update, false);
        } else if (SERVER_MODE_CONFIG.equals(name)
                || SERVER_MODE_WORLDS.equals(name)
                || SERVER_MODE_BANS.equals(name)) {
            showingWorlds = SERVER_MODE_WORLDS.equals(name) && !worlds.isEmpty();
            showingBans = SERVER_MODE_BANS.equals(name) && bansTab.isPresent();
            refreshServerScope();
        } else if (SERVER_WORLD.equals(name)) {
            if (action.value != null && worlds.contains(action.value)) {
                world = action.value;
            }
            refreshServerScope();
        } else if (HISTORY_LINK.equals(name)) {
            showHistoryOf(action.id);
        } else if (PROBE_TOGGLE.equals(name) || KEY_TEST.equals(name)) {
            handleDebug(name);
        } else if (SEARCH.equals(name) && PermissionsTab.SECTION.name().equals(action.section)) {
            permissionsTab.ifPresent(tab -> {
                SettingSearch search = SettingSearch.parse(
                        action.value, SettingSearch.Filter.of(action.filter), SettingSearch.Match.of(action.match));
                searches.put(PermissionsTab.SECTION, search);
                UICommandBuilder update = new UICommandBuilder();
                tab.search(search, update);
                sendUpdate(update, false);
            });
        } else if (SEARCH.equals(name)) {
            sectionOf(action.section).ifPresent(key -> {
                searches.put(
                        key,
                        SettingSearch.parse(
                                action.value,
                                SettingSearch.Filter.of(action.filter),
                                SettingSearch.Match.of(action.match)));
                UICommandBuilder update = new UICommandBuilder();
                applySearch(key, sections.get(key), update, true);
                showLegend(update);
                sendUpdate(update, false);
            });
        } else if (KEYMAP_MODE_SERVER.equals(name) || KEYMAP_MODE_MINE.equals(name)) {
            if (bindForm.isPresent()) {
                editingServerKeys = KEYMAP_MODE_SERVER.equals(name);
                UICommandBuilder update = new UICommandBuilder();
                showKeymapMode(update);
                showLegend(update);
                sendUpdate(update, false);
            }
        } else if (name.startsWith(TAB_PREFIX)) {
            selectTab(name.substring(TAB_PREFIX.length()));
        } else if (name.startsWith(LEGEND_PREFIX)) {
            filterByState(name.substring(LEGEND_PREFIX.length()));
        } else {
            sectionOf(action.section).ifPresent(key -> handleSettingAction(key, sections.get(key), name, action));
        }
    }

    private void buildDebugTools(UICommandBuilder commands, UIEventBuilder events) {
        commands.set("#DebugTools.Visible", probe.isPresent());
        if (probe.isEmpty()) {
            return;
        }
        showProbeButton(commands);
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#ProbeToggle", EventData.of(PanelAction.KEY, PROBE_TOGGLE));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#KeyTest", EventData.of(PanelAction.KEY, KEY_TEST));
    }

    private void showProbeButton(UICommandBuilder commands) {
        probe.ifPresent(tool -> commands.set(
                "#ProbeToggle.Text",
                tr.t(
                        tool.isOn(viewerId)
                                ? "controlcore.panel.debug.probe_stop"
                                : "controlcore.panel.debug.probe_start")));
    }

    private void handleDebug(String name) {
        if (probe.isEmpty()) {
            return;
        }
        InputProbe tool = probe.get();
        UICommandBuilder update = new UICommandBuilder();
        if (PROBE_TOGGLE.equals(name)) {
            boolean on = tool.toggle(viewerId);
            showProbeButton(update);
            footer(update, tr.t(on ? "controlcore.debug.probe_on" : "controlcore.debug.probe_off"));
        } else {
            Result<Void> opened = tool.openKeyTest(viewerId, InputProbe.KEY_TEST_TARGETS);
            if (opened.isSuccess()) {
                return;
            }
            footer(update, tr.error(opened.error()));
        }
        sendUpdate(update, false);
    }

    private void handleSettingAction(PanelSection key, SettingsSection section, String name, PanelAction action) {
        switch (name) {
            case SettingRows.SLIDE -> {
                showSliderText(key, action);
                return;
            }
            case SettingRows.PICK -> {
                showPickedColor(key, action, true);
                return;
            }
            case SettingRows.PICK_INLINE -> {
                showPickedColor(key, action, false);
                return;
            }
            case SettingRows.SAVE_COLORED, SettingRows.RESET_COLORED -> {
                saveColored(key, section, SettingRows.RESET_COLORED.equals(name), action);
                return;
            }
            case SettingRows.ADD_ITEM, SettingRows.REMOVE_ITEM -> {
                changeList(key, section, name, action);
                return;
            }
            case SettingRows.TYPED -> {
                typed(key, action);
                return;
            }
            case SettingRows.UNDO -> {
                undoChange(key, section, action);
                return;
            }
            case SettingRows.PICK_GAME_ACTION -> {
                pickGameAction(key, action);
                return;
            }
            case SettingRows.PICK_KEYS -> {
                SettingSnapshot current = latestOf(key, action.id);
                if (current == null) {
                    return;
                }
                action.value = KeyPicker.compose(action.value, action.bindKeys, current.value());
                if (action.value.equals(current.value())) {
                    UICommandBuilder update = new UICommandBuilder();
                    Integer index = rowIndexes.get(key).get(current.id());
                    if (index != null) {
                        showRowValue(key, section, index, current, update);
                    }
                    sendUpdate(update, false);
                    return;
                }
                name = SettingRows.SAVE;
            }
            default -> {}
        }
        SettingSnapshot before = latestOf(key, action.id);
        Result<SettingSnapshot> result = switch (name) {
            case SettingRows.SAVE, SettingRows.CHOOSE -> section.save(action.id, action.value);
            case SettingRows.SLID ->
                sliderValue(key, action)
                        .map(value -> section.save(action.id, value))
                        .orElse(null);
            case SettingRows.CHECK -> section.check(action.id, action.checked);
            case SettingRows.RESET -> section.reset(action.id);
            default -> null;
        };
        if (result == null) {
            return;
        }
        boolean controlAlreadyShowsValue = SettingRows.CHECK.equals(name) || SettingRows.CHOOSE.equals(name);
        UICommandBuilder update = new UICommandBuilder();
        if (key == PanelSection.SERVER_CONFIG) {
            showRestartNotice(update);
        }
        result.onSuccess(snapshot -> {
                    footer(update, section.saved(snapshot));
                    Integer index = rowIndexes.get(key).get(snapshot.id());
                    if (!controlAlreadyShowsValue && index != null) {
                        showRowValue(key, section, index, snapshot, update);
                    }
                    succeeded(key, before, snapshot, true, update);
                })
                .onFailure(error -> failed(key, action.id, tr.error(error), update));
        sendUpdate(update, false);
    }

    private void saveColored(PanelSection key, SettingsSection section, boolean reset, PanelAction action) {
        Result<SettingSnapshot> text = reset ? section.reset(action.id) : section.save(action.id, action.value);
        Result<SettingSnapshot> color = null;
        if (text.isSuccess() && action.item != null) {
            if (reset) {
                color = section.reset(action.item);
            } else {
                String hex = SettingRows.pickedHex(action.color);
                if (hex != null) {
                    color = section.save(action.item, hex);
                }
            }
        }
        UICommandBuilder update = new UICommandBuilder();
        if (text.isSuccess()) {
            SettingSnapshot saved = text.value();
            footer(update, section.saved(saved));
            Integer index = rowIndexes.get(key).get(saved.id());
            if (index != null) {
                SettingRows.showValue(section, SettingsSection.Kind.TEXT, key.row(index), saved, update);
            }
            succeeded(key, null, saved, false, update);
        } else {
            failed(key, action.id, tr.error(text.error()), update);
        }
        if (color != null && color.isSuccess()) {
            SettingSnapshot saved = color.value();
            remember(key, saved);
            Integer index = rowIndexes.get(key).get(saved.id());
            if (index != null) {
                SettingRows.showPicker(key.row(index), saved, update);
            }
        } else if (color != null) {
            footer(update, tr.error(color.error()));
        }
        sendUpdate(update, false);
    }

    private void changeList(PanelSection key, SettingsSection section, String name, PanelAction action) {
        SettingSnapshot snapshot = latestOf(key, action.id);
        if (snapshot == null) {
            return;
        }
        Result<SettingSnapshot> result;
        if (SettingRows.ADD_ITEM.equals(name)) {
            result = section.addItem(snapshot, action.value);
        } else {
            int index;
            try {
                index = Integer.parseInt(action.item == null ? "" : action.item);
            } catch (NumberFormatException e) {
                return;
            }
            result = section.removeItem(snapshot, index);
        }
        UICommandBuilder update = new UICommandBuilder();
        if (result.isSuccess()) {
            String shownKey = shownKey(key, snapshot.id());
            undo.put(shownKey, snapshot.value());
            errors.remove(shownKey);
            remember(key, result.value());
            rebuild();
            footer(update, section.saved(result.value()));
        } else {
            failed(key, action.id, tr.error(result.error()), update);
        }
        sendUpdate(update, false);
    }

    private void showPickedColor(PanelSection key, PanelAction action, boolean fillField) {
        String hex = SettingRows.pickedHex(action.value);
        Integer index = indexOf(key, action.id);
        if (hex == null || index == null) {
            return;
        }
        UICommandBuilder update = new UICommandBuilder();
        if (fillField) {
            update.set(key.row(index) + " #Input.Value", hex);
        }
        footer(update, tr.t("controlcore.panel.color_picked", "color", hex));
        sendUpdate(update, false);
    }

    private void showSliderText(PanelSection key, PanelAction action) {
        Optional<String> value = sliderValue(key, action);
        Integer index = indexOf(key, action.id);
        if (value.isEmpty() || index == null) {
            return;
        }
        UICommandBuilder update = new UICommandBuilder();
        update.set(key.row(index) + " #Input.Value", value.get());
        sendUpdate(update, false);
    }

    private Optional<String> sliderValue(PanelSection key, PanelAction action) {
        SettingSnapshot snapshot = latestOf(key, action.id);
        if (snapshot == null || action.number == null) {
            return Optional.empty();
        }
        SettingsSection.Kind kind = SettingsSection.kindOf(snapshot);
        if (kind != SettingsSection.Kind.INTEGER && kind != SettingsSection.Kind.DECIMAL) {
            return Optional.empty();
        }
        return Optional.of(
                SettingsSection.Slider.of(snapshot).text(action.number, kind == SettingsSection.Kind.INTEGER));
    }

    private void onSettingChanged(PanelSection key, SettingsSection section, SettingChanged change) {
        if (!section.concerns(change)) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            stopListening();
            return;
        }
        Store<EntityStore> store = ref.getStore();
        store.getExternalData().getWorld().execute(() -> refresh(ref, store, key, section, change.id()));
    }

    private void refresh(
            Ref<EntityStore> ref, Store<EntityStore> store, PanelSection key, SettingsSection section, SettingId id) {
        try {
            if (!isOpen(ref, store)) {
                stopListening();
                return;
            }
            Integer index = rowIndexes.getOrDefault(key, Map.of()).get(id);
            if (index == null) {
                return;
            }
            section.current(id).onSuccess(snapshot -> {
                if (snapshot.value().equals(shown.get(shownKey(key, id)))) {
                    return;
                }
                remember(key, snapshot);
                if (SettingsSection.kindOf(snapshot) == SettingsSection.Kind.LIST) {
                    rebuild();
                }
                UICommandBuilder update = new UICommandBuilder();
                if (key == PanelSection.SERVER_CONFIG) {
                    showRestartNotice(update);
                }
                showRowValue(key, section, index, snapshot, update);
                if (!inlineColors.contains(shownKey(key, id))) {
                    update.set(key.row(index) + " #Description.Text", section.description(snapshot));
                }
                unsaved.remove(shownKey(key, id));
                showState(key, index, snapshot, update);
                footer(update, section.changedElsewhere(snapshot));
                showLegend(update);
                sendUpdate(update, false);
            });
        } catch (RuntimeException e) {
            log.error("Could not refresh the control panel of " + playerRef.getUsername(), e);
        }
    }

    private void onBansChanged() {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            stopListening();
            return;
        }
        Store<EntityStore> store = ref.getStore();
        store.getExternalData().getWorld().execute(() -> {
            try {
                if (!isOpen(ref, store)) {
                    stopListening();
                } else if (showingBans) {
                    rebuild();
                }
            } catch (RuntimeException e) {
                log.error("Could not refresh the bans list of " + playerRef.getUsername(), e);
            }
        });
    }

    private boolean isOpen(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!ref.isValid()) {
            return false;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        return player != null && player.getPageManager().getCustomPage() == this;
    }

    private void stopListening() {
        for (Subscription subscription : subscriptions) {
            subscriptions.remove(subscription);
            subscription.close();
        }
    }

    private void showRowValue(
            PanelSection key, SettingsSection section, int index, SettingSnapshot snapshot, UICommandBuilder update) {
        if (inlineColors.contains(shownKey(key, snapshot.id()))) {
            SettingRows.showPicker(key.row(index), snapshot, update);
        } else {
            SettingRows.showValue(section, section.kind(snapshot), key.row(index), snapshot, update);
        }
    }

    private void showRestartNotice(UICommandBuilder commands) {
        if (!sections.containsKey(PanelSection.SERVER_CONFIG)) {
            return;
        }
        List<Text> names = pendingRestart.get();
        restartNames = names.stream().map(tr::translate).collect(Collectors.toSet());
        commands.set("#RestartBanner.Visible", !names.isEmpty());
        commands.set(
                "#RestartText.Text",
                names.isEmpty()
                        ? ""
                        : tr.t(
                                "controlcore.panel.restart_pending",
                                "keys",
                                names.stream().map(tr::translate).collect(Collectors.joining(", "))));
    }

    private void selectTab(String tabName) {
        for (Tab tab : tabs) {
            if (tab.name().equals(tabName) && tab != selected) {
                selected = tab;
                if (tab == Tab.HISTORY && historyTab.map(HistoryTab::stale).orElse(false)) {
                    rebuild();
                    return;
                }
                UICommandBuilder update = new UICommandBuilder();
                showTab(update);
                sendUpdate(update, false);
                return;
            }
        }
    }

    private void showTab(UICommandBuilder commands) {
        for (Tab tab : Tab.values()) {
            boolean on = tab == selected;
            commands.set(tab.group + ".Visible", on);
            commands.set(tab.slot() + ".Visible", tabs.contains(tab));
            commands.set(tab.edge() + ".Visible", on);
            commands.set(tab.button + ".Style", Value.ref(LAYOUT, on ? "SelectedTabStyle" : "TabStyle"));
        }
        showLegend(commands);
    }

    private void showKeymapMode(UICommandBuilder commands) {
        boolean admin = bindForm.isPresent();
        boolean server = admin && editingServerKeys;
        commands.set("#KeymapModeButtons.Visible", admin);
        commands.set("#ServerKeymaps.Visible", server);
        commands.set("#MyKeymaps.Visible", !server);
        commands.set("#NewBindToggle.Visible", server);
        commands.set("#KeymapModeServer.Style", Value.ref(LAYOUT, server ? "SegmentOnStyle" : "SegmentStyle"));
        commands.set("#KeymapModeMine.Style", Value.ref(LAYOUT, server ? "SegmentStyle" : "SegmentOnStyle"));
        listHeights(commands);
    }

    private void listHeights(UICommandBuilder commands) {
        boolean admin = bindForm.isPresent();
        int server = 536
                - (probe.isPresent() ? 42 : 0)
                - (bindForm.map(KeyBindForm::isOpen).orElse(false) ? 112 : 0);
        commands.setObject("#ServerKeymapList.Anchor", height(server));
        commands.setObject("#KeymapList.Anchor", height(admin ? 538 : 580));
    }

    private static Anchor height(int pixels) {
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(pixels));
        return anchor;
    }

    private void applySearch(PanelSection key, SettingsSection section, UICommandBuilder commands, boolean report) {
        List<SettingsSection.Entry> entries = built.getOrDefault(key, List.of());
        SettingSearch search = searches.getOrDefault(key, SettingSearch.ALL);
        SettingRows.State legend = legendFilters.get(key);
        boolean filtered = !search.isEmpty() || legend != null;
        boolean[] visible = new boolean[entries.size()];
        int rows = 0;
        int matching = 0;
        int heading = -1;
        for (int index = 0; index < entries.size(); index++) {
            switch (entries.get(index)) {
                case SettingsSection.Heading ignored -> {
                    heading = index;
                    visible[index] = !filtered && headingInScope(key, entries, index);
                }
                case SettingsSection.Row row -> {
                    rows++;
                    SettingSnapshot snapshot = latest.getOrDefault(shownKey(key, row.id()), row.snapshot());
                    visible[index] = inScope(key, row.id())
                            && matchesSearch(section, search, snapshot)
                            && (legend == null || state(key, snapshot) == legend);
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

    private void focusMod(String namespace) {
        boolean serverSettings = sections.containsKey(PanelSection.SETTINGS)
                && sections.get(PanelSection.SETTINGS).entries().stream()
                        .anyMatch(entry -> entry instanceof SettingsSection.Row row
                                && row.id().namespace().equals(namespace));
        PanelSection section = serverSettings ? PanelSection.SETTINGS : PanelSection.PREFS;
        Tab tab = serverSettings ? Tab.SETTINGS : Tab.PLAYER;
        if (sections.containsKey(section) && tabs.contains(tab)) {
            searches.put(section, modSearch(namespace));
            selected = tab;
        }
    }

    private void showSettingsOf(ModsView.Entry entry, boolean keys) {
        Tab tab;
        PanelSection section;
        if (keys) {
            tab = Tab.KEYMAP;
            section = editingServerKeys && bindForm.isPresent() ? PanelSection.SERVER_KEYMAPS : PanelSection.KEYMAPS;
        } else if (entry.hasServerSettings() && tabs.contains(Tab.SETTINGS)) {
            tab = Tab.SETTINGS;
            section = PanelSection.SETTINGS;
        } else {
            tab = Tab.PLAYER;
            section = PanelSection.PREFS;
        }
        if (!tabs.contains(tab)
                || !sections.containsKey(section)
                || entry.namespace().isEmpty()) {
            return;
        }
        SettingSearch search = modSearch(entry.namespace().get());
        searches.put(section, search);
        selected = tab;
        UICommandBuilder update = new UICommandBuilder();
        update.set(section.search + ".Value", search.text());
        update.set(section.search + "Filter.Value", search.filter().label());
        update.set(section.search + "Match.Value", search.match().label());
        applySearch(section, sections.get(section), update, true);
        showTab(update);
        sendUpdate(update, false);
    }

    private static SettingSearch modSearch(String namespace) {
        return SettingSearch.parse("mod:" + namespace, SettingSearch.Filter.ALL, SettingSearch.Match.CONTAINS);
    }

    private void remember(PanelSection key, SettingSnapshot snapshot) {
        shown.put(shownKey(key, snapshot.id()), snapshot.value());
        latest.put(shownKey(key, snapshot.id()), snapshot);
    }

    private void pickGameAction(PanelSection key, PanelAction action) {
        UICommandBuilder update = new UICommandBuilder();
        Result<SettingId> id = SettingId.parse(action.id == null ? "" : action.id);
        Integer index = indexOf(key, action.id);
        if (id.isSuccess()) {
            Optional<GameActionMode> wanted = GameActionMode.parse(action.value);
            Result<GameActionMode> saved =
                    wanted.isPresent() ? customKeyBinds.setGameAction(id.value(), wanted.get()) : null;
            if (saved != null && saved.isSuccess()) {
                SettingSnapshot row = latestOf(key, action.id);
                footer(
                        update,
                        tr.t(
                                "controlcore.keys.game_action.saved",
                                "name",
                                row == null
                                        ? action.id
                                        : tr.translate(row.definition().nameText()),
                                "mode",
                                SettingRows.gameActionLabel(tr, saved.value())));
            } else if (saved != null) {
                footer(update, tr.error(saved.error()));
            }
            if (index != null) {
                update.set(
                        key.row(index) + " #GameAction.Value",
                        customKeyBinds.gameAction(id.value()).id());
            }
        }
        sendUpdate(update, false);
    }

    private SettingSnapshot latestOf(PanelSection key, String idText) {
        return SettingId.parse(idText == null ? "" : idText)
                .map(id -> latest.get(shownKey(key, id)))
                .orElse(null);
    }

    private Integer indexOf(PanelSection key, String idText) {
        return SettingId.parse(idText == null ? "" : idText)
                .map(id -> rowIndexes.getOrDefault(key, Map.of()).get(id))
                .orElse(null);
    }

    private static String shownKey(PanelSection key, SettingId id) {
        return key.name() + "|" + id;
    }

    private Optional<PanelSection> sectionOf(String name) {
        for (PanelSection candidate : PanelSection.values()) {
            if (candidate.name().equals(name) && sections.containsKey(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private final class Hooks
            implements ModsTab.Host,
                    KeyBindForm.Host,
                    PermissionsTab.Host,
                    HistoryTab.Host,
                    SnapshotsTab.Host,
                    ProfilesTab.Host,
                    HealthTab.Host,
                    AutomationTab.Host,
                    BansTab.Host {

        @Override
        public PlayerRef viewer() {
            return playerRef;
        }

        @Override
        public void later(Runnable work) {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            ref.getStore().getExternalData().getWorld().execute(work);
        }

        @Override
        public void rebuild() {
            ControlPanelPage.this.rebuild();
        }

        @Override
        public void send(UICommandBuilder update) {
            sendUpdate(update, false);
        }

        @Override
        public void showSettingsOf(ModsView.Entry entry, boolean keys) {
            ControlPanelPage.this.showSettingsOf(entry, keys);
        }

        @Override
        public void formToggled(UICommandBuilder update) {
            listHeights(update);
        }

        @Override
        public void showRestartNotice(UICommandBuilder update) {
            ControlPanelPage.this.showRestartNotice(update);
        }

        @Override
        public void resize(String element, int pixels, UICommandBuilder update) {
            update.setObject(element + ".Anchor", height(pixels));
        }
    }
}
