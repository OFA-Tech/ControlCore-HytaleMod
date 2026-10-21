package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSearch;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The control panel as a native Hytale custom page ({@code Common/UI/Custom/Pages/ControlCore/ControlPanel.ui}).
 * Tabs are text buttons that show one {@code #...Tab} group and disable their own button. The settings tabs
 * append one row document per registered setting ({@link SettingRows}); appended rows are addressed as
 * {@code #List[index] #Child}. The Mods tab is {@link ModsTab}, the admin's bind form {@link KeyBindForm}.
 */
final class ControlPanelPage extends InteractiveCustomUIPage<PanelAction> {

    static final String LAYOUT = "Pages/ControlCore/ControlPanel.ui";

    private static final String CLOSE = "Close";
    private static final String TAB_PREFIX = "Tab:";
    private static final String SEARCH = "Search";
    private static final String KEYMAP_MODE_SERVER = "KeymapMode:Server";
    private static final String KEYMAP_MODE_MINE = "KeymapMode:Mine";
    private static final String CUSTOM_NAMESPACE = "custom";

    private enum Tab {
        SERVER(ControlPanelTab.SERVER_CONFIGS, "#ServerTab", "#ServerTabButton"),
        SETTINGS(ControlPanelTab.MOD_SETTINGS, "#SettingsTab", "#SettingsTabButton"),
        PLAYER(ControlPanelTab.PLAYER_PREFS, "#PlayerTab", "#PlayerTabButton"),
        KEYMAP(ControlPanelTab.KEYMAPS, "#KeymapTab", "#KeymapTabButton"),
        MODS(ControlPanelTab.MODS, "#ModsTab", "#ModsTabButton");

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

        boolean shownFor(ControlPanelRequest request) {
            return switch (this) {
                case SERVER, SETTINGS -> request.includeServerSettings();
                case PLAYER -> request.includePlayerPreferences();
                case KEYMAP -> request.includeRegisteredActions();
                case MODS -> true;
            };
        }
    }

    private final Log log;
    private final Translator tr;
    private final Supplier<List<Text>> pendingRestart;
    private final Set<Tab> tabs = EnumSet.noneOf(Tab.class);
    private final Map<PanelSection, SettingsSection> sections = new EnumMap<>(PanelSection.class);
    private final Map<PanelSection, Map<SettingId, Integer>> rowIndexes = new EnumMap<>(PanelSection.class);
    /** Colour settings shown inside a text row (same keys as {@link #shown}). */
    private final Set<String> inlineColors = new HashSet<>();
    /** Value each row currently shows, to skip change notifications the panel caused itself (world thread). */
    private final Map<String, String> shown = new HashMap<>();
    /** Latest snapshot of each row (same keys as {@link #shown}), used to filter rows by the search. */
    private final Map<String, SettingSnapshot> latest = new HashMap<>();
    /** Rows and headings of each section in display order, as built. */
    private final Map<PanelSection, List<SettingsSection.Entry>> built = new EnumMap<>(PanelSection.class);
    /** The search typed into each section; kept when the page is rebuilt. */
    private final Map<PanelSection, SettingSearch> searches = new EnumMap<>(PanelSection.class);

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ModsTab modsTab;
    /** The admin's bind form; empty for players and in {@code /prefs}. */
    private final Optional<KeyBindForm> bindForm;

    private Tab selected;
    /** Admin panel only: whether the Keymaps tab edits the server's keys (else the viewer's own). */
    private boolean editingServerKeys = true;

    ControlPanelPage(PlayerRef player, ControlPanelRequest request, ControlPanelContent content, Log log) {
        super(player, CustomPageLifetime.CanDismiss, PanelAction.CODEC);
        this.log = log;
        this.tr = content.translator();
        this.pendingRestart = content.pendingRestart();
        Hooks hooks = new Hooks();
        this.modsTab = new ModsTab(content.mods(), tr, hooks);
        ControlPanelRequest shownSections = request.isEmpty() ? ControlPanelRequest.PLAYER_PREFERENCES : request;
        boolean manageKeys = content.manageKeys() && shownSections.includeRegisteredActions();
        this.bindForm =
                manageKeys ? Optional.of(new KeyBindForm(content.customKeyBinds(), tr, hooks)) : Optional.empty();
        for (Tab tab : Tab.values()) {
            if (tab.shownFor(shownSections)) {
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

    // --- Building -------------------------------------------------------------------------------------------

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        sections.forEach((key, section) -> {
            buildSection(key, section, commands, events);
            buildSearch(key, commands, events);
            applySearch(key, section, commands, false);
        });
        modsTab.build(
                commands, events, tabs.contains(Tab.SETTINGS), tabs.contains(Tab.PLAYER), tabs.contains(Tab.KEYMAP));
        for (Tab tab : Tab.values()) {
            commands.set(tab.button + ".Visible", tabs.contains(tab));
        }
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
        showRestartNotice(commands);

        for (Tab tab : tabs) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    tab.button,
                    EventData.of(PanelAction.KEY, TAB_PREFIX + tab.name()));
        }
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#CloseButton", EventData.of(PanelAction.KEY, CLOSE));
        if (subscriptions.isEmpty()) {
            sections.forEach((key, section) ->
                    subscriptions.add(section.listen(change -> onSettingChanged(key, section, change))));
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
                case SettingsSection.Heading heading -> SettingRows.heading(key, element, heading.text(), commands);
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
                    SettingRows.build(key, section, row, element, customBind, commands, events);
                }
            }
        }
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
    }

    private DropdownEntryInfo dropdownEntry(String keyPrefix, String option, String value) {
        return new DropdownEntryInfo(
                LocalizableString.fromString(tr.t(keyPrefix + option.toLowerCase(Locale.ROOT))), value);
    }

    // --- Events ---------------------------------------------------------------------------------------------

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        stopListening();
        super.onDismiss(ref, store);
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PanelAction action) {
        String name = action.name();
        if (bindForm.map(form -> form.handle(action)).orElse(false) || modsTab.handle(action)) {
            return;
        }
        if (CLOSE.equals(name)) {
            close();
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
                sendUpdate(update, false);
            });
        } else if (KEYMAP_MODE_SERVER.equals(name) || KEYMAP_MODE_MINE.equals(name)) {
            if (bindForm.isPresent()) {
                editingServerKeys = KEYMAP_MODE_SERVER.equals(name);
                UICommandBuilder update = new UICommandBuilder();
                showKeymapMode(update);
                sendUpdate(update, false);
            }
        } else if (name.startsWith(TAB_PREFIX)) {
            selectTab(name.substring(TAB_PREFIX.length()));
        } else {
            sectionOf(action.section).ifPresent(key -> handleSettingAction(key, sections.get(key), name, action));
        }
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
            default -> {}
        }
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
        // Check boxes and dropdowns already show what the player picked. Setting their value again could fire
        // ValueChanged once more, so only text fields (normalised input) and resets are written back.
        boolean writeBack =
                SettingRows.SAVE.equals(name) || SettingRows.RESET.equals(name) || SettingRows.SLID.equals(name);
        UICommandBuilder update = new UICommandBuilder();
        result.onSuccess(snapshot -> {
                    remember(key, snapshot);
                    update.set(key.status + ".Text", section.saved(snapshot));
                    Integer index = rowIndexes.get(key).get(snapshot.id());
                    if (writeBack && index != null) {
                        showRowValue(key, section, index, snapshot, update);
                    }
                })
                .onFailure(error -> update.set(key.status + ".Text", tr.error(error)));
        if (key == PanelSection.SERVER_CONFIG) {
            showRestartNotice(update);
        }
        sendUpdate(update, false);
    }

    /** Saves (or resets) a text row and the colour shown in it. */
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
            remember(key, saved);
            update.set(key.status + ".Text", section.saved(saved));
            Integer index = rowIndexes.get(key).get(saved.id());
            if (index != null) {
                SettingRows.showValue(section, SettingsSection.Kind.TEXT, key.row(index), saved, update);
            }
        } else {
            update.set(key.status + ".Text", tr.error(text.error()));
        }
        if (color != null && color.isSuccess()) {
            SettingSnapshot saved = color.value();
            remember(key, saved);
            Integer index = rowIndexes.get(key).get(saved.id());
            if (index != null) {
                SettingRows.showPicker(key.row(index), saved, update);
            }
        } else if (color != null) {
            update.set(key.status + ".Text", tr.error(color.error()));
        }
        sendUpdate(update, false);
    }

    /** Adds or removes a list item, then rebuilds the page so the items and their buttons match. */
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
        if (result.isSuccess()) {
            rebuild();
        }
        UICommandBuilder update = new UICommandBuilder();
        update.set(key.status + ".Text", result.isSuccess() ? section.saved(result.value()) : tr.error(result.error()));
        sendUpdate(update, false);
    }

    /**
     * A picked colour: in a colour row it goes into the row's text field; in a text row with a colour it waits for
     * Save. Either way the status line says how to keep it.
     */
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
        update.set(key.status + ".Text", tr.t("controlcore.panel.color_picked", "color", hex));
        sendUpdate(update, false);
    }

    /** While the slider moves, the text field shows the value it would save. */
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

    /** @return the text the slider's position stands for, if the row is a number row */
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

    // --- Changes made elsewhere -----------------------------------------------------------------------------

    /** Called on the thread that changed the setting; the panel is updated on the player's world thread. */
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
                    rebuild(); // the items and their buttons change
                }
                UICommandBuilder update = new UICommandBuilder();
                showRowValue(key, section, index, snapshot, update);
                if (!inlineColors.contains(shownKey(key, id))) {
                    update.set(key.row(index) + " #Description.Text", section.description(snapshot));
                }
                update.set(key.status + ".Text", section.changedElsewhere(snapshot));
                if (key == PanelSection.SERVER_CONFIG) {
                    showRestartNotice(update);
                }
                sendUpdate(update, false);
            });
        } catch (RuntimeException e) {
            log.error("Could not refresh the control panel of " + playerRef.getUsername(), e);
        }
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

    // --- Showing values, tabs and searches ------------------------------------------------------------------

    /** Shows a value in its row: a colour shown inside a text row only moves that row's picker. */
    private void showRowValue(
            PanelSection key, SettingsSection section, int index, SettingSnapshot snapshot, UICommandBuilder update) {
        if (inlineColors.contains(shownKey(key, snapshot.id()))) {
            SettingRows.showPicker(key.row(index), snapshot, update);
        } else {
            SettingRows.showValue(section, SettingsSection.kindOf(snapshot), key.row(index), snapshot, update);
        }
    }

    /** Lists the server config keys and mods that wait for a restart, above the Server Configs list. */
    private void showRestartNotice(UICommandBuilder commands) {
        if (!sections.containsKey(PanelSection.SERVER_CONFIG)) {
            return;
        }
        List<Text> names = pendingRestart.get();
        commands.set("#ServerRestart.Visible", !names.isEmpty());
        commands.set(
                "#ServerRestart.Text",
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
                UICommandBuilder update = new UICommandBuilder();
                showTab(update);
                sendUpdate(update, false);
                return;
            }
        }
    }

    private void showTab(UICommandBuilder commands) {
        for (Tab tab : Tab.values()) {
            commands.set(tab.group + ".Visible", tab == selected);
            commands.set(tab.button + ".Disabled", tab == selected);
        }
    }

    private void showKeymapMode(UICommandBuilder commands) {
        boolean admin = bindForm.isPresent();
        boolean server = admin && editingServerKeys;
        commands.set("#KeymapModeButtons.Visible", admin);
        commands.set("#ServerKeymaps.Visible", server);
        commands.set("#MyKeymaps.Visible", !server);
        commands.set("#KeymapModeServer.Disabled", server);
        commands.set("#KeymapModeMine.Disabled", admin && !server);
    }

    /**
     * Shows only the rows matching the section's search, and the headings of categories with a shown row.
     *
     * @param report whether to say in the status line how many rows match
     */
    private void applySearch(PanelSection key, SettingsSection section, UICommandBuilder commands, boolean report) {
        List<SettingsSection.Entry> entries = built.getOrDefault(key, List.of());
        SettingSearch search = searches.getOrDefault(key, SettingSearch.ALL);
        boolean[] visible = new boolean[entries.size()];
        int rows = 0;
        int matching = 0;
        int heading = -1;
        for (int index = 0; index < entries.size(); index++) {
            switch (entries.get(index)) {
                case SettingsSection.Heading ignored -> {
                    heading = index;
                    visible[index] = search.isEmpty();
                }
                case SettingsSection.Row row -> {
                    rows++;
                    SettingSnapshot snapshot = latest.getOrDefault(shownKey(key, row.id()), row.snapshot());
                    visible[index] = search.matches(
                            snapshot,
                            section.name(snapshot),
                            section.category(snapshot),
                            section.description(snapshot));
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
            if (search.isEmpty()) {
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

    // --- Jumping to a mod's settings ------------------------------------------------------------------------

    /** Opens on the settings (or preferences) of the mod whose settings ids start with {@code namespace}. */
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

    /** Switches to a mod's settings (or preferences) or keys, searched by its id. */
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

    // --- Helpers --------------------------------------------------------------------------------------------

    private void remember(PanelSection key, SettingSnapshot snapshot) {
        shown.put(shownKey(key, snapshot.id()), snapshot.value());
        latest.put(shownKey(key, snapshot.id()), snapshot);
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

    /** What the Mods tab and the bind form may do on this page. */
    private final class Hooks implements ModsTab.Host, KeyBindForm.Host {

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
        public void showRestartNotice(UICommandBuilder update) {
            ControlPanelPage.this.showRestartNotice(update);
        }
    }
}
