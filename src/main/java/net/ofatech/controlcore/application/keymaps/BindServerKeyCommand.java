package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingsPermissions;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class BindServerKeyCommand implements ModCommand {

    private final KeymapRegistry keymaps;

    public BindServerKeyCommand(KeymapRegistry keymaps) {
        this.keymaps = keymaps;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-bind-server")
                .description("Change the server's keys for a key action (players who chose their own keep them)")
                .arg("id", ArgType.STRING, "Key action id, mod:action (see /cc-keys)")
                .arg("keys", ArgType.GREEDY_STRING, "Keys joined with +, alternatives with |, or none")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return SettingId.parse(context.string("id"))
                .flatMap(id -> keymaps.bindServer(id, context.string("keys"))
                        .map(binds ->
                                Reply.of(KeymapsText.changed(keymaps.find(id).orElseThrow(), binds, "The server's"))));
    }
}
