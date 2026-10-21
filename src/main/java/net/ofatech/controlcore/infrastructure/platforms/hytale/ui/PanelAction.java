package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** Data the client sends when a bound element of the control panel is used. */
final class PanelAction {

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
    static final String FILTER = "@Filter";
    static final String MATCH = "@Match";
    static final String NUMBER = "@Number";
    static final String ITEM = "Item";
    static final String COLOR = "@Color";

    static final BuilderCodec<PanelAction> CODEC = BuilderCodec.builder(PanelAction.class, PanelAction::new)
            .append(new KeyedCodec<>(KEY, Codec.STRING), (action, value) -> action.name = value, action -> action.name)
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
            .append(new KeyedCodec<>(ITEM, Codec.STRING), (action, value) -> action.item = value, action -> action.item)
            .add()
            .append(
                    new KeyedCodec<>(COLOR, Codec.STRING),
                    (action, value) -> action.color = value,
                    action -> action.color)
            .add()
            .append(
                    new KeyedCodec<>(NUMBER, Codec.FLOAT),
                    (action, value) -> action.number = value == null ? null : value.doubleValue(),
                    action -> action.number == null ? null : action.number.floatValue())
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
            .append(
                    new KeyedCodec<>(FILTER, Codec.STRING),
                    (action, value) -> action.filter = value,
                    action -> action.filter)
            .add()
            .append(
                    new KeyedCodec<>(MATCH, Codec.STRING),
                    (action, value) -> action.match = value,
                    action -> action.match)
            .add()
            .build();

    String name;
    String section;
    String id;
    String value;
    Boolean checked;
    Double number;
    String item;
    String color;
    String bindName;
    String bindKeys;
    String filter;
    String match;

    /** @return the action's name; empty when the client sent none */
    String name() {
        return name != null ? name : "";
    }
}
