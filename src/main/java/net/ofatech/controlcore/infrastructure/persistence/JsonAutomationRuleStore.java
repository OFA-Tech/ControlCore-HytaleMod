package net.ofatech.controlcore.infrastructure.persistence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.automation.ActionKind;
import net.ofatech.controlcore.domain.model.automation.ConditionKind;
import net.ofatech.controlcore.domain.model.automation.Rule;
import net.ofatech.controlcore.domain.model.automation.Trigger;
import net.ofatech.controlcore.domain.ports.config.AutomationRuleStore;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class JsonAutomationRuleStore implements AutomationRuleStore {

    private final JsonFile file;
    private final Log log;
    private List<Rule> cached;

    public JsonAutomationRuleStore(Path file, Log log) {
        this.file = new JsonFile(file, log, "automation rules");
        this.log = log;
    }

    @Override
    public synchronized List<Rule> all() {
        if (cached == null) {
            cached = read();
        }
        return cached;
    }

    @Override
    public synchronized boolean saveAll(List<Rule> rules) {
        List<FileRule> form = new ArrayList<>();
        for (Rule rule : rules) {
            form.add(FileRule.of(rule));
        }
        if (!file.write(form.toArray(FileRule[]::new), FileRule[].class)) {
            return false;
        }
        cached = List.copyOf(rules);
        return true;
    }

    private List<Rule> read() {
        List<Rule> rules = new ArrayList<>();
        for (FileRule rule : file.<FileRule[]>read(FileRule[].class).orElse(new FileRule[0])) {
            Optional<Rule> parsed = rule == null ? Optional.empty() : rule.toRule();
            if (parsed.isPresent()) {
                rules.add(parsed.get());
            } else {
                log.warn("Skipping an automation rule in " + file.path() + " that could not be read");
            }
        }
        return rules;
    }

    private static final class FileRule {
        private String id;
        private String name;
        private boolean enabled;
        private String trigger;
        private String triggerValue;
        private List<FilePart> conditions;
        private List<FilePart> actions;
        private int cooldownSeconds;

        static FileRule of(Rule rule) {
            FileRule form = new FileRule();
            form.id = rule.id();
            form.name = rule.name();
            form.enabled = rule.enabled();
            form.trigger = rule.trigger().id();
            form.triggerValue = rule.triggerValue();
            form.conditions = new ArrayList<>();
            for (Rule.Condition condition : rule.conditions()) {
                form.conditions.add(new FilePart(condition.kind().id(), condition.value()));
            }
            form.actions = new ArrayList<>();
            for (Rule.Action action : rule.actions()) {
                form.actions.add(new FilePart(action.kind().id(), action.value()));
            }
            form.cooldownSeconds = rule.cooldownSeconds();
            return form;
        }

        Optional<Rule> toRule() {
            Optional<Trigger> parsed = Trigger.of(trigger);
            if (id == null || id.isBlank() || name == null || name.isBlank() || parsed.isEmpty()) {
                return Optional.empty();
            }
            List<Rule.Condition> conditionList = new ArrayList<>();
            for (FilePart part : conditions == null ? List.<FilePart>of() : conditions) {
                Optional<ConditionKind> kind = part == null ? Optional.empty() : ConditionKind.of(part.kind);
                if (kind.isEmpty()) {
                    return Optional.empty();
                }
                conditionList.add(new Rule.Condition(kind.get(), part.value));
            }
            List<Rule.Action> actionList = new ArrayList<>();
            for (FilePart part : actions == null ? List.<FilePart>of() : actions) {
                Optional<ActionKind> kind = part == null ? Optional.empty() : ActionKind.of(part.kind);
                if (kind.isEmpty()) {
                    return Optional.empty();
                }
                actionList.add(new Rule.Action(kind.get(), part.value));
            }
            return Optional.of(new Rule(
                    id,
                    name,
                    enabled,
                    parsed.get(),
                    triggerValue,
                    conditionList,
                    actionList,
                    Math.max(0, cooldownSeconds)));
        }
    }

    private static final class FilePart {
        private String kind;
        private String value;

        FilePart(String kind, String value) {
            this.kind = kind;
            this.value = value;
        }
    }
}
