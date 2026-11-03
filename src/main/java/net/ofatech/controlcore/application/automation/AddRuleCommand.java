package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.automation.Rule;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class AddRuleCommand implements ModCommand {

    private final AutomationService automation;
    private final Texts texts;

    public AddRuleCommand(AutomationService automation, Texts texts) {
        this.automation = automation;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-rule-add")
                .description("Create an automation rule")
                .arg(
                        "rule",
                        ArgType.GREEDY_STRING,
                        "key=value parts joined with ;, for example name=Hello; when=join; do=message-player:Hi {player}");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        Set<String> before = automation.rules().stream().map(Rule::id).collect(Collectors.toSet());
        return RuleSpec.parse(context.string("rule"), Optional.empty())
                .flatMap(parsed -> automation.save(
                        Optional.empty(),
                        parsed.form(),
                        parsed.enabled(),
                        context.sender().player().map(PlayerInfo::id),
                        tr))
                .map(saved -> Reply.of(automation.rules().stream()
                        .map(Rule::id)
                        .filter(id -> !before.contains(id))
                        .findFirst()
                        .map(id -> saved + " " + tr.t("controlcore.automation.command.id", "id", id))
                        .orElse(saved)));
    }
}
