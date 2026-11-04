package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

final class ControlPanelPage extends InteractiveCustomUIPage<PanelAction> {

    static final String LAYOUT = "Pages/ControlCore/ControlPanel.ui";

    private static final String CLOSE = "Close";
    private static final String TAB_PREFIX = "Tab:";
    private static final String HISTORY_MODE_CHANGES = "HistoryModeChanges";
    private static final String HISTORY_MODE_SNAPSHOTS = "HistoryModeSnapshots";
    private static final String HISTORY_MODE_PROFILES = "HistoryModeProfiles";
    private static final long PLAYERS_REFRESH_DELAY_MILLIS = 750;

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

    private enum HistoryMode {
        CHANGES,
        SNAPSHOTS,
        PROFILES
    }

    private final Log log;
    private final Translator tr;
    private final Set<Tab> tabs = EnumSet.noneOf(Tab.class);
    private final Map<PanelSection, SettingsSection> sections = new EnumMap<>(PanelSection.class);
    private final Map<PanelSection, SectionState> states = new EnumMap<>(PanelSection.class);
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ModsTab modsTab;
    private final Optional<KeyBindForm> bindForm;
    private final Optional<PermissionsTab> permissionsTab;
    private final Optional<HistoryTab> historyTab;
    private final Optional<SnapshotsTab> snapshotsTab;
    private final Optional<ProfilesTab> profilesTab;
    private final Optional<HealthTab> healthTab;
    private final Optional<AutomationTab> automationTab;
    private final Optional<PlayersTab> playersTab;
    private final Optional<BackupsTab> backupsTab;
    private final RestartControls restart;
    private final SettingRowEditor rows;
    private final SectionSearch search;
    private final KeymapTools keys;
    private final ServerScope scope;
    private final PanelKeyDetect keyDetect;
    private final PlayerId viewerId;
    private final String viewerName;
    private final BooleanSupplier stillAdmin;
    private final AtomicBoolean playersRefreshPending = new AtomicBoolean();
    private HistoryMode historyMode;
    private Tab selected;
    private boolean replied;

