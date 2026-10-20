package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ListKeysCommand implements ModCommand {

    private final KeymapRegistry keymaps;
    private final Texts texts;

    public ListKeysCommand(KeymapRegistry keymaps, Texts texts) {
        this.keymaps = keymaps;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-keys").description("List the key actions and your key bindings");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        var player = context.sender().player().map(info -> info.id());
        List<KeyAction> actions = player.map(keymaps::actionsFor).orElseGet(keymaps::actions);
        if (actions.isEmpty()) {
            return Result.success(Reply.of(tr(context).t("controlcore.keys.list.none")));
        }
        List<String> lines = new ArrayList<>();
        lines.add(tr(context).t("controlcore.keys.list.title"));
        for (KeyAction action : actions) {
            lines.add("- " + line(tr(context), action, player.orElse(null)));
        }
        return Result.success(Reply.of(String.join("\n", lines)));
    }

    private String line(Translator tr, KeyAction action, PlayerId player) {
        if (player != null && keymaps.ownKeys(action.id(), player).isPresent()) {
            return KeymapsText.line(tr, action, keymaps.keysFor(action.id(), player), "yours");
        }
        boolean serverChoice = !keymaps.serverKeys(action.id()).equals(action.defaultKeys());
        return KeymapsText.line(tr, action, keymaps.serverKeys(action.id()), serverChoice ? "server" : "default");
    }

    private Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }
}
