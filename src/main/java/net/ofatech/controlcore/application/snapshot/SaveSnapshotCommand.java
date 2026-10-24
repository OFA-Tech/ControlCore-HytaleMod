package net.ofatech.controlcore.application.snapshot;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class SaveSnapshotCommand implements ModCommand {

    private final SnapshotService snapshots;
    private final Texts texts;

    public SaveSnapshotCommand(SnapshotService snapshots, Texts texts) {
        this.snapshots = snapshots;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-snapshot-save")
                .description("Save a snapshot of the server's settings (replaces one with the same name)")
                .arg("name", ArgType.STRING, "Name, for example pvp-weekend")
                .optionalArg(
                        "parts",
                        ArgType.STRING,
                        "all (default), or a list like config,keys,settings:controlcore+homes,permissions,mods,players");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        String parts = context.arg("parts").map(Object::toString).orElse("all");
        return SnapshotCommands.parts(parts)
                .flatMap(selection -> snapshots.save(
                        context.string("name"),
                        selection.parts(),
                        selection.mods(),
                        SnapshotCommands.who(context),
                        SnapshotCommands.actor(context)))
                .map(saved -> Reply.of(snapshots.savedText(saved, texts.forSender(context.sender()))));
    }
}
