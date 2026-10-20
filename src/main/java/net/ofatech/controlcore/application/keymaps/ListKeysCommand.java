package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ListKeysCommand implements ModCommand {

    private final KeymapRegistry keymaps;

    public ListKeysCommand(KeymapRegistry keymaps) {
        this.keymaps = keymaps;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-keys").description("List the key actions and your key bindings");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        List<KeyAction> actions = keymaps.actions();
        if (actions.isEmpty()) {
            return Result.success(Reply.of("No key actions are registered."));
        }
        List<String> lines = new ArrayList<>();
        lines.add("Key actions (change with /cc-bind <id> <keys>, for example /cc-bind controlcore:open-preferences"
                + " ctrl+f):");
        var player = context.sender().player().map(info -> info.id());
        for (KeyAction action : actions) {
            lines.add("- " + line(action, player.orElse(null)));
        }
        return Result.success(Reply.of(String.join("\n", lines)));
    }

    private String line(KeyAction action, PlayerId player) {
        if (player != null && keymaps.ownKeys(action.id(), player).isPresent()) {
            return KeymapsText.line(action, keymaps.keysFor(action.id(), player), "yours");
        }
        boolean serverChoice = !keymaps.serverKeys(action.id()).equals(action.defaultKeys());
        return KeymapsText.line(action, keymaps.serverKeys(action.id()), serverChoice ? "server" : "default");
    }
}
