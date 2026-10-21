package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingsPermissions;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ResetServerKeyCommand implements ModCommand {

    private final KeymapRegistry keymaps;
    private final Texts texts;

    public ResetServerKeyCommand(KeymapRegistry keymaps, Texts texts) {
        this.keymaps = keymaps;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-bind-server-reset")
                .description("Go back to the mod's default keys for a key action")
                .arg("id", ArgType.STRING, "Key action id, mod:action (see /cc-keys)")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return SettingId.parse(context.string("id"))
                .flatMap(id -> keymaps.resetServerBind(id)
                        .map(binds -> Reply.of(KeymapsText.changed(
                                tr(context), keymaps.find(id).orElseThrow(), binds, true))));
    }

    private Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }
}
