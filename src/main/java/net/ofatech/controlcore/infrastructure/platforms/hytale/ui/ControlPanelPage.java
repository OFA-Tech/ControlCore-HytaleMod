package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
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
    }

    /** Where a settings section is drawn. */
    private record Target(String list, String status) {}

    private static final Map<SettingScope, Target> TARGETS = Map.of(
            SettingScope.SERVER, new Target("#SettingsList", "#SettingsStatus"),
            SettingScope.PLAYER, new Target("#PlayerList", "#PlayerStatus"));

    /** Buttons shown for upcoming features; disabled until they are wired. */
    private static final List<String> NOT_WIRED_YET = List.of(
            "#RebindOpenPanel",
            "#ResetOpenPanel",
            "#RebindReloadServer",
            "#ResetReloadServer",
            "#RebindToggleDebug",
            "#ResetToggleDebug");

    private final ServerSettings server;
    private final Map<SettingScope, SettingsSection> sections = new EnumMap<>(SettingScope.class);
    private final Map<SettingScope, Map<SettingId, Integer>> rowIndexes = new EnumMap<>(SettingScope.class);
    private Tab selected;

    ControlPanelPage(PlayerRef player, ControlPanelContent content) {
        super(player, CustomPageLifetime.CanDismiss, Action.CODEC);
        this.server = content.server();
        this.selected = Tab.of(content.startTab());
        Optional<PlayerId> viewer = Optional.of(PlayerId.of(player.getUuid()));
        sections.put(
                SettingScope.SERVER,
                new SettingsSection(
                        content.settings(), SettingScope.SERVER, viewer, "No server settings are registered yet."));
        sections.put(
                SettingScope.PLAYER,
                new SettingsSection(
                        content.settings(),
                        SettingScope.PLAYER,
                        viewer,
                        "No mod has registered player preferences yet."));
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        commands.set("#ServerName.Text", orDash(server.serverName()));
        commands.set("#Motd.Text", orDash(server.motd()));
        commands.set("#Password.Text", server.passwordProtected() ? "Set (private server)" : "None (public server)");
        commands.set("#MaxPlayers.Text", Integer.toString(server.maxPlayers()));
        commands.set("#MaxViewRadius.Text", Integer.toString(server.maxViewRadius()));
        commands.set("#PlayTimeout.Text", describe(server.playTimeout()));
        NOT_WIRED_YET.forEach(button -> commands.set(button + ".Disabled", true));
        sections.values().forEach(section -> buildSection(section, commands, events));
        showTab(commands);

        for (Tab tab : Tab.values()) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating, tab.button, EventData.of(Action.KEY, TAB_PREFIX + tab.name()));
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of(Action.KEY, CLOSE));
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
            sectionOf(action.section).ifPresent(section -> handleSettingAction(section, name, action));
        }
    }

    private void buildSection(SettingsSection section, UICommandBuilder commands, UIEventBuilder events) {
        Target target = TARGETS.get(section.scope());
        Map<SettingId, Integer> indexes = new HashMap<>();
        rowIndexes.put(section.scope(), indexes);
        commands.set(target.status() + ".Text", "");
        List<SettingsSection.Entry> entries = section.entries();
        for (int index = 0; index < entries.size(); index++) {
            String element = target.list() + "[" + index + "]";
            switch (entries.get(index)) {
                case SettingsSection.Heading heading -> {
                    commands.append(target.list(), HEADING);
                    commands.set(element + ".Text", heading.text());
                }
                case SettingsSection.Row row -> {
                    indexes.put(row.id(), index);
                    buildRow(section.scope(), row, element, commands, events);
                }
            }
        }
    }

    private static void buildRow(
            SettingScope scope,
            SettingsSection.Row row,
            String element,
            UICommandBuilder commands,
            UIEventBuilder events) {
        SettingSnapshot snapshot = row.snapshot();
        String input = element + " #Input";
        String id = snapshot.id().toString();
        switch (row.kind()) {
            case TEXT -> {
                commands.append(TARGETS.get(scope).list(), TEXT_ROW);
                commands.set(
                        input + ".PlaceholderText", snapshot.definition().type().describe());
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        element + " #Save",
                        action(SAVE, scope, id).append(Action.VALUE, input + ".Value"));
            }
            case TOGGLE -> {
                commands.append(TARGETS.get(scope).list(), TOGGLE_ROW);
                events.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        input,
                        action(CHECK, scope, id).append(Action.CHECKED, input + ".Value"));
            }
            case CHOICE -> {
                commands.append(TARGETS.get(scope).list(), CHOICE_ROW);
                commands.set(
                        input + ".Entries",
                        SettingsSection.options(snapshot).stream()
                                .map(option -> new DropdownEntryInfo(LocalizableString.fromString(option), option))
                                .toList());
                events.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        input,
                        action(CHOOSE, scope, id).append(Action.VALUE, input + ".Value"));
            }
        }
        commands.set(element + " #Label.Text", snapshot.definition().displayName());
        commands.set(element + " #Description.Text", SettingsSection.describe(snapshot));
        showValue(row.kind(), input, snapshot, commands);
        events.addEventBinding(CustomUIEventBindingType.Activating, element + " #Reset", action(RESET, scope, id));
    }

    private void handleSettingAction(SettingsSection section, String name, Action action) {
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
        Target target = TARGETS.get(section.scope());
        UICommandBuilder update = new UICommandBuilder();
        result.onSuccess(snapshot -> {
                    update.set(target.status() + ".Text", SettingsSection.saved(snapshot));
                    Integer index = rowIndexes.get(section.scope()).get(snapshot.id());
                    if (writeBack && index != null) {
                        String element = target.list() + "[" + index + "]";
                        showValue(
                                SettingsSection.kindOf(snapshot.definition().type()),
                                element + " #Input",
                                snapshot,
                                update);
                    }
                })
                .onFailure(error -> update.set(target.status() + ".Text", error.description()));
        sendUpdate(update, false);
    }

    private static void showValue(
            SettingsSection.Kind kind, String input, SettingSnapshot snapshot, UICommandBuilder commands) {
        switch (kind) {
            case TEXT, CHOICE -> commands.set(input + ".Value", snapshot.value());
            case TOGGLE -> commands.set(input + ".Value", Boolean.parseBoolean(snapshot.value()));
        }
    }

    private static EventData action(String name, SettingScope scope, String id) {
        return new EventData()
                .append(Action.KEY, name)
                .append(Action.SECTION, scope.name())
                .append(Action.ID, id);
    }

    private Optional<SettingsSection> sectionOf(String scope) {
        for (SettingScope candidate : SettingScope.values()) {
            if (candidate.name().equals(scope)) {
                return Optional.ofNullable(sections.get(candidate));
            }
        }
        return Optional.empty();
    }

    private void selectTab(String tabName) {
        for (Tab tab : Tab.values()) {
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

    private static String describe(Duration duration) {
        if (duration.isZero()) {
            return "-";
        }
        long minutes = duration.toMinutes();
        String readable = duration.toSecondsPart() == 0 && minutes > 0
                ? minutes + (minutes == 1 ? " minute" : " minutes")
                : duration.toSeconds() + " seconds";
        return readable + " (" + duration + ")";
    }

    private static String orDash(String text) {
        return text.isBlank() ? "-" : text;
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
