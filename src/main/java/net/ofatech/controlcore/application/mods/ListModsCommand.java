package net.ofatech.controlcore.application.mods;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingsPermissions;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

/** {@code /cc-mods}: the server's mods (not Hytale's own) with their state, for admins and the console. */
@AutoService(ModCommand.class)
public final class ListModsCommand implements ModCommand {

    private final ModsEditor mods;
    private final Texts texts;

    public ListModsCommand(ModsEditor mods, Texts texts) {
        this.mods = mods;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-mods")
                .description("List the server's mods and whether they run")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        List<String> lines = new ArrayList<>();
        for (ModsView.Entry entry :
                mods.viewedBy(context.sender().player().map(PlayerInfo::id)).list()) {
            ModInfo mod = entry.mod();
            if (mod.core()) {
                continue;
            }
            List<String> details = new ArrayList<>();
            details.add(tr.t("controlcore.mods.state." + mod.state().name().toLowerCase(Locale.ROOT)));
            if (mod.changesAfterRestart()) {
                details.add(tr.t(
                        mod.enabledAfterRestart()
                                ? "controlcore.panel.mods.badge.on_after_restart"
                                : "controlcore.panel.mods.badge.off_after_restart"));
            }
            entry.namespace().ifPresent(namespace -> details.add("mod:" + namespace));
            lines.add(tr.t(
                    "controlcore.mods.line",
                    "name",
                    mod.name(),
                    "version",
                    mod.version(),
                    "id",
                    mod.id(),
                    "details",
                    String.join(", ", details)));
        }
        if (lines.isEmpty()) {
            return Result.success(Reply.of(tr.t("controlcore.mods.none")));
        }
        return Result.success(
                Reply.of(tr.t("controlcore.mods.title", "count", lines.size()) + "\n" + String.join("\n", lines)));
    }
}
