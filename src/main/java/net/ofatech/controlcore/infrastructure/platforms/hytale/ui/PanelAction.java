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
                    new KeyedCodec<>("@RuleCooldown", Codec.STRING),
                    (action, value) -> action.rule.put("Cooldown", value),
                    action -> action.rule.get("Cooldown"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA0Kind", Codec.STRING),
                    (action, value) -> action.rule.put("A0Kind", value),
                    action -> action.rule.get("A0Kind"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA0Value", Codec.STRING),
                    (action, value) -> action.rule.put("A0Value", value),
                    action -> action.rule.get("A0Value"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA1Kind", Codec.STRING),
                    (action, value) -> action.rule.put("A1Kind", value),
                    action -> action.rule.get("A1Kind"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA1Value", Codec.STRING),
                    (action, value) -> action.rule.put("A1Value", value),
                    action -> action.rule.get("A1Value"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA2Kind", Codec.STRING),
                    (action, value) -> action.rule.put("A2Kind", value),
                    action -> action.rule.get("A2Kind"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA2Value", Codec.STRING),
                    (action, value) -> action.rule.put("A2Value", value),
                    action -> action.rule.get("A2Value"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA3Kind", Codec.STRING),
                    (action, value) -> action.rule.put("A3Kind", value),
                    action -> action.rule.get("A3Kind"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA3Value", Codec.STRING),
                    (action, value) -> action.rule.put("A3Value", value),
                    action -> action.rule.get("A3Value"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA4Kind", Codec.STRING),
                    (action, value) -> action.rule.put("A4Kind", value),
                    action -> action.rule.get("A4Kind"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA4Value", Codec.STRING),
                    (action, value) -> action.rule.put("A4Value", value),
                    action -> action.rule.get("A4Value"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA5Kind", Codec.STRING),
                    (action, value) -> action.rule.put("A5Kind", value),
                    action -> action.rule.get("A5Kind"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA5Value", Codec.STRING),
                    (action, value) -> action.rule.put("A5Value", value),
                    action -> action.rule.get("A5Value"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA6Kind", Codec.STRING),
                    (action, value) -> action.rule.put("A6Kind", value),
                    action -> action.rule.get("A6Kind"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA6Value", Codec.STRING),
                    (action, value) -> action.rule.put("A6Value", value),
                    action -> action.rule.get("A6Value"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA7Kind", Codec.STRING),
                    (action, value) -> action.rule.put("A7Kind", value),
                    action -> action.rule.get("A7Kind"))
            .add()
            .append(
                    new KeyedCodec<>("@RuleA7Value", Codec.STRING),
                    (action, value) -> action.rule.put("A7Value", value),
                    action -> action.rule.get("A7Value"))
            .add()
            .append(
                    new KeyedCodec<>("@PlayerKind", Codec.STRING),
                    (action, value) -> action.rule.put("PlayerKind", value),
                    action -> action.rule.get("PlayerKind"))
            .add()
            .append(
                    new KeyedCodec<>("@PlayerGroup", Codec.STRING),
                    (action, value) -> action.rule.put("PlayerGroup", value),
                    action -> action.rule.get("PlayerGroup"))
            .add()
            .append(
                    new KeyedCodec<>("@PlayerName", Codec.STRING),
                    (action, value) -> action.rule.put("PlayerName", value),
                    action -> action.rule.get("PlayerName"))
            .add()
            .append(
                    new KeyedCodec<>("@PlayerNode", Codec.STRING),
                    (action, value) -> action.rule.put("PlayerNode", value),
                    action -> action.rule.get("PlayerNode"))
            .add()
            .append(
                    new KeyedCodec<>("@PlayerLength", Codec.STRING),
                    (action, value) -> action.rule.put("PlayerLength", value),
                    action -> action.rule.get("PlayerLength"))
            .add()
            .append(
                    new KeyedCodec<>("@PlayerReason", Codec.STRING),
                    (action, value) -> action.rule.put("PlayerReason", value),
                    action -> action.rule.get("PlayerReason"))
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

    static String playerKey(String field) {
        return "@Player" + field;
    }

    String player(String field) {
        return rule("Player" + field);
    }

    String name() {
        return name != null ? name : "";
    }
}
