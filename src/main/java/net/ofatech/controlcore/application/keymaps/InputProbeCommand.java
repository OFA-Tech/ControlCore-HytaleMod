package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class InputProbeCommand implements ModCommand {

    public static final String PERMISSION = "debug";

    private final InputProbe probe;
    private final Texts texts;

    public InputProbeCommand(InputProbe probe, Texts texts) {
        this.probe = probe;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-input-probe")
                .description("Show in chat what your client sends when you press keys (run again to stop)")
                .permission(PERMISSION)
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        PlayerInfo sender = context.sender().player().orElseThrow();
        Translator tr = texts.forSender(context.sender());
        return Result.success(Reply.of(
                probe.toggle(sender.id()) ? tr.t("controlcore.debug.probe_on") : tr.t("controlcore.debug.probe_off")));
    }
}
