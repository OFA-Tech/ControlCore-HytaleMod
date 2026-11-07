package net.ofatech.controlcore.application.maintenance;

import com.google.auto.service.AutoService;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.CommonErrors;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class MaintenanceCommand implements ModCommand {

    private final MaintenanceService maintenance;
    private final Texts texts;

    public MaintenanceCommand(MaintenanceService maintenance, Texts texts) {
        this.maintenance = maintenance;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-maintenance")
                .description("Turn maintenance mode on or off, or show it")
                .arg("state", ArgType.STRING, "on, off or status")
                .optionalArg("message", ArgType.STRING, "What kicked and refused players see")
                .optionalArg("motd", ArgType.STRING, "The MOTD to show while it is on")
                .optionalArg("for", ArgType.STRING, "Turn it off by itself after this long, for example 30m or 2h");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        Translator tr = texts.forSender(context.sender());
        String state = context.string("state").strip().toLowerCase(Locale.ROOT);
        return switch (state) {
            case "status" -> Result.success(Reply.of(maintenance.status(tr)));
            case "off" -> maintenance.turnOff(who, tr).map(Reply::of);
            case "on" ->
                MaintenanceService.offAfter(text(context, "for"))
                        .flatMap(length ->
                                maintenance.turnOn(text(context, "message"), text(context, "motd"), length, who, tr))
                        .map(Reply::of);
            default -> Result.failure(CommonErrors.badChoice("Maintenance.BadState", "on, off, status"));
        };
    }

    private static String text(CommandContext context, String name) {
        return context.arg(name).map(Object::toString).orElse("");
    }
}
