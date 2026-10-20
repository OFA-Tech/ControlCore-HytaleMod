package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class BindKeyCommand implements ModCommand {

    private final KeymapRegistry keymaps;

    public BindKeyCommand(KeymapRegistry keymaps) {
        this.keymaps = keymaps;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-bind")
                .description("Change your keys for a key action")
                .arg("id", ArgType.STRING, "Key action id, mod:action (see /cc-keys)")
                .arg("keys", ArgType.GREEDY_STRING, "Keys joined with +, alternatives with |, or none")
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        var player = context.sender().player().orElseThrow().id();
        return SettingId.parse(context.string("id"))
                .flatMap(id -> keymaps.bind(id, player, context.string("keys"))
                        .map(binds ->
                                Reply.of(KeymapsText.changed(keymaps.find(id).orElseThrow(), binds, "Your"))));
    }
}
