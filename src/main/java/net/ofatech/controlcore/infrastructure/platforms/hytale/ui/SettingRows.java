package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Translator;

final class SettingRows {

    static final String SAVE = "Save";
    static final String CHECK = "Check";
    static final String CHOOSE = "Choose";
    static final String RESET = "Reset";
    static final String SLIDE = "Slide";
    static final String SLID = "Slid";
    static final String PICK = "Pick";
    static final String PICK_INLINE = "PickInline";

    static final String SAVE_COLORED = "SaveColored";
    static final String RESET_COLORED = "ResetColored";
    static final String ADD_ITEM = "AddItem";
    static final String REMOVE_ITEM = "RemoveItem";

    private static final String HEADING = "Pages/ControlCore/SettingCategory.ui";
    private static final String TEXT_ROW = "Pages/ControlCore/SettingTextRow.ui";
    private static final String TOGGLE_ROW = "Pages/ControlCore/SettingToggleRow.ui";
    private static final String INTEGER_ROW = "Pages/ControlCore/SettingIntegerRow.ui";
    private static final String DECIMAL_ROW = "Pages/ControlCore/SettingDecimalRow.ui";
    private static final String COLOR_ROW = "Pages/ControlCore/SettingColorRow.ui";
    private static final String TEXT_COLOR_ROW = "Pages/ControlCore/SettingTextColorRow.ui";
    private static final String LIST_ROW = "Pages/ControlCore/SettingListRow.ui";
    private static final String LIST_ITEM = "Pages/ControlCore/SettingListItem.ui";
    private static final String CHOICE_ROW = "Pages/ControlCore/SettingChoiceRow.ui";
    private static final String INFO_ROW = "Pages/ControlCore/SettingInfoRow.ui";
    private static final String KEY_ROW = "Pages/ControlCore/SettingKeyRow.ui";
    static final String PICK_KEYS = "PickKeys";
    static final String PICK_GAME_ACTION = "PickGameAction";
    static final String DETECT = "DetectKey";

    private SettingRows() {}

    static final String TYPED = "Typed";
    static final String UNDO = "Undo";

    private static final String ICONS = "Pages/ControlCore/Icons/";

    enum State {
        RESTART("Restart"),
        ERROR("Error"),
        UNSAVED("Unsaved"),
        CHANGED("Changed"),
        LOCKED("Locked"),
        DEFAULT("Default");

        private final String icon;

        State(String icon) {
            this.icon = icon;
        }

        String icon() {
            return ICONS + icon + ".png";
        }

        String legendButton() {
            return "#Legend" + icon;
        }
    }

    static void heading(PanelSection key, String element, String text, String count, UICommandBuilder commands) {
        commands.append(key.list, HEADING);
        commands.set(element + " #Text.Text", text);
        commands.set(element + " #Count.Text", count);
    }

    static void showKeys(Translator tr, String hold, String key, String value, UICommandBuilder commands) {
        KeyPicker.Picked picked = KeyPicker.read(value);
        commands.set(hold + ".Entries", entries(KeyPicker.holds(tr)));
        commands.set(key + ".Entries", entries(KeyPicker.keys(tr, picked)));
        commands.set(hold + ".Value", picked.hold());
        commands.set(key + ".Value", picked.key());
    }

    private static List<DropdownEntryInfo> entries(List<KeyPicker.Option> options) {
        return options.stream()
                .map(option -> new DropdownEntryInfo(LocalizableString.fromString(option.label()), option.value()))
                .toList();
    }

    static boolean canUndo(SettingsSection.Row row) {
        return row.kind() != SettingsSection.Kind.INFO && row.color().isEmpty();
    }

    static boolean tracksTyping(SettingsSection.Kind kind) {
        return switch (kind) {
            case TEXT, INTEGER, DECIMAL, COLOR -> true;
            default -> false;
        };
    }

