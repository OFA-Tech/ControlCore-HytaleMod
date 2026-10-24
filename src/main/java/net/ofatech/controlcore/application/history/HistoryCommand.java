package net.ofatech.controlcore.application.history;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.HistoryView;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class HistoryCommand implements ModCommand {

    static final int SHOWN = 10;

    private final HistoryEditor history;
    private final Texts texts;

    public HistoryCommand(HistoryEditor history, Texts texts) {
        this.history = history;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-history")
                .description("List the latest changes to server settings, config.json, keys, permissions and mods")
                .optionalArg("filter", ArgType.STRING, "Only changes whose name, who or value contains this text");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Optional<PlayerId> viewer = context.sender().player().map(PlayerInfo::id);
        if (!history.maySee(viewer)) {
            return Result.failure(Error.forbidden(
                    "History.Forbidden",
                    Text.key("controlcore.errors.history.no_view", "You may not see the change history.")));
        }
        Translator tr = texts.forSender(context.sender());
        String filter = context.arg("filter")
                .map(Object::toString)
                .map(text -> text.strip().toLowerCase(Locale.ROOT))
                .orElse("");
        List<HistoryView.Entry> matching = history.entries(tr, viewer).stream()
                .filter(entry -> matches(entry, filter))
                .toList();
        if (matching.isEmpty()) {
            return Result.success(Reply.of(tr.t("controlcore.history.none")));
        }
        StringBuilder text = new StringBuilder(tr.t(
                "controlcore.history.list.header",
                "shown",
                Math.min(SHOWN, matching.size()),
                "total",
                matching.size()));
        for (HistoryView.Entry entry : matching.subList(0, Math.min(SHOWN, matching.size()))) {
            text.append('\n').append(line(entry, tr));
        }
        if (history.mayRollBack(viewer)) {
            text.append('\n').append(tr.t("controlcore.history.list.rollback_hint"));
        }
        return Result.success(Reply.of(text.toString()));
    }

    static boolean matches(HistoryView.Entry entry, String filter) {
        if (filter.isEmpty()) {
            return true;
        }
        return String.join(
                        "\n",
                        entry.what(),
                        entry.who(),
                        entry.target(),
                        entry.before(),
                        entry.after(),
                        entry.source(),
                        "#" + entry.id())
                .toLowerCase(Locale.ROOT)
                .contains(filter);
    }

    private static String line(HistoryView.Entry entry, Translator tr) {
        String line = tr.t(
                "controlcore.history.list.line",
                "id",
                entry.id(),
                "when",
                entry.when(),
                "who",
                entry.who(),
                "source",
                entry.source(),
                "what",
                entry.what(),
                "before",
                entry.before(),
                "after",
                entry.after());
        if (entry.rolledBack()) {
            line += " " + tr.t("controlcore.history.list.rolled_back");
        }
        return line;
    }
}
