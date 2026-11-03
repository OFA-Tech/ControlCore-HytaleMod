package net.ofatech.controlcore.application.automation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ofatech.controlcore.domain.model.automation.ActionKind;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.automation.ConditionKind;
import net.ofatech.controlcore.domain.model.automation.Trigger;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

final class RuleSpec {

    static final String FIELDS = "name, when, if, do, cooldown, on";

    record Parsed(AutomationView.Form form, Optional<Boolean> enabled) {}

    private RuleSpec() {}

    static Result<Parsed> parse(String text, Optional<AutomationView.Form> base) {
        String name = base.map(AutomationView.Form::name).orElse("");
        String trigger = base.map(AutomationView.Form::trigger).orElse("");
        String triggerValue = base.map(AutomationView.Form::triggerValue).orElse("");
        String condition = base.map(AutomationView.Form::condition).orElse("none");
        String conditionValue = base.map(AutomationView.Form::conditionValue).orElse("");
        String cooldown = base.map(AutomationView.Form::cooldown).orElse("");
        List<AutomationView.Step> actions = new ArrayList<>();
        boolean newActions = false;
        Optional<Boolean> enabled = Optional.empty();
        for (String piece : (text == null ? "" : text).split(";")) {
            String field = piece.strip();
            if (field.isEmpty()) {
                continue;
            }
            int equals = field.indexOf('=');
            if (equals <= 0) {
                return bad(field);
            }
            String key = field.substring(0, equals).strip().toLowerCase(Locale.ROOT);
            String value = field.substring(equals + 1).strip();
            switch (key) {
                case "name" -> name = value;
                case "when" -> {
                    String[] parts = split(value);
                    if (Trigger.of(parts[0]).isEmpty()) {
                        return unknown("Automation.UnknownTrigger", parts[0], triggers());
                    }
                    trigger = parts[0];
                    triggerValue = parts[1];
                }
                case "if" -> {
                    String[] parts = split(value);
                    if (parts[0].isEmpty() || parts[0].equalsIgnoreCase("none")) {
                        condition = "none";
                        conditionValue = "";
                    } else if (ConditionKind.of(parts[0]).isEmpty()) {
                        return unknown(
                                "Automation.UnknownCondition",
                                parts[0],
                                Arrays.stream(ConditionKind.values()).map(ConditionKind::id));
                    } else {
                        condition = parts[0];
                        conditionValue = parts[1];
                    }
                }
                case "do" -> {
                    String[] parts = split(value);
                    if (ActionKind.of(parts[0]).isEmpty()) {
                        return unknown(
                                "Automation.UnknownAction",
                                parts[0],
                                Arrays.stream(ActionKind.values()).map(ActionKind::id));
                    }
                    if (!newActions) {
                        newActions = true;
                        actions.clear();
                    }
                    actions.add(new AutomationView.Step(parts[0], parts[1]));
                }
                case "cooldown" -> cooldown = value;
                case "on" -> {
                    Optional<Boolean> on = switch (value.toLowerCase(Locale.ROOT)) {
                        case "yes", "true", "on" -> Optional.of(true);
                        case "no", "false", "off" -> Optional.of(false);
                        default -> Optional.empty();
                    };
                    if (on.isEmpty()) {
                        return bad(field);
                    }
                    enabled = on;
                }
                default -> {
                    return bad(field);
                }
            }
        }
        if (trigger.isEmpty()) {
            return Result.failure(Error.validation(
                    "Automation.UnknownTrigger",
                    Text.key("controlcore.errors.automation.no_trigger", "A rule needs when=, one of: {choices}.")
                            .with("choices", triggers().collect(Collectors.joining(", ")))));
        }
        if (!newActions) {
            base.ifPresent(form -> actions.addAll(form.actions()));
        }
        return Result.success(new Parsed(
                new AutomationView.Form(name, trigger, triggerValue, condition, conditionValue, actions, cooldown),
                enabled));
    }

    private static Stream<String> triggers() {
        return Arrays.stream(Trigger.values()).map(Trigger::id);
    }

    private static String[] split(String value) {
        int colon = value.indexOf(':');
        return colon < 0
                ? new String[] {value.strip(), ""}
                : new String[] {
                    value.substring(0, colon).strip(),
                    value.substring(colon + 1).strip()
                };
    }

    private static <T> Result<T> bad(String field) {
        return Result.failure(Error.validation(
                "Automation.BadField",
                Text.key(
                                "controlcore.errors.automation.bad_field",
                                "'{field}' is not a rule field. Write key=value parts joined with ;, using {fields}.")
                        .with("field", field, "fields", FIELDS)));
    }

    private static <T> Result<T> unknown(String code, String kind, Stream<String> ids) {
        return Result.failure(Error.validation(
                code,
                Text.key("controlcore.errors.automation.unknown_kind", "'{kind}' is not one of: {choices}.")
                        .with("kind", kind, "choices", ids.collect(Collectors.joining(", ")))));
    }
}
