package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingsPermissions;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

/**
 * {@code /cc-keybind-add-server <name> <keys> <command>}: an admin's own key action, run as the server. Only
 * players with {@code <base>.keys.server} (operators) can use it.
 */
@AutoService(ModCommand.class)
public final class AddServerKeyBindCommand implements ModCommand {

    private final KeymapRegistry keymaps;

    public AddServerKeyBindCommand(KeymapRegistry keymaps) {
        this.keymaps = keymaps;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-keybind-add-server")
                .description("Create a key bind that runs a command as the server (only for players with "
                        + KeymapRegistry.SERVER_KEYS_NODE + ")")
                .arg("name", ArgType.STRING, "Name, for example announce (becomes custom:announce)")
                .arg("keys", ArgType.STRING, "Keys joined with +, alternatives with |, for example ctrl+o")
                .arg("command", ArgType.GREEDY_STRING, "Command to run; {player} and {uuid} are filled in")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return AddKeyBindCommand.add(keymaps, context, true);
    }
}
