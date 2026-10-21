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
public final class AddKeyBindCommand implements ModCommand {

    private final KeymapRegistry keymaps;
    private final Texts texts;

    public AddKeyBindCommand(KeymapRegistry keymaps, Texts texts) {
        this.keymaps = keymaps;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-keybind-add")
                .description("Create or replace a key bind that runs a command as the player who presses it")
                .arg("name", ArgType.STRING, "Name, for example warp-spawn (becomes custom:warp-spawn)")
                .arg("keys", ArgType.STRING, "Keys joined with +, alternatives with |, for example ctrl+f")
                .arg("command", ArgType.GREEDY_STRING, "Command to run; {player} and {uuid} are filled in")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return add(keymaps, context, false, tr(context));
    }

    static Result<Reply> add(KeymapRegistry keymaps, CommandContext context, boolean runAsServer, Translator tr) {
        boolean existed = keymaps.findCustom(context.string("name")).isPresent();
        return keymaps.addCustom(context.string("name"), context.string("keys"), context.string("command"), runAsServer)
                .map(action -> Reply.of(
                        (existed ? tr.t("controlcore.keys.replaced") + " " : "") + KeymapsText.created(tr, action)));
    }

    private Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }
}
