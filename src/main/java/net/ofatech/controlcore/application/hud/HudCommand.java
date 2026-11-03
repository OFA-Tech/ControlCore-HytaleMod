package net.ofatech.controlcore.application.hud;

import com.google.auto.service.AutoService;
import java.util.Locale;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class HudCommand implements ModCommand {

    private final HudService hud;
    private final Texts texts;

    public HudCommand(HudService hud, Texts texts) {
        this.hud = hud;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-hud")
                .description("Hide or show your whole HUD")
                .arg("action", ArgType.STRING, "hide, show or toggle")
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        PlayerId player = context.sender().player().orElseThrow().id();
        Translator tr = texts.forSender(context.sender());
        boolean hidden;
        switch (context.string("action").strip().toLowerCase(Locale.ROOT)) {
            case "hide" -> hidden = true;
            case "show" -> hidden = false;
            case "toggle" -> hidden = !hud.isHidden(player);
            default -> {
                return Result.failure(Error.validation(
                        "Hud.BadChoice",
                        Text.key("controlcore.errors.players.bad_choice", "Use {choices}.")
                                .with("choices", "hide, show, toggle")));
            }
        }
        hud.setHidden(player, hidden);
        return Result.success(
                Reply.of(tr.t(hidden ? "controlcore.hud.command.hidden" : "controlcore.hud.command.shown")));
    }
}
