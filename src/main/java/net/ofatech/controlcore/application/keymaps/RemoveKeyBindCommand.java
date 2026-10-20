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
public final class RemoveKeyBindCommand implements ModCommand {

    private final KeymapRegistry keymaps;
    private final Texts texts;

    public RemoveKeyBindCommand(KeymapRegistry keymaps, Texts texts) {
        this.keymaps = keymaps;
        this.texts = texts;
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
                .map(action -> Reply.of(KeymapsText.removed(tr(context), action)));
    }

    private Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }
}
