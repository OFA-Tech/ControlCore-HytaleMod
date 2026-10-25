package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ToggleRuleCommand implements ModCommand {

    private final AutomationService automation;
    private final Texts texts;

    public ToggleRuleCommand(AutomationService automation, Texts texts) {
        this.automation = automation;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-rule-toggle")
                .description("Turn an automation rule on or off")
                .arg("rule", ArgType.STRING, "The rule's id (see /cc-rules)");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return automation
                .toggle(
                        context.string("rule"),
                        context.sender().player().map(PlayerInfo::id),
                        texts.forSender(context.sender()))
                .map(Reply::of);
    }
}
