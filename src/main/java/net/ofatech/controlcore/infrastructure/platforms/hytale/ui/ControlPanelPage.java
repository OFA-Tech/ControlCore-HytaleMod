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

    private static final String CLOSE = "Close";
    private static final String TAB_PREFIX = "Tab:";
    private static final String SAVE = "Save";
    private static final String CHECK = "Check";
    private static final String CHOOSE = "Choose";
    private static final String RESET = "Reset";

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
        PREFS("#PlayerList", "#PlayerStatus");

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

    /** Buttons shown for upcoming features; disabled until they are wired. */
    private static final List<String> NOT_WIRED_YET = List.of(
            "#RebindOpenPanel",
            "#ResetOpenPanel",
            "#RebindReloadServer",
            "#ResetReloadServer",
            "#RebindToggleDebug",
            "#ResetToggleDebug");

    private final Log log;
    private final Set<Tab> tabs = EnumSet.noneOf(Tab.class);
    private final Map<Section, SettingsSection> sections = new EnumMap<>(Section.class);
    private final Map<Section, Map<SettingId, Integer>> rowIndexes = new EnumMap<>(Section.class);
    /** Value each row currently shows, to skip change notifications the panel caused itself (world thread). */
    private final Map<SettingId, String> shown = new HashMap<>();

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private Tab selected;

    ControlPanelPage(PlayerRef player, ControlPanelRequest request, ControlPanelContent content, Log log) {
        super(player, CustomPageLifetime.CanDismiss, Action.CODEC);
        this.log = log;
        ControlPanelRequest shownSections = request.isEmpty() ? ControlPanelRequest.PLAYER_PREFERENCES : request;
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
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        NOT_WIRED_YET.forEach(button -> commands.set(button + ".Disabled", true));
        sections.forEach((key, section) -> buildSection(key, section, commands, events));
        for (Tab tab : Tab.values()) {
            commands.set(tab.button + ".Visible", tabs.contains(tab));
        }
        commands.set("#TabButtons.Visible", tabs.size() > 1);
        showTab(commands);

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
                    shown.put(row.id(), row.snapshot().value());
                    buildRow(key, row, element, commands, events);
                }
            }
        }
    }

    private static void buildRow(
            Section key, SettingsSection.Row row, String element, UICommandBuilder commands, UIEventBuilder events) {
        SettingSnapshot snapshot = row.snapshot();
        String input = element + " #Input";
        String id = snapshot.id().toString();
        switch (row.kind()) {
            case TEXT -> {
                commands.append(key.list, TEXT_ROW);
                commands.set(
                        input + ".PlaceholderText", snapshot.definition().type().describe());
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
        commands.set(element + " #Description.Text", SettingsSection.describe(snapshot));
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
                    shown.put(snapshot.id(), snapshot.value());
                    update.set(key.status + ".Text", SettingsSection.saved(snapshot));
                    Integer index = rowIndexes.get(key).get(snapshot.id());
                    if (writeBack && index != null) {
                        showValue(SettingsSection.kindOf(snapshot), key.row(index), snapshot, update);
                    }
                })
                .onFailure(error -> update.set(key.status + ".Text", error.description()));
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
                if (snapshot.value().equals(shown.get(id))) {
                    return;
                }
                shown.put(id, snapshot.value());
                UICommandBuilder update = new UICommandBuilder();
                showValue(SettingsSection.kindOf(snapshot), key.row(index), snapshot, update);
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
                .build();

        private String name;
        private String section;
        private String id;
        private String value;
        private Boolean checked;
    }
}
