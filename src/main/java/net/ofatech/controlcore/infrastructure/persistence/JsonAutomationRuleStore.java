package net.ofatech.controlcore.infrastructure.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Log log;
    private List<Rule> cached;

    public JsonAutomationRuleStore(Path file, Log log) {
        this.file = file;
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
    public synchronized void saveAll(List<Rule> rules) {
        cached = List.copyOf(rules);
        List<FileRule> form = new ArrayList<>();
        for (Rule rule : rules) {
            form.add(FileRule.of(rule));
        }
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(form), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Could not save " + file, e);
        }
    }

    private List<Rule> read() {
        List<Rule> rules = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return rules;
        }
        try {
            FileRule[] saved = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FileRule[].class);
            if (saved != null) {
                for (FileRule rule : saved) {
                    Optional<Rule> parsed = rule == null ? Optional.empty() : rule.toRule();
                    if (parsed.isPresent()) {
                        rules.add(parsed.get());
                    } else {
                        log.warn("Skipping an automation rule in " + file + " that could not be read");
                    }
                }
            }
        } catch (IOException | JsonParseException e) {
            log.error("Could not read " + file, e);
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
