package net.ofatech.controlcore.domain.model.automation;

import java.util.List;
import net.ofatech.controlcore.domain.guard.Guard;

public record Rule(
        String id,
        String name,
        boolean enabled,
        Trigger trigger,
        String triggerValue,
        List<Condition> conditions,
        List<Action> actions,
        int cooldownSeconds) {

    public record Condition(ConditionKind kind, String value) {

        public Condition {
            Guard.notNull(kind, "kind");
            value = value == null ? "" : value.strip();
        }
    }

    public record Action(ActionKind kind, String value) {

        public Action {
            Guard.notNull(kind, "kind");
            value = value == null ? "" : value.strip();
        }
    }

    public Rule {
        Guard.notBlank(id, "id");
        Guard.notBlank(name, "name");
        Guard.notNull(trigger, "trigger");
        triggerValue = triggerValue == null ? "" : triggerValue.strip();
        conditions = List.copyOf(conditions);
        actions = List.copyOf(actions);
    }

    public Rule withEnabled(boolean on) {
        return new Rule(id, name, on, trigger, triggerValue, conditions, actions, cooldownSeconds);
    }
}
