package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingsPermissions;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class KeyGameActionCommand implements ModCommand {

    private final KeymapRegistry keymaps;
    private final Texts texts;

    public KeyGameActionCommand(KeymapRegistry keymaps, Texts texts) {
        this.keymaps = keymaps;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-key-game-action")
                .description("Choose whether one key action keeps the key's game action: server, keep or replace")
                .arg("id", ArgType.STRING, "Key action id, mod:action (see /cc-keys)")
                .arg("mode", ArgType.STRING, "server (follow the server setting), keep or replace")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        String modeText = context.string("mode");
        Result<GameActionMode> mode = GameActionMode.parse(modeText)
                .map(Result::success)
                .orElseGet(() -> Result.failure(Error.validation(
                        "Keys.UnknownGameActionMode",
                        Text.key(
                                        "controlcore.errors.keys.unknown_game_action_mode",
                                        "'{mode}' is not server, keep or replace.")
                                .with("mode", modeText))));
        return SettingId.parse(context.string("id"))
                .flatMap(id -> mode.flatMap(wanted -> keymaps.setGameAction(id, wanted))
                        .map(saved -> Reply.of(tr.t(
                                "controlcore.keys.game_action.saved",
                                "name",
                                tr.translate(keymaps.find(id).orElseThrow().nameText()),
                                "mode",
                                tr.t("controlcore.keys.game_action." + saved.id())))));
    }
}