    ControlPanelPage(PlayerRef player, ControlPanelRequest request, ControlPanelContent content, Log log) {
        super(player, CustomPageLifetime.CanDismiss, PanelAction.CODEC);
        this.log = log;
        this.tr = content.translator();
        Hooks hooks = new Hooks();
        this.modsTab = new ModsTab(content.mods(), tr, hooks);
        ControlPanelRequest shownSections = request.isEmpty() ? ControlPanelRequest.PLAYER_PREFERENCES : request;
        boolean manageKeys = content.manageKeys() && shownSections.includeRegisteredActions();
        this.bindForm = manageKeys
                ? Optional.of(new KeyBindForm(content.customKeyBinds(), tr, hooks, content.keyDetection()::allKeys))
                : Optional.empty();
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
        this.automationTab = shownSections.includeServerSettings()
                ? (content.automation().isPresent() || content.announcements().isPresent()
                        ? Optional.of(new AutomationTab(content.automation(), content.announcements(), tr, hooks))
                        : Optional.empty())
                : Optional.empty();
        this.historyMode = historyTab.isPresent() ? HistoryMode.CHANGES : HistoryMode.SNAPSHOTS;
        this.playersTab = shownSections.includeServerSettings()
                ? content.players().map(view -> new PlayersTab(view, content.maintenance(), tr, hooks, log))
                : Optional.empty();
        this.backupsTab = shownSections.includeServerSettings()
                ? content.backups().map(view -> new BackupsTab(view, tr, hooks))
                : Optional.empty();
        this.restart = new RestartControls(
                shownSections.includeServerSettings() ? content.restart() : Optional.empty(), tr, hooks);
        this.viewerId = PlayerId.of(player.getUuid());
        this.viewerName = player.getUsername();
        this.stillAdmin = content.stillAdmin();
        this.keyDetect = new PanelKeyDetect(content.keyDetection(), content.rights(), tr, hooks, log);
        for (PanelSection key : PanelSection.values()) {
            states.put(key, new SectionState());
        }
        this.rows =
                new SettingRowEditor(sections, states, content.pendingRestart(), content.defaults(), tr, hooks, log);
        this.search = new SectionSearch(sections, states, tr, hooks);
        this.keys = new KeymapTools(
                states,
                content.customKeyBinds(),
                bindForm,
                manageKeys ? content.debug() : Optional.empty(),
                keyDetect,
                viewerId,
                tr,
                hooks);
        this.scope = new ServerScope(sections, content.worlds(), playersTab.isPresent(), backupsTab.isPresent(), hooks);
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
                            tr,
                            content.keyDetection()::allKeys));
        }
        if (manageKeys) {
            sections.put(
                    PanelSection.SERVER_KEYMAPS,
                    SettingsSection.keymaps(
                            content.keymaps(),
                            SettingScope.SERVER,
                            viewer,
                            tr.t("controlcore.panel.empty.server_keymaps"),
                            tr,
                            content.keyDetection()::allKeys));
        }
        content.focus().ifPresent(search::focusMod);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        rows.showRestartNotice(commands);
        sections.forEach((key, section) -> {
            rows.build(key, section, commands, events);
            search.build(key, commands, events);
            search.apply(key, section, commands, false);
        });
        modsTab.build(
                commands, events, tabs.contains(Tab.SETTINGS), tabs.contains(Tab.PLAYER), tabs.contains(Tab.KEYMAP));
        permissionsTab.ifPresent(tab -> {
            tab.build(commands, events, search.of(PermissionsTab.SECTION));
            search.build(PermissionsTab.SECTION, commands, events);
        });
        historyTab.ifPresent(tab -> tab.build(commands, events));
        snapshotsTab.ifPresent(tab -> tab.build(commands, events));
        profilesTab.ifPresent(tab -> tab.build(commands, events));
        healthTab.ifPresent(tab -> tab.build(commands, events));
        automationTab.ifPresent(tab -> tab.build(commands, events));
        playersTab.ifPresent(tab -> tab.build(commands, events));
        backupsTab.ifPresent(tab -> tab.build(commands, events));
        restart.build(commands, events, healthTab.isPresent());
        rows.disarmDefaults();
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
        scope.build(commands, events);
        keys.buildDebugTools(commands, events);
        commands.set("#TabButtons.Visible", tabs.size() > 1);
        keys.buildModeSwitch(commands, events);
        showTab(commands);
        keys.showKeymapMode(commands);

        for (Tab tab : tabs) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    tab.button,
                    EventData.of(PanelAction.KEY, TAB_PREFIX + tab.name()));
        }
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#CloseButton", EventData.of(PanelAction.KEY, CLOSE));
        search.bindLegend(events);
        if (subscriptions.isEmpty()) {
            sections.forEach((key, section) ->
                    subscriptions.add(section.listen(change -> rows.onSettingChanged(key, section, change))));
            playersTab.ifPresent(tab -> subscriptions.add(tab.view().onChange(this::refreshPlayers)));
            restart.listen().ifPresent(subscriptions::add);
        }
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

    private Optional<PanelSection> currentSection() {
        PanelSection key = switch (selected) {
            case SERVER -> scope.showingRows() ? PanelSection.SERVER_CONFIG : null;
            case SETTINGS -> PanelSection.SETTINGS;
            case PLAYER -> PanelSection.PREFS;
            case KEYMAP -> keys.keymapSection();
            case MODS, PERMISSIONS, HISTORY, HEALTH, AUTOMATION -> null;
        };
        return key != null && sections.containsKey(key) ? Optional.of(key) : Optional.empty();
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
        try {
            ChangeContext.fromPanel(viewerId, viewerName, () -> handle(action));
        } catch (RuntimeException e) {
            log.error("A control panel action of " + viewerName + " failed: " + action.name(), e);
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.t("controlcore.panel.failed"));
            sendUpdate(update, false);
            return;
        }
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
        boolean serverKeys = KeyBindForm.changesServerBinds(name)
                || PanelSection.SERVER_KEYMAPS.name().equals(action.section);
        if (serverKeys && sections.containsKey(PanelSection.SERVER_KEYMAPS) && !stillAdmin.getAsBoolean()) {
            UICommandBuilder update = new UICommandBuilder();
            SettingRowEditor.footer(update, tr.t("controlcore.panel.no_longer_admin"));
            sendUpdate(update, false);
            return;
        }
        if (bindForm.map(form -> form.handle(action)).orElse(false)
                || modsTab.handle(action)
                || permissionsTab.map(tab -> tab.handle(action)).orElse(false)
                || historyTab.map(tab -> tab.handle(action)).orElse(false)
                || snapshotsTab.map(tab -> tab.handle(action)).orElse(false)
                || profilesTab.map(tab -> tab.handle(action)).orElse(false)
                || healthTab.map(tab -> tab.handle(action)).orElse(false)
                || automationTab.map(tab -> tab.handle(action)).orElse(false)
                || playersTab.map(tab -> tab.handle(action)).orElse(false)
                || backupsTab.map(tab -> tab.handle(action)).orElse(false)
                || restart.handle(action)) {
            return;
        }
        if (CLOSE.equals(name)) {
            close();
        } else if (SettingRowEditor.RESTORE_DEFAULTS.equals(name)) {
            rows.restoreDefaults(action.section);
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
        } else if (SettingRowEditor.HISTORY_LINK.equals(name)) {
            showHistoryOf(action.id);
        } else if (name.startsWith(TAB_PREFIX)) {
            selectTab(name.substring(TAB_PREFIX.length()));
        } else if (!scope.handle(action) && !keys.handle(action) && !search.handle(action)) {
            search.sectionOf(action.section).ifPresent(key -> {
                if (!keys.handleRow(key, sections.get(key), name, action)) {
                    rows.handle(key, sections.get(key), name, action);
                }
            });
        }
    }

    private void refreshPlayers() {
        if (!playersRefreshPending.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.delayedExecutor(PLAYERS_REFRESH_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref == null || !ref.isValid()) {
                        playersRefreshPending.set(false);
                        stopListening();
                        return;
                    }
                    Store<EntityStore> store = ref.getStore();
                    store.getExternalData().getWorld().execute(() -> {
                        playersRefreshPending.set(false);
                        try {
                            if (!isOpen(ref, store)) {
                                stopListening();
                            } else if (scope.showingPlayers()
                                    && playersTab.map(PlayersTab::refreshNow).orElse(false)) {
                                rebuild();
                            }
                        } catch (RuntimeException e) {
                            log.error("Could not refresh the Players list of " + playerRef.getUsername(), e);
                        }
                    });
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
        search.showLegend(commands);
    }

    static Anchor height(int pixels) {
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(pixels));
        return anchor;
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
                    PlayersTab.Host,
                    BackupsTab.Host,
                    RestartControls.Host,
                    PanelKeyDetect.Host,
                    SettingRowEditor.Host,
                    SectionSearch.Host,
                    KeymapTools.Host,
                    ServerScope.Host {

        @Override
        public PlayerRef viewer() {
            return playerRef;
        }

        @Override
        public CustomUIPage page() {
            return ControlPanelPage.this;
        }

        @Override
        public void closePanel() {
            close();
        }

        @Override
        public String asViewer(Supplier<String> work) {
            return ChangeContext.fromPanel(viewerId, viewerName, work);
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
            search.showSettingsOf(entry, keys);
        }

        @Override
        public void formToggled(UICommandBuilder update) {
            keys.listHeights(update);
        }

        @Override
        public void detect(BiFunction<String, KeyDetection.Mode, String> applyKeys) {
            keyDetect.start(PanelKeyDetect.Target.BIND_FORM, applyKeys);
        }

        @Override
        public void showRestartNotice(UICommandBuilder update) {
            rows.showRestartNotice(update);
        }

        @Override
        public void restartBanner(UICommandBuilder update, boolean pending) {
            restart.showBanner(update, pending);
        }

        @Override
        public void whileOpen(Runnable work) {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                ControlPanelPage.this.stopListening();
                return;
            }
            Store<EntityStore> store = ref.getStore();
            store.getExternalData().getWorld().execute(() -> {
                if (!ControlPanelPage.this.isOpen(ref, store)) {
                    ControlPanelPage.this.stopListening();
                    return;
                }
                try {
                    work.run();
                } catch (RuntimeException e) {
                    log.error("Could not update the control panel of " + playerRef.getUsername(), e);
                }
            });
        }

        @Override
        public void resize(String element, int pixels, UICommandBuilder update) {
            update.setObject(element + ".Anchor", height(pixels));
        }

        @Override
        public void stopListening() {
            ControlPanelPage.this.stopListening();
        }

        @Override
        public boolean isOpen(Ref<EntityStore> ref, Store<EntityStore> store) {
            return ControlPanelPage.this.isOpen(ref, store);
        }

        @Override
        public void showLegend(UICommandBuilder update) {
            search.showLegend(update);
        }

        @Override
        public void worldChanged(SettingId changed) {
            scope.worldChanged(changed);
        }

        @Override
        public boolean hasHistory(SettingId id) {
            return historyTab.map(tab -> tab.has(id.toString())).orElse(false);
        }

        @Override
        public boolean customBind(PanelSection key, SettingId id) {
            return keys.customBind(key, id);
        }

        @Override
        public Optional<GameActionMode> gameAction(PanelSection key, SettingId id) {
            return keys.gameAction(key, id);
        }

        @Override
        public String conflictNote(PanelSection key, SettingsSection section, SettingSnapshot saved) {
            return keys.conflictNote(key, section, saved);
        }

        @Override
        public Set<String> shownIds(PanelSection key) {
            return search.shownIds(key);
        }

        @Override
        public Optional<Set<String>> permissionTargets() {
            return permissionsTab.map(PermissionsTab::shownTargets);
        }

        @Override
        public Optional<PanelSection> currentSection() {
            return ControlPanelPage.this.currentSection();
        }

        @Override
        public boolean inScope(PanelSection key, SettingId id) {
            return scope.inScope(key, id);
        }

        @Override
        public boolean headingInScope(PanelSection key, List<SettingsSection.Entry> entries, int heading) {
            return scope.headingInScope(key, entries, heading);
        }

        @Override
        public SettingRows.State state(PanelSection key, SettingSnapshot snapshot) {
            return rows.state(key, snapshot);
        }

        @Override
        public Optional<PermissionsTab> permissionsTab() {
            return permissionsTab;
        }

        @Override
        public boolean hasTab(ControlPanelTab tab) {
            return tabs.contains(Tab.of(tab));
        }

        @Override
        public void select(ControlPanelTab tab) {
            selected = Tab.of(tab);
        }

        @Override
        public void showTab(UICommandBuilder update) {
            ControlPanelPage.this.showTab(update);
        }

        @Override
        public PanelSection keymapSection() {
            return keys.keymapSection();
        }

        @Override
        public SettingSnapshot latestOf(PanelSection key, String idText) {
            return rows.latestOf(key, idText);
        }

        @Override
        public Integer indexOf(PanelSection key, String idText) {
            return rows.indexOf(key, idText);
        }

        @Override
        public Result<SettingSnapshot> saveValue(
                PanelSection key,
                SettingsSection section,
                String id,
                Supplier<Result<SettingSnapshot>> change,
                boolean controlAlreadyShowsValue,
                UICommandBuilder update) {
            return rows.saveValue(key, section, id, change, controlAlreadyShowsValue, update);
        }

        @Override
        public void applySearch(PanelSection key, SettingsSection section, UICommandBuilder update, boolean report) {
            search.apply(key, section, update, report);
        }

        @Override
        public void refresh(
                Ref<EntityStore> ref,
                Store<EntityStore> store,
                PanelSection key,
                SettingsSection section,
                SettingId id) {
            rows.refresh(ref, store, key, section, id);
        }
    }
}
