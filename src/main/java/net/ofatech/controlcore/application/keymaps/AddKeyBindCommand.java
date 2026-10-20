package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingsPermissions;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

/** {@code /cc-keybind-add <name> <keys> <command>}: an admin's own key action, run as the player. */
@AutoService(ModCommand.class)
public final class AddKeyBindCommand implements ModCommand {

    private final KeymapRegistry keymaps;

    public AddKeyBindCommand(KeymapRegistry keymaps) {
        this.keymaps = keymaps;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-keybind-add")
                .description("Create a key bind that runs a command as the player who presses it")
                .arg("name", ArgType.STRING, "Name, for example warp-spawn (becomes custom:warp-spawn)")
                .arg("keys", ArgType.STRING, "Keys joined with +, alternatives with |, for example ctrl+f")
                .arg("command", ArgType.GREEDY_STRING, "Command to run; {player} and {uuid} are filled in")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return add(keymaps, context, false);
    }

    static Result<Reply> add(KeymapRegistry keymaps, CommandContext context, boolean runAsServer) {
        return keymaps.addCustom(context.string("name"), context.string("keys"), context.string("command"), runAsServer)
                .map(action -> Reply.of(KeymapsText.created(action)));
    }
}
