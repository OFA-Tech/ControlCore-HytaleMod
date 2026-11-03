package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.automation.Rule;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class EditRuleCommand implements ModCommand {

    private final AutomationService automation;
    private final Texts texts;

    public EditRuleCommand(AutomationService automation, Texts texts) {
        this.automation = automation;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-rule-edit")
                .description("Change parts of an automation rule; parts you leave out stay as they are")
                .arg("id", ArgType.STRING, "The rule's id (see /cc-rules)")
                .arg(
                        "changes",
                        ArgType.GREEDY_STRING,
                        "key=value parts joined with ;, for example cooldown=60; on=no. Any do= replaces all actions");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        if (!automation.mayManage(who)) {
            return Result.failure(Error.forbidden(
                    "Automation.Forbidden",
                    Text.key("controlcore.errors.automation.forbidden", "Only admins can manage automation rules.")));
        }
        Optional<Rule> rule = automation.find(context.string("id"));
        if (rule.isEmpty()) {
            return Result.failure(Error.notFound(
                    "Automation.Unknown",
                    Text.key("controlcore.errors.automation.unknown", "There is no rule {id}.")
                            .with("id", context.string("id"))));
        }
        return RuleSpec.parse(context.string("changes"), Optional.of(AutomationService.form(rule.get())))
                .flatMap(parsed -> automation.save(
                        Optional.of(rule.get().id()),
                        parsed.form(),
                        parsed.enabled(),
                        who,
                        texts.forSender(context.sender())))
                .map(Reply::of);
    }
}
