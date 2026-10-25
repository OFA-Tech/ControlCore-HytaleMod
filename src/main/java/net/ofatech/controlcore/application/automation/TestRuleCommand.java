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
public final class TestRuleCommand implements ModCommand {

    private final AutomationService automation;
    private final Texts texts;

    public TestRuleCommand(AutomationService automation, Texts texts) {
        this.automation = automation;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-rule-test")
                .description("Run an automation rule once now, ignoring its trigger, conditions and cooldown")
                .arg("rule", ArgType.STRING, "The rule's id (see /cc-rules)");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return automation
                .test(
                        context.string("rule"),
                        context.sender().player(),
                        context.sender().player().map(PlayerInfo::id),
                        texts.forSender(context.sender()))
                .map(Reply::of);
    }
}
