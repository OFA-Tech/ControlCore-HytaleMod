package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingsPermissions;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class RemoveKeyBindCommand implements ModCommand {

    private final KeymapRegistry keymaps;

    public RemoveKeyBindCommand(KeymapRegistry keymaps) {
        this.keymaps = keymaps;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-keybind-remove")
                .description("Delete a key bind created with /cc-keybind-add")
                .arg("name", ArgType.STRING, "Name of the bind, for example warp-spawn or custom:warp-spawn")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return keymaps.removeCustom(context.string("name"))
                .map(action -> Reply.of("Removed the key bind " + action.id() + "."));
    }
}
