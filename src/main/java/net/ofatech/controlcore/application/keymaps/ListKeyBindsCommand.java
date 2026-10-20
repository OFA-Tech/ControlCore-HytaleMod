package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingsPermissions;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;
import net.ofatech.controlcore.domain.result.Result;

/** {@code /cc-keybind-list}: the key binds admins created, with their commands. */
@AutoService(ModCommand.class)
public final class ListKeyBindsCommand implements ModCommand {

    private final CustomKeyBindStore binds;

    public ListKeyBindsCommand(CustomKeyBindStore binds) {
        this.binds = binds;
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
            return Result.success(Reply.of("No custom key binds yet. Create one with /cc-keybind-add."));
        }
        List<String> lines = new ArrayList<>();
        lines.add("Custom key binds (edit by adding the same name again, delete with /cc-keybind-remove):");
        for (CustomKeyBind bind : all) {
            lines.add("- custom:" + bind.name() + ": " + bind.keys() + " runs /" + bind.command()
                    + (bind.runAsServer() ? " as the server" : ""));
        }
        return Result.success(Reply.of(String.join("\n", lines)));
    }
}
