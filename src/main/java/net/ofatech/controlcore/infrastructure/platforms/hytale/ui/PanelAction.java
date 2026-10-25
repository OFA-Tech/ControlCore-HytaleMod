package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

final class PanelAction {

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
    static final String EDIT = "@Edit";

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
            .append(new KeyedCodec<>(EDIT, Codec.STRING), (action, value) -> action.edit = value, action -> action.edit)
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
            .append(
                    new KeyedCodec<>("@RuleName", Codec.STRING),
                    (action, value) -> action.rule.put("Name", value),
                    action -> action.rule.get("Name"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleTrigger", Codec.STRING),
                    (action, value) -> action.rule.put("Trigger", value),
                    action -> action.rule.get("Trigger"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleTriggerValue", Codec.STRING),
                    (action, value) -> action.rule.put("TriggerValue", value),
                    action -> action.rule.get("TriggerValue"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleCondition", Codec.STRING),
                    (action, value) -> action.rule.put("Condition", value),
                    action -> action.rule.get("Condition"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleConditionValue", Codec.STRING),
                    (action, value) -> action.rule.put("ConditionValue", value),
                    action -> action.rule.get("ConditionValue"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleAction", Codec.STRING),
                    (action, value) -> action.rule.put("Action", value),
                    action -> action.rule.get("Action"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleActionValue", Codec.STRING),
                    (action, value) -> action.rule.put("ActionValue", value),
                    action -> action.rule.get("ActionValue"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleAction2", Codec.STRING),
                    (action, value) -> action.rule.put("Action2", value),
                    action -> action.rule.get("Action2"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleAction2Value", Codec.STRING),
                    (action, value) -> action.rule.put("Action2Value", value),
                    action -> action.rule.get("Action2Value"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleCooldown", Codec.STRING),
                    (action, value) -> action.rule.put("Cooldown", value),
                    action -> action.rule.get("Cooldown"))
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
    String edit;
    String bindName;
    String bindKeys;
    String filter;
    String match;
    final java.util.Map<String, String> rule = new java.util.HashMap<>();

    static String ruleKey(String field) {
        return "@Rule" + field;
    }

    String rule(String field) {
        String value = rule.get(field);
        return value == null ? "" : value;
    }

    String name() {
        return name != null ? name : "";
    }
}
