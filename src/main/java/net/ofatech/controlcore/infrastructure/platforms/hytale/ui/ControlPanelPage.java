package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBindEditor;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The control panel as a native Hytale custom page ({@code Common/UI/Custom/Pages/ControlCore/ControlPanel.ui}).
 * Tabs are text buttons that show one {@code #...Tab} group and disable their own button. The settings tabs
 * append one row document per registered setting ({@code Setting*Row.ui}); appended rows are addressed as
 * {@code #List[index] #Child}.
 */
final class ControlPanelPage extends InteractiveCustomUIPage<ControlPanelPage.Action> {

    static final String LAYOUT = "Pages/ControlCore/ControlPanel.ui";

    private static final String HEADING = "Pages/ControlCore/SettingCategory.ui";
    private static final String TEXT_ROW = "Pages/ControlCore/SettingTextRow.ui";
    private static final String TOGGLE_ROW = "Pages/ControlCore/SettingToggleRow.ui";
    private static final String CHOICE_ROW = "Pages/ControlCore/SettingChoiceRow.ui";
    private static final String INFO_ROW = "Pages/ControlCore/SettingInfoRow.ui";
    private static final String CUSTOM_BIND_ROW = "Pages/ControlCore/SettingCustomBindRow.ui";

    private static final String CLOSE = "Close";
    private static final String TAB_PREFIX = "Tab:";
    private static final String SAVE = "Save";
    private static final String CHECK = "Check";
    private static final String CHOOSE = "Choose";
    private static final String RESET = "Reset";
    private static final String ADD_BIND = "AddBind";
    private static final String EDIT_BIND = "EditBind";
    private static final String REMOVE_BIND = "RemoveBind";
    private static final String CUSTOM_NAMESPACE = "custom";
    private static final String KEYMAP_MODE_SERVER = "KeymapMode:Server";
    private static final String KEYMAP_MODE_MINE = "KeymapMode:Mine";

    private enum Tab {
        SERVER(ControlPanelTab.SERVER_CONFIGS, "#ServerTab", "#ServerTabButton"),
        SETTINGS(ControlPanelTab.MOD_SETTINGS, "#SettingsTab", "#SettingsTabButton"),
        PLAYER(ControlPanelTab.PLAYER_PREFS, "#PlayerTab", "#PlayerTabButton"),
        KEYMAP(ControlPanelTab.KEYMAPS, "#KeymapTab", "#KeymapTabButton");

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
            };
        }
    }

    /** A list of settings in the panel and where it is drawn. */
    private enum Section {
        SERVER_CONFIG("#ServerList", "#ServerStatus"),
        SETTINGS("#SettingsList", "#SettingsStatus"),
        PREFS("#PlayerList", "#PlayerStatus"),
        KEYMAPS("#KeymapList", "#KeymapStatus"),
        SERVER_KEYMAPS("#ServerKeymapList", "#ServerKeymapStatus");

        private final String list;
        private final String status;

        Section(String list, String status) {
            this.list = list;
            this.status = status;
        }

        String row(int index) {
            return list + "[" + index + "]";
        }
    }

    private final Log log;
    private final CustomKeyBindEditor customKeyBinds;
    private final boolean canAddBinds;
    private final Set<Tab> tabs = EnumSet.noneOf(Tab.class);
    private final Map<Section, SettingsSection> sections = new EnumMap<>(Section.class);
    private final Map<Section, Map<SettingId, Integer>> rowIndexes = new EnumMap<>(Section.class);
    /** Value each row currently shows, to skip change notifications the panel caused itself (world thread). */
    private final Map<String, String> shown = new HashMap<>();

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private Tab selected;
    /** Admin panel only: whether the Keymaps tab edits the server's keys (else the viewer's own). */
    private boolean editingServerKeys = true;

    ControlPanelPage(PlayerRef player, ControlPanelRequest request, ControlPanelContent content, Log log) {
        super(player, CustomPageLifetime.CanDismiss, Action.CODEC);
        this.log = log;
        this.customKeyBinds = content.customKeyBinds();
        ControlPanelRequest shownSections = request.isEmpty() ? ControlPanelRequest.PLAYER_PREFERENCES : request;
        this.canAddBinds = shownSections.includeServerSettings() && shownSections.includeRegisteredActions();
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
                    Section.SERVER_CONFIG,
                    new SettingsSection(
                            content.serverConfig(),
                            SettingScope.SERVER,
                            viewer,
                            "The server config cannot be edited on this platform."));
            sections.put(
                    Section.SETTINGS,
                    new SettingsSection(
                            content.settings(), SettingScope.SERVER, viewer, "No server settings are registered yet."));
        }
        if (shownSections.includePlayerPreferences()) {
            sections.put(
                    Section.PREFS,
                    new SettingsSection(
                            content.settings(),
                            SettingScope.PLAYER,
                            viewer,
                            "No mod has registered player preferences yet."));
        }
        if (shownSections.includeRegisteredActions()) {
            sections.put(
                    Section.KEYMAPS,
                    SettingsSection.keymaps(
                            content.keymaps(), SettingScope.PLAYER, viewer, "No mod has registered key actions yet."));
        }
        if (canAddBinds) {
            sections.put(
                    Section.SERVER_KEYMAPS,
                    SettingsSection.keymaps(
                            content.keymaps(),
                            SettingScope.SERVER,
                            viewer,
                            "No key actions yet. Mods register them, or add your own above."));
        }
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        sections.forEach((key, section) -> buildSection(key, section, commands, events));
        for (Tab tab : Tab.values()) {
            commands.set(tab.button + ".Visible", tabs.contains(tab));
        }
        commands.set("#TabButtons.Visible", tabs.size() > 1);
        if (canAddBinds) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#KeymapModeServer",
                    EventData.of(Action.KEY, KEYMAP_MODE_SERVER));
            events.addEventBinding(
                    CustomUIEventBindingType.Activating, "#KeymapModeMine", EventData.of(Action.KEY, KEYMAP_MODE_MINE));
            commands.set("#NewBindName.PlaceholderText", "name, e.g. warp-spawn");
            commands.set("#NewBindKeys.PlaceholderText", "keys, e.g. ctrl+r");
            commands.set("#NewBindCommand.PlaceholderText", "command, e.g. warp spawn {player}");
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#NewBindAdd",
                    new EventData()
                            .append(Action.KEY, ADD_BIND)
                            .append(Action.NAME, "#NewBindName.Value")
                            .append(Action.KEYS, "#NewBindKeys.Value")
                            .append(Action.VALUE, "#NewBindCommand.Value")
                            .append(Action.CHECKED, "#NewBindAsServer.Value"));
        }
        showTab(commands);
        showKeymapMode(commands);

        for (Tab tab : tabs) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating, tab.button, EventData.of(Action.KEY, TAB_PREFIX + tab.name()));
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of(Action.KEY, CLOSE));
        if (subscriptions.isEmpty()) {
            sections.forEach((key, section) ->
                    subscriptions.add(section.listen(change -> onSettingChanged(key, section, change))));
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        stopListening();
        super.onDismiss(ref, store);
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Action action) {
        String name = action.name != null ? action.name : "";
        if (CLOSE.equals(name)) {
            close();
        } else if (ADD_BIND.equals(name)) {
            addBind(action);
        } else if (KEYMAP_MODE_SERVER.equals(name) || KEYMAP_MODE_MINE.equals(name)) {
            if (canAddBinds) {
                editingServerKeys = KEYMAP_MODE_SERVER.equals(name);
                UICommandBuilder update = new UICommandBuilder();
                showKeymapMode(update);
                sendUpdate(update, false);
            }
        } else if (EDIT_BIND.equals(name)) {
            editBind(action.id);
        } else if (REMOVE_BIND.equals(name)) {
            removeBind(action.id);
        } else if (name.startsWith(TAB_PREFIX)) {
            selectTab(name.substring(TAB_PREFIX.length()));
        } else {
            sectionOf(action.section).ifPresent(key -> handleSettingAction(key, sections.get(key), name, action));
        }
    }

    private void buildSection(Section key, SettingsSection section, UICommandBuilder commands, UIEventBuilder events) {
        Map<SettingId, Integer> indexes = new HashMap<>();
        rowIndexes.put(key, indexes);
        commands.set(key.status + ".Text", "");
        List<SettingsSection.Entry> entries = section.entries();
        for (int index = 0; index < entries.size(); index++) {
            String element = key.row(index);
            switch (entries.get(index)) {
                case SettingsSection.Heading heading -> {
                    commands.append(key.list, HEADING);
                    commands.set(element + ".Text", heading.text());
                }
                case SettingsSection.Row row -> {
                    indexes.put(row.id(), index);
                    shown.put(shownKey(key, row.id()), row.snapshot().value());
                    boolean customBind = canAddBinds
                            && key == Section.SERVER_KEYMAPS
                            && CUSTOM_NAMESPACE.equals(row.id().namespace());
                    buildRow(key, section, row, element, customBind, commands, events);
                }
            }
        }
    }

    private static void buildRow(
            Section key,
            SettingsSection section,
            SettingsSection.Row row,
            String element,
            boolean customBind,
            UICommandBuilder commands,
            UIEventBuilder events) {
        SettingSnapshot snapshot = row.snapshot();
        String input = element + " #Input";
        String id = snapshot.id().toString();
        switch (row.kind()) {
            case TEXT -> {
                commands.append(key.list, customBind ? CUSTOM_BIND_ROW : TEXT_ROW);
                if (customBind) {
                    events.addEventBinding(
                            CustomUIEventBindingType.Activating, element + " #Edit", action(EDIT_BIND, key, id));
                    events.addEventBinding(
                            CustomUIEventBindingType.Activating, element + " #Remove", action(REMOVE_BIND, key, id));
                }
                commands.set(input + ".PlaceholderText", section.placeholder(snapshot));
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        element + " #Save",
                        action(SAVE, key, id).append(Action.VALUE, input + ".Value"));
            }
            case TOGGLE -> {
                commands.append(key.list, TOGGLE_ROW);
                events.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        input,
                        action(CHECK, key, id).append(Action.CHECKED, input + ".Value"));
            }
            case CHOICE -> {
                commands.append(key.list, CHOICE_ROW);
                commands.set(
                        input + ".Entries",
                        SettingsSection.options(snapshot).stream()
                                .map(option -> new DropdownEntryInfo(LocalizableString.fromString(option), option))
                                .toList());
                events.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        input,
                        action(CHOOSE, key, id).append(Action.VALUE, input + ".Value"));
            }
            case INFO -> commands.append(key.list, INFO_ROW);
        }
        commands.set(element + " #Label.Text", snapshot.definition().displayName());
        commands.set(element + " #Description.Text", section.description(snapshot));
        showValue(row.kind(), element, snapshot, commands);
        if (row.kind() != SettingsSection.Kind.INFO) {
            events.addEventBinding(CustomUIEventBindingType.Activating, element + " #Reset", action(RESET, key, id));
        }
    }

    private void handleSettingAction(Section key, SettingsSection section, String name, Action action) {
        Result<SettingSnapshot> result = switch (name) {
            case SAVE, CHOOSE -> section.save(action.id, action.value);
            case CHECK -> section.check(action.id, action.checked);
            case RESET -> section.reset(action.id);
            default -> null;
        };
        if (result == null) {
            return;
        }
        // Check boxes and dropdowns already show what the player picked. Setting their value again could fire
        // ValueChanged once more, so only text fields (normalised input) and resets are written back.
        boolean writeBack = SAVE.equals(name) || RESET.equals(name);
        UICommandBuilder update = new UICommandBuilder();
        result.onSuccess(snapshot -> {
                    shown.put(shownKey(key, snapshot.id()), snapshot.value());
                    update.set(key.status + ".Text", SettingsSection.saved(snapshot));
                    Integer index = rowIndexes.get(key).get(snapshot.id());
                    if (writeBack && index != null) {
                        showValue(SettingsSection.kindOf(snapshot), key.row(index), snapshot, update);
                    }
                })
                .onFailure(error -> update.set(key.status + ".Text", error.description()));
        sendUpdate(update, false);
    }

    private void editBind(String id) {
        if (!canAddBinds || id == null) {
            return;
        }
        UICommandBuilder update = new UICommandBuilder();
        customKeyBinds
                .find(id)
                .ifPresentOrElse(
                        bind -> {
                            update.set("#NewBindName.Value", bind.name());
                            update.set("#NewBindKeys.Value", bind.keys());
                            update.set("#NewBindCommand.Value", bind.command());
                            update.set("#NewBindAsServer.Value", bind.runAsServer());
                            update.set(
                                    Section.SERVER_KEYMAPS.status + ".Text",
                                    "Editing custom:" + bind.name() + ": change it above and click Save bind.");
                        },
                        () -> update.set(Section.SERVER_KEYMAPS.status + ".Text", "There is no key bind " + id + "."));
        sendUpdate(update, false);
    }

    private void removeBind(String id) {
        if (!canAddBinds || id == null) {
            return;
        }
        Result<String> result = customKeyBinds.remove(id);
        if (result.isSuccess()) {
            rebuild();
        }
        UICommandBuilder update = new UICommandBuilder();
        update.set(
                Section.SERVER_KEYMAPS.status + ".Text",
                result.isSuccess() ? result.value() : result.error().description());
        sendUpdate(update, false);
    }

    private void addBind(Action action) {
        if (!canAddBinds) {
            return;
        }
        Result<String> result =
                customKeyBinds.add(action.bindName, action.bindKeys, action.value, Boolean.TRUE.equals(action.checked));
        if (result.isSuccess()) {
            rebuild();
        }
        UICommandBuilder update = new UICommandBuilder();
        update.set(
                Section.SERVER_KEYMAPS.status + ".Text",
                result.isSuccess() ? result.value() : result.error().description());
        if (result.isSuccess()) {
            update.set("#NewBindName.Value", "");
            update.set("#NewBindKeys.Value", "");
            update.set("#NewBindCommand.Value", "");
            update.set("#NewBindAsServer.Value", false);
        }
        sendUpdate(update, false);
    }

    /** Called on the thread that changed the setting; the panel is updated on the player's world thread. */
    private void onSettingChanged(Section key, SettingsSection section, SettingChanged change) {
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
            Ref<EntityStore> ref, Store<EntityStore> store, Section key, SettingsSection section, SettingId id) {
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
                shown.put(shownKey(key, id), snapshot.value());
                UICommandBuilder update = new UICommandBuilder();
                showValue(SettingsSection.kindOf(snapshot), key.row(index), snapshot, update);
                update.set(key.row(index) + " #Description.Text", section.description(snapshot));
                update.set(key.status + ".Text", SettingsSection.changedElsewhere(snapshot));
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

    private static void showValue(
            SettingsSection.Kind kind, String element, SettingSnapshot snapshot, UICommandBuilder commands) {
        String input = element + " #Input";
        switch (kind) {
            case TEXT, CHOICE -> commands.set(input + ".Value", snapshot.value());
            case TOGGLE -> commands.set(input + ".Value", Boolean.parseBoolean(snapshot.value()));
            case INFO -> commands.set(element + " #Value.Text", SettingsSection.shownValue(snapshot));
        }
    }

    private static String shownKey(Section key, SettingId id) {
        return key.name() + "|" + id;
    }

    private static EventData action(String name, Section key, String id) {
        return new EventData()
                .append(Action.KEY, name)
                .append(Action.SECTION, key.name())
                .append(Action.ID, id);
    }

    private Optional<Section> sectionOf(String name) {
        for (Section candidate : Section.values()) {
            if (candidate.name().equals(name) && sections.containsKey(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
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

    private void showKeymapMode(UICommandBuilder commands) {
        boolean server = canAddBinds && editingServerKeys;
        commands.set("#KeymapModeButtons.Visible", canAddBinds);
        commands.set("#ServerKeymaps.Visible", server);
        commands.set("#MyKeymaps.Visible", !server);
        commands.set("#KeymapModeServer.Disabled", server);
        commands.set("#KeymapModeMine.Disabled", canAddBinds && !server);
    }

    private void showTab(UICommandBuilder commands) {
        for (Tab tab : Tab.values()) {
            commands.set(tab.group + ".Visible", tab == selected);
            commands.set(tab.button + ".Disabled", tab == selected);
        }
    }

    /** Data sent by the client when a bound element is used. */
    static final class Action {

        // Plain keys: the value is sent as is. A key starting with '@' makes the client read the value from a UI
        // property instead (for example "@Value" -> "#SettingsList[2] #Input.Value"); a constant under an '@'
        // key disconnects the client.
        static final String KEY = "Action";
        static final String SECTION = "Section";
        static final String ID = "Id";
        static final String VALUE = "@Value";
        static final String CHECKED = "@Checked";
        static final String NAME = "@BindName";
        static final String KEYS = "@BindKeys";

        static final BuilderCodec<Action> CODEC = BuilderCodec.builder(Action.class, Action::new)
                .append(
                        new KeyedCodec<>(KEY, Codec.STRING),
                        (action, value) -> action.name = value,
                        action -> action.name)
                .add()
                .append(
                        new KeyedCodec<>(SECTION, Codec.STRING),
                        (action, value) -> action.section = value,
                        action -> action.section)
                .add()
                .append(new KeyedCodec<>(ID, Codec.STRING), (action, value) -> action.id = value, action -> action.id)
                .add()
                .append(
                        new KeyedCodec<>(VALUE, Codec.STRING),
                        (action, value) -> action.value = value,
                        action -> action.value)
                .add()
                .append(
                        new KeyedCodec<>(CHECKED, Codec.BOOLEAN),
                        (action, value) -> action.checked = value,
                        action -> action.checked)
                .add()
                .append(
                        new KeyedCodec<>(NAME, Codec.STRING),
                        (action, value) -> action.bindName = value,
                        action -> action.bindName)
                .add()
                .append(
                        new KeyedCodec<>(KEYS, Codec.STRING),
                        (action, value) -> action.bindKeys = value,
                        action -> action.bindKeys)
                .add()
                .build();

        private String name;
        private String section;
        private String id;
        private String value;
        private Boolean checked;
        private String bindName;
        private String bindKeys;
    }
}