    static void build(
            PanelSection key,
            SettingsSection section,
            SettingsSection.Row row,
            String element,
            boolean customBind,
            Optional<GameActionMode> gameAction,
            UICommandBuilder commands,
            UIEventBuilder events) {
        SettingSnapshot snapshot = row.snapshot();
        String input = element + " #Input";
        String id = snapshot.id().toString();
        switch (row.kind()) {
            case KEYS -> {
                commands.append(key.list, KEY_ROW);
                commands.set(element + " #BindButtons.Visible", customBind);
                if (customBind) {
                    events.addEventBinding(
                            CustomUIEventBindingType.Activating, element + " #Edit", event(KeyBindForm.EDIT, key, id));
                    events.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            element + " #Remove",
                            event(KeyBindForm.REMOVE, key, id));
                }
                gameAction.ifPresent(mode -> {
                    Translator tr = section.translator();
                    commands.set(element + " #GameAction.Visible", true);
                    commands.set(
                            element + " #GameAction.Entries",
                            Arrays.stream(GameActionMode.values())
                                    .map(option -> new DropdownEntryInfo(
                                            LocalizableString.fromString(gameActionLabel(tr, option)), option.id()))
                                    .toList());
                    commands.set(element + " #GameAction.Value", mode.id());
                    events.addEventBinding(
                            CustomUIEventBindingType.ValueChanged,
                            element + " #GameAction",
                            event(PICK_GAME_ACTION, key, id).append(PanelAction.VALUE, element + " #GameAction.Value"));
                });
                events.addEventBinding(
                        CustomUIEventBindingType.Activating, element + " #Detect", event(DETECT, key, id));
                for (String part : List.of(" #Hold", " #Key")) {
                    events.addEventBinding(
                            CustomUIEventBindingType.ValueChanged,
                            element + part,
                            event(PICK_KEYS, key, id)
                                    .append(PanelAction.VALUE, element + " #Hold.Value")
                                    .append(PanelAction.KEYS, element + " #Key.Value"));
                }
            }
            case TEXT -> {
                if (row.color().isPresent()) {
                    commands.append(key.list, TEXT_COLOR_ROW);
                    events.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            element + " #Save",
                            event(SAVE_COLORED, key, id)
                                    .append(
                                            PanelAction.ITEM,
                                            row.color().get().id().toString())
                                    .append(PanelAction.VALUE, input + ".Value")
                                    .append(PanelAction.COLOR, element + " #Picker.Color"));
                    events.addEventBinding(
                            CustomUIEventBindingType.ValueChanged,
                            element + " #Picker",
                            event(PICK_INLINE, key, id).append(PanelAction.VALUE, element + " #Picker.Color"),
                            false);
                } else {
                    commands.append(key.list, TEXT_ROW);
                    bindSave(key, element, id, events);
                }
                commands.set(input + ".PlaceholderText", section.placeholder(snapshot));
            }
            case INTEGER, DECIMAL -> {
                commands.append(key.list, row.kind() == SettingsSection.Kind.INTEGER ? INTEGER_ROW : DECIMAL_ROW);
                SettingsSection.Slider slider = SettingsSection.Slider.of(snapshot);
                String bar = element + " #Slider";
                setSliderNumber(commands, bar + ".Min", row.kind(), slider.min());
                setSliderNumber(commands, bar + ".Max", row.kind(), slider.max());
                setSliderNumber(commands, bar + ".Step", row.kind(), slider.step());
                commands.set(input + ".PlaceholderText", section.placeholder(snapshot));
                bindSave(key, element, id, events);
                events.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        bar,
                        event(SLIDE, key, id).append(PanelAction.NUMBER, bar + ".Value"),
                        false);
                events.addEventBinding(
                        CustomUIEventBindingType.MouseButtonReleased,
                        bar,
                        event(SLID, key, id).append(PanelAction.NUMBER, bar + ".Value"));
            }
            case COLOR -> {
                commands.append(key.list, COLOR_ROW);
                commands.set(input + ".PlaceholderText", section.placeholder(snapshot));
                bindSave(key, element, id, events);
                events.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        element + " #Picker",
                        event(PICK, key, id).append(PanelAction.VALUE, element + " #Picker.Color"),
                        false);
            }
            case LIST -> {
                commands.append(key.list, LIST_ROW);
                commands.set(input + ".PlaceholderText", section.placeholder(snapshot));
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        element + " #Add",
                        event(ADD_ITEM, key, id).append(PanelAction.VALUE, input + ".Value"));
                List<String> items = SettingsSection.items(snapshot);
                for (int item = 0; item < items.size(); item++) {
                    commands.append(element + " #Items", LIST_ITEM);
                    String itemElement = element + " #Items[" + item + "]";
                    commands.set(itemElement + " #Text.Text", items.get(item));
                    events.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            itemElement + " #Remove",
                            event(REMOVE_ITEM, key, id).append(PanelAction.ITEM, Integer.toString(item)));
                }
            }
            case TOGGLE -> {
                commands.append(key.list, TOGGLE_ROW);
                events.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        input,
                        event(CHECK, key, id).append(PanelAction.CHECKED, input + ".Value"));
            }
            case CHOICE -> {
                commands.append(key.list, CHOICE_ROW);
                commands.set(
                        input + ".Entries",
                        SettingsSection.options(snapshot).stream()
                                .map(option -> new DropdownEntryInfo(
                                        LocalizableString.fromString(section.option(snapshot, option)), option))
                                .toList());
                events.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        input,
                        event(CHOOSE, key, id).append(PanelAction.VALUE, input + ".Value"));
            }
            case INFO -> commands.append(key.list, INFO_ROW);
        }
        commands.set(element + " #Label.Text", section.name(snapshot));
        commands.set(element + " #Description.Text", section.description(snapshot));
        showValue(section, row.kind(), element, snapshot, commands);
        row.color().ifPresent(color -> showPicker(element, color, commands));
        if (row.color().isPresent()) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    element + " #Reset",
                    event(RESET_COLORED, key, id)
                            .append(PanelAction.ITEM, row.color().get().id().toString()));
        } else if (row.kind() != SettingsSection.Kind.INFO) {
            events.addEventBinding(CustomUIEventBindingType.Activating, element + " #Reset", event(RESET, key, id));
        }
        if (canUndo(row)) {
            events.addEventBinding(CustomUIEventBindingType.Activating, element + " #Undo", event(UNDO, key, id));
        }
        if (tracksTyping(row.kind())) {
            events.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    input,
                    event(TYPED, key, id).append(PanelAction.VALUE, input + ".Value"),
                    false);
        }
    }

    static void showValue(
            SettingsSection section,
            SettingsSection.Kind kind,
            String element,
            SettingSnapshot snapshot,
            UICommandBuilder commands) {
        String input = element + " #Input";
        switch (kind) {
            case KEYS ->
                showKeys(section.translator(), element + " #Hold", element + " #Key", snapshot.value(), commands);
            case TEXT, CHOICE -> commands.set(input + ".Value", snapshot.value());
            case COLOR -> {
                commands.set(input + ".Value", snapshot.value());
                showPicker(element, snapshot, commands);
            }
            case LIST -> commands.set(input + ".Value", "");
            case INTEGER, DECIMAL -> {
                commands.set(input + ".Value", snapshot.value());
                SettingsSection.Slider slider = SettingsSection.Slider.of(snapshot);
                setSliderNumber(commands, element + " #Slider.Value", kind, slider.position(snapshot.value()));
            }
            case TOGGLE -> commands.set(input + ".Value", Boolean.parseBoolean(snapshot.value()));
            case INFO -> commands.set(element + " #Value.Text", section.shownValue(snapshot));
        }
    }

    static String gameActionLabel(Translator tr, GameActionMode mode) {
        return tr.t("controlcore.keys.game_action." + mode.id());
    }

    static void showPicker(String element, SettingSnapshot color, UICommandBuilder commands) {
        String hex = pickedHex(color.value());
        if (hex != null) {
            commands.set(element + " #Picker.Color", hex);
        }
    }

    static String pickedHex(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.strip();
        digits = digits.startsWith("#") ? digits.substring(1) : digits;
        if (!digits.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            return null;
        }
        return "#" + digits.substring(0, 6).toLowerCase(Locale.ROOT);
    }

    static EventData event(String name, PanelSection key, String id) {
        return new EventData()
                .append(PanelAction.KEY, name)
                .append(PanelAction.SECTION, key.name())
                .append(PanelAction.ID, id);
    }

    private static void bindSave(PanelSection key, String element, String id, UIEventBuilder events) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                element + " #Save",
                event(SAVE, key, id).append(PanelAction.VALUE, element + " #Input.Value"));
    }

    private static void setSliderNumber(
            UICommandBuilder commands, String property, SettingsSection.Kind kind, double value) {
        if (kind == SettingsSection.Kind.INTEGER) {
            commands.set(property, (int) Math.round(value));
        } else {
            commands.set(property, (float) value);
        }
    }
}
