package net.ofatech.controlcore.application.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.Test;

class RuleSpecTest {

    private static final AutomationView.Form BASE = new AutomationView.Form(
            "Greeter",
            "join",
            "",
            "has-permission",
            "vip",
            List.of(new AutomationView.Step("message-player", "Hi"), new AutomationView.Step("message-all", "Yo")),
            "30");

    private static RuleSpec.Parsed ok(Result<RuleSpec.Parsed> result) {
        assertThat(result.isSuccess()).as("%s", result).isTrue();
        return result.value();
    }

    private static String code(Result<RuleSpec.Parsed> result) {
        assertThat(result.isFailure()).as("%s", result).isTrue();
        return result.error().code();
    }

    @Test
    void anEditKeepsEveryFieldItDoesNotName() {
        RuleSpec.Parsed parsed = ok(RuleSpec.parse("cooldown=60", Optional.of(BASE)));

        assertThat(parsed.form())
                .isEqualTo(
                        new AutomationView.Form("Greeter", "join", "", "has-permission", "vip", BASE.actions(), "60"));
        assertThat(parsed.enabled()).isEmpty();
    }

    @Test
    void theFirstDoReplacesAllOldActionsAndLaterOnesAppend() {
        RuleSpec.Parsed parsed =
                ok(RuleSpec.parse("do=toast-player:success:Hey; do=message-all:a=b", Optional.of(BASE)));

        assertThat(parsed.form().actions())
                .containsExactly(
                        new AutomationView.Step("toast-player", "success:Hey"),
                        new AutomationView.Step("message-all", "a=b"));
    }

    @Test
    void ifNoneClearsTheConditionAndItsValue() {
        AutomationView.Form form =
                ok(RuleSpec.parse("if=none", Optional.of(BASE))).form();
        assertThat(form.condition()).isEqualTo("none");
        assertThat(form.conditionValue()).isEmpty();

        AutomationView.Form blank = ok(RuleSpec.parse("if=", Optional.of(BASE))).form();
        assertThat(blank.condition()).isEqualTo("none");
    }

    @Test
    void keysAreCaseInsensitiveAndBlankPartsAreIgnored() {
        AutomationView.Form form = ok(RuleSpec.parse(" ;NAME = Spaced ; ;When=every-minutes : 5;", Optional.empty()))
                .form();

        assertThat(form.name()).isEqualTo("Spaced");
        assertThat(form.trigger()).isEqualTo("every-minutes");
        assertThat(form.triggerValue()).isEqualTo("5");
        assertThat(form.condition()).isEqualTo("none");
        assertThat(form.actions()).isEmpty();
    }

    @Test
    void onAcceptsTheUsualYesAndNoWords() {
        for (String yes : List.of("yes", "TRUE", "on")) {
            assertThat(ok(RuleSpec.parse("on=" + yes, Optional.of(BASE))).enabled())
                    .contains(true);
        }
        for (String no : List.of("no", "False", "off")) {
            assertThat(ok(RuleSpec.parse("on=" + no, Optional.of(BASE))).enabled())
                    .contains(false);
        }
        assertThat(code(RuleSpec.parse("on=maybe", Optional.of(BASE)))).isEqualTo("Automation.BadField");
    }

    @Test
    void malformedOrUnknownPartsAreRejected() {
        assertThat(code(RuleSpec.parse("name", Optional.empty()))).isEqualTo("Automation.BadField");
        assertThat(code(RuleSpec.parse("=x", Optional.empty()))).isEqualTo("Automation.BadField");
        assertThat(code(RuleSpec.parse("colour=red", Optional.empty()))).isEqualTo("Automation.BadField");
        assertThat(code(RuleSpec.parse("if=moon-phase:full", Optional.empty())))
                .isEqualTo("Automation.UnknownCondition");
        assertThat(code(RuleSpec.parse("do=explode", Optional.empty()))).isEqualTo("Automation.UnknownAction");
    }

    @Test
    void aSemicolonInsideAMessageSplitsTheRule() {
        assertThat(code(RuleSpec.parse("do=message-all:Hello; world", Optional.empty())))
                .isEqualTo("Automation.BadField");
    }

    @Test
    void nullOrEmptyTextGivesTheBaseUnchanged() {
        assertThat(ok(RuleSpec.parse(null, Optional.of(BASE))).form()).isEqualTo(BASE);
        assertThat(code(RuleSpec.parse("", Optional.empty()))).isEqualTo("Automation.UnknownTrigger");
    }

    @Test
    void anUnknownOrMissingTriggerListsTheTriggers() {
        Result<RuleSpec.Parsed> unknown = RuleSpec.parse("when=full-moon", Optional.of(BASE));
        assertThat(code(unknown)).isEqualTo("Automation.UnknownTrigger");
        assertThat(unknown.error().description()).contains("join", "every-minutes");

        Result<RuleSpec.Parsed> missing = RuleSpec.parse("name=Hi; do=message-all:Yo", Optional.empty());
        assertThat(code(missing)).isEqualTo("Automation.UnknownTrigger");
        assertThat(missing.error().description()).contains("when=", "join");
    }
}
