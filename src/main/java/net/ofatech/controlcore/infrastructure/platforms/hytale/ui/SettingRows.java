package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.List;
import java.util.Locale;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;

/**
 * The rows of the control panel's setting lists: which row document each kind of setting appends, the events its
 * controls send, and how a value is shown in it.
 */
final class SettingRows {

    // Events sent by the rows; the page dispatches them by these names.
    static final String SAVE = "Save";
    static final String CHECK = "Check";
    static final String CHOOSE = "Choose";
    static final String RESET = "Reset";
    /** The slider moved: show its value in the text field (nothing is saved yet). */
    static final String SLIDE = "Slide";
    /** The slider was let go: save its value. */
    static final String SLID = "Slid";
    /** A colour was picked in a colour row: it goes into the row's text field. */
    static final String PICK = "Pick";
    /** A colour was picked in a text row with a colour: only a hint until Save. */
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
    private static final String CUSTOM_BIND_ROW = "Pages/ControlCore/SettingCustomBindRow.ui";

    private SettingRows() {}

    static void heading(PanelSection key, String element, String text, UICommandBuilder commands) {
        commands.append(key.list, HEADING);
        commands.set(element + ".Text", text);
    }

    /**
     * Appends a setting's row and binds its controls.
     *
     * @param customBind an admin's own key bind: the row also gets Edit and Remove buttons
     */
    static void build(
            PanelSection key,
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
                    commands.append(key.list, customBind ? CUSTOM_BIND_ROW : TEXT_ROW);
                    if (customBind) {
                        events.addEventBinding(
                                CustomUIEventBindingType.Activating,
                                element + " #Edit",
                                event(KeyBindForm.EDIT, key, id));
                        events.addEventBinding(
                                CustomUIEventBindingType.Activating,
                                element + " #Remove",
                                event(KeyBindForm.REMOVE, key, id));
                    }
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
                // The picker has no Value property; its colour is read and set as Color ("#rrggbb").
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
    }

    /** Shows {@code snapshot}'s value in the controls of a row of {@code kind}. */
    static void showValue(
            SettingsSection section,
            SettingsSection.Kind kind,
            String element,
            SettingSnapshot snapshot,
            UICommandBuilder commands) {
        String input = element + " #Input";
        switch (kind) {
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

    /** Sets a row's colour picker (its markup property is Color; it has no Value). */
    static void showPicker(String element, SettingSnapshot color, UICommandBuilder commands) {
        String hex = pickedHex(color.value());
        if (hex != null) {
            commands.set(element + " #Picker.Color", hex);
        }
    }

    /** @return {@code #rrggbb} from the picker's colour ({@code #rrggbb}, {@code #rrggbbaa} or {@code rrggbb}) */
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

    /** Sliders take int values; float sliders take float values. */
    private static void setSliderNumber(
            UICommandBuilder commands, String property, SettingsSection.Kind kind, double value) {
        if (kind == SettingsSection.Kind.INTEGER) {
            commands.set(property, (int) Math.round(value));
        } else {
            commands.set(property, (float) value);
        }
    }
}
