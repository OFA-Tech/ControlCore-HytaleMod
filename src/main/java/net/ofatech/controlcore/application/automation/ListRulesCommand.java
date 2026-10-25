package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ListRulesCommand implements ModCommand {

    private final AutomationService automation;
    private final AutomationSettings settings;
    private final Texts texts;

    public ListRulesCommand(AutomationService automation, AutomationSettings settings, Texts texts) {
        this.automation = automation;
        this.settings = settings;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-rules")
                .description("List the automation rules, and the actions and triggers mods offer");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        if (!automation.mayManage(context.sender().player().map(PlayerInfo::id))) {
            return Result.failure(Error.forbidden(
                    "Automation.Forbidden",
                    Text.key("controlcore.errors.automation.forbidden", "Only admins can manage automation rules.")));
        }
        Translator tr = texts.forSender(context.sender());
        List<String> lines = new ArrayList<>();
        List<AutomationView.Entry> entries = automation.entries(tr);
        lines.add(tr.t("controlcore.automation.list.title", "count", entries.size()));
        if (!settings.on()) {
            lines.add(tr.t("controlcore.automation.globally_off"));
        }
        for (AutomationView.Entry entry : entries) {
            lines.add((entry.enabled() ? "[on] " : "[off] ") + entry.id() + " - " + entry.summary());
        }
        automation
                .modActions()
                .forEach((id, name) -> lines.add(tr.t("controlcore.automation.list.action", "id", id, "name", name)));
        automation
                .modTriggers()
                .forEach((id, name) -> lines.add(tr.t("controlcore.automation.list.trigger", "id", id, "name", name)));
        return Result.success(Reply.of(String.join("\n", lines)));
    }
}
