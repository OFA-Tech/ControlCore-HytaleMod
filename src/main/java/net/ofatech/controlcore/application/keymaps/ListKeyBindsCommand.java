package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingsPermissions;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;
import net.ofatech.controlcore.domain.result.Result;

/** {@code /cc-keybind-list}: the key binds admins created, with their commands. */
@AutoService(ModCommand.class)
public final class ListKeyBindsCommand implements ModCommand {

    private final CustomKeyBindStore binds;
    private final Texts texts;

    public ListKeyBindsCommand(CustomKeyBindStore binds, Texts texts) {
        this.binds = binds;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-keybind-list")
                .description("List the key binds created with /cc-keybind-add")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        List<CustomKeyBind> all = binds.all();
        if (all.isEmpty()) {
            return Result.success(Reply.of(tr(context).t("controlcore.keys.custom.none")));
        }
        List<String> lines = new ArrayList<>();
        lines.add(tr(context).t("controlcore.keys.custom.title"));
        for (CustomKeyBind bind : all) {
            lines.add("- "
                    + tr(context)
                            .t(
                                    bind.runAsServer()
                                            ? "controlcore.keys.custom.line_server"
                                            : "controlcore.keys.custom.line",
                                    "name",
                                    bind.name(),
                                    "keys",
                                    bind.keys(),
                                    "command",
                                    bind.command()));
        }
        return Result.success(Reply.of(String.join("\n", lines)));
    }

    private Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }
}
