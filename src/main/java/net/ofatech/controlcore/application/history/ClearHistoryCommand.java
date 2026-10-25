package net.ofatech.controlcore.application.history;

import com.google.auto.service.AutoService;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ClearHistoryCommand implements ModCommand {

    private final HistoryEditor history;
    private final Texts texts;
    private final Log log;

    public ClearHistoryCommand(HistoryEditor history, Texts texts, Log log) {
        this.history = history;
        this.texts = texts;
        this.log = log;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-history-clear")
                .description("Delete recorded changes older than a number of days, or all of them")
                .arg("age", ArgType.STRING, "A number of days like 30 or 30d, or all");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return age(context.string("age"))
                .flatMap(age -> history.clear(
                        age, context.sender().player().map(PlayerInfo::id), texts.forSender(context.sender())))
                .map(text -> {
                    log.info(context.sender().name() + " cleared change history: " + context.string("age"));
                    return Reply.of(text);
                });
    }

    static Result<Optional<Duration>> age(String text) {
        String value = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        if (value.equals("all")) {
            return Result.success(Optional.empty());
        }
        String digits = value.endsWith("d") ? value.substring(0, value.length() - 1) : value;
        if (digits.matches("\\d{1,4}")) {
            int days = Integer.parseInt(digits);
            if (days >= 1 && days <= 3650) {
                return Result.success(Optional.of(Duration.ofDays(days)));
            }
        }
        return Result.failure(Error.validation(
                "History.InvalidAge",
                Text.key(
                                "controlcore.errors.history.invalid_age",
                                "'{age}' is not a number of days (1 to 3650) or all.")
                        .with("age", text == null ? "" : text)));
    }
}
