package net.ofatech.controlcore.application.mods;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingsPermissions;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

/** {@code /cc-mods}: the server's mods (not Hytale's own) with their state, for admins and the console. */
@AutoService(ModCommand.class)
public final class ListModsCommand implements ModCommand {

    private final ModsEditor mods;

    public ListModsCommand(ModsEditor mods) {
        this.mods = mods;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-mods")
                .description("List the server's mods and whether they run")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        List<String> lines = new ArrayList<>();
        for (ModsView.Entry entry :
                mods.viewedBy(context.sender().player().map(PlayerInfo::id)).list()) {
            ModInfo mod = entry.mod();
            if (mod.core()) {
                continue;
            }
            String state = mod.state().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            String after = mod.changesAfterRestart()
                    ? (mod.enabledAfterRestart() ? ", on after restart" : ", off after restart")
                    : "";
            lines.add("- " + mod.name() + " " + mod.version() + " (" + mod.id() + "): " + state + after
                    + entry.namespace().map(ns -> ", settings: mod:" + ns).orElse("")
                    + (mod.logo().isPresent() ? ", logo" : ""));
        }
        return Result.success(
                Reply.of(lines.isEmpty() ? "No mods." : "Mods (" + lines.size() + "):\n" + String.join("\n", lines)));
    }
}
