package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingsPermissions;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class AddServerKeyBindCommand implements ModCommand {

    private final KeymapRegistry keymaps;
    private final Texts texts;

    public AddServerKeyBindCommand(KeymapRegistry keymaps, Texts texts) {
        this.keymaps = keymaps;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-keybind-add-server")
                .description("Create or replace a key bind that runs a command as the server (only for players with "
                        + KeymapRegistry.SERVER_KEYS_NODE + ")")
                .arg("name", ArgType.STRING, "Name, for example announce (becomes custom:announce)")
                .arg("keys", ArgType.STRING, "Keys joined with +, alternatives with |, for example ctrl+o")
                .arg("command", ArgType.GREEDY_STRING, "Command to run; {player} and {uuid} are filled in")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return AddKeyBindCommand.add(keymaps, context, true, tr(context));
    }

    private Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }
}
