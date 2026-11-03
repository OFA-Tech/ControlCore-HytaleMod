package net.ofatech.controlcore.application.mods;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.mods.ModDependency;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.ports.system.ServerHealth;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class ModCommands {

    private final ModsEditor mods;
    private final Texts texts;
    private final ServerHealth health;

    public ModCommands(ModsEditor mods, Texts texts, ServerHealth health) {
        this.mods = mods;
        this.texts = texts;
        this.health = health;
    }

    ModsView view(CommandContext context) {
        return mods.viewedBy(context.sender().player().map(PlayerInfo::id));
    }

    Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }

    static Result<ModsView.Entry> find(ModsView view, String input) {
        String wanted = input == null ? "" : input.strip();
        return view.list().stream()
                .filter(entry -> entry.mod().id().equalsIgnoreCase(wanted)
                        || entry.mod().name().equalsIgnoreCase(wanted)
                        || entry.namespace().filter(wanted::equalsIgnoreCase).isPresent())
                .findFirst()
                .map(Result::success)
                .orElseGet(() -> Result.failure(Error.notFound(
                        "Mods.Unknown",
                        Text.key("controlcore.errors.mods.unknown", "There is no mod {id}.")
                                .with("id", wanted))));
    }

    Result<Reply> details(CommandContext context, String input) {
        ModsView view = view(context);
        Translator tr = tr(context);
        return find(view, input).map(entry -> {
            ModInfo mod = entry.mod();
            List<String> lines = new ArrayList<>();
            lines.add(mod.name() + " " + version(mod) + " (" + mod.id() + ") - "
                    + tr.t("controlcore.mods.state." + mod.state().name().toLowerCase(Locale.ROOT))
                    + (mod.changesAfterRestart()
                            ? ", "
                                    + tr.t(
                                            mod.enabledAfterRestart()
                                                    ? "controlcore.panel.mods.badge.on_after_restart"
                                                    : "controlcore.panel.mods.badge.off_after_restart")
                            : ""));
            lines.add(mod.description().isEmpty() ? tr.t("controlcore.panel.mods.no_description") : mod.description());
            if (!mod.authors().isEmpty()) {
                lines.add(tr.t("controlcore.panel.mods.by", "authors", String.join(", ", mod.authors())));
            }
            List<String> requires = new ArrayList<>();
            List<String> canUse = new ArrayList<>();
            for (ModDependency dependency : mod.dependencies()) {
                Optional<ModsView.Entry> installed = view.list().stream()
                        .filter(other -> other.mod().id().equalsIgnoreCase(dependency.id()))
                        .findFirst();
                String need = dependency.range().isEmpty()
                        ? tr.t("controlcore.panel.mods.dep.any")
                        : tr.t("controlcore.panel.mods.dep.needs", "range", dependency.range());
                String have = installed
                        .map(other -> tr.t("controlcore.panel.mods.dep.installed", "version", version(other.mod())))
                        .orElse(tr.t("controlcore.panel.mods.dep.not_installed"));
                String name = installed.map(other -> other.mod().name()).orElse(dependency.id());
                String mark = dependency.optional()
                        ? tr.t(
                                installed.isPresent()
                                        ? "controlcore.panel.mods.dep.installed_badge"
                                        : "controlcore.panel.mods.dep.absent")
                        : tr.t(
                                switch (dependency.status()) {
                                    case OK -> "controlcore.panel.mods.dep.ok";
                                    case MISSING -> "controlcore.panel.mods.dep.missing";
                                    case OFF -> "controlcore.panel.mods.dep.off";
                                    case WRONG_VERSION -> "controlcore.panel.mods.dep.wrong";
                                });
                (dependency.optional() ? canUse : requires)
                        .add("  - " + name + ": " + need + ", " + have + " [" + mark + "]");
            }
            List<String> neededBy = new ArrayList<>();
            for (String other : entry.neededBy()) {
                Optional<ModsView.Entry> byName = view.list().stream()
                        .filter(candidate -> candidate.mod().name().equalsIgnoreCase(other)
                                || candidate.mod().id().equalsIgnoreCase(other))
                        .findFirst();
                boolean on = byName.map(candidate -> candidate.mod().running()).orElse(false);
                neededBy.add("  - " + other + " ["
                        + tr.t(on ? "controlcore.panel.mods.dep.on" : "controlcore.panel.mods.dep.off") + "]");
            }
            section(lines, tr.t("controlcore.ui.mods.requires"), requires, tr);
            section(lines, tr.t("controlcore.ui.mods.canUse"), canUse, tr);
            section(lines, tr.t("controlcore.ui.mods.neededBy"), neededBy, tr);
            lines.add(tr.t("controlcore.ui.mods.hytale") + ": "
                    + (mod.serverVersion().isEmpty()
                            ? tr.t("controlcore.panel.mods.dep.any")
                            : tr.t("controlcore.panel.mods.dep.needs", "range", mod.serverVersion()))
                    + ", " + tr.t("controlcore.panel.mods.dep.server", "version", health.gameVersion())
                    + " ["
                    + tr.t(mod.serverVersionOk() ? "controlcore.panel.mods.dep.ok" : "controlcore.panel.mods.dep.wrong")
                    + "]");
            return Reply.of(String.join("\n", lines));
        });
    }

    Result<Reply> setEnabled(CommandContext context, boolean enabled) {
        ModsView view = view(context);
        Translator tr = tr(context);
        boolean all = context.arg("confirm")
                .map(Object::toString)
                .filter(text -> text.strip().equalsIgnoreCase("all"))
                .isPresent();
        Result<ModsView.Entry> found = find(view, context.string("mod"));
        if (found.isFailure()) {
            return Result.failure(found.error());
        }
        ModInfo mod = found.value().mod();
        List<ModsView.Entry> others = found.value().manageable() ? view.alsoChanged(mod.id(), enabled) : List.of();
        String names = String.join(
                ", ", others.stream().map(entry -> entry.mod().name()).toList());
        if (!others.isEmpty() && !all) {
            return Result.failure(Error.validation(
                    "Mods.Confirm",
                    Text.of(tr.t(
                                    enabled
                                            ? "controlcore.panel.mods.confirm_on"
                                            : "controlcore.panel.mods.confirm_off",
                                    "name",
                                    mod.name(),
                                    "mods",
                                    names)
                            + " "
                            + tr.t(
                                    "controlcore.mods.confirm_command",
                                    "command",
                                    enabled ? "cc-mod-on" : "cc-mod-off",
                                    "mod",
                                    mod.id()))));
        }
        return view.setEnabled(mod.id(), enabled)
                .map(changed -> Reply.of(
                        others.isEmpty()
                                ? tr.t(
                                        enabled
                                                ? "controlcore.panel.mods.enabled_restart"
                                                : "controlcore.panel.mods.disabled_restart",
                                        "name",
                                        changed.mod().name())
                                : tr.t(
                                        "controlcore.panel.mods.also_changed",
                                        "name",
                                        changed.mod().name(),
                                        "count",
                                        others.size(),
                                        "mods",
                                        names)));
    }

    Result<Reply> configs(CommandContext context) {
        ModsView view = view(context);
        Translator tr = tr(context);
        if (!view.canManage()) {
            return Result.failure(Error.forbidden(
                    "Mods.ConfigForbidden",
                    Text.key("controlcore.errors.mods.config_forbidden", "Only admins can read mod config files.")));
        }
        return find(view, context.string("mod"))
                .map(entry -> Reply.of(tr.t(
                                "controlcore.panel.mods.config_title",
                                "name",
                                entry.mod().name())
                        + "\n"
                        + configText(view.configFiles(entry.mod().id()), tr)));
    }

    private static void section(List<String> lines, String title, List<String> items, Translator tr) {
        lines.add(title + ":");
        lines.addAll(items.isEmpty() ? List.of("  " + tr.t("controlcore.ui.mods.noneListed")) : items);
    }

    static String version(ModInfo mod) {
        return mod.version().isEmpty() ? "" : "v" + mod.version();
    }

    public static String configText(List<ModCatalog.ConfigFile> files, Translator tr) {
        if (files.isEmpty()) {
            return tr.t("controlcore.panel.mods.no_config");
        }
        StringBuilder text = new StringBuilder();
        for (ModCatalog.ConfigFile file : files) {
            text.append(text.isEmpty() ? "" : "\n\n").append(file.name()).append(':');
            for (String line : file.lines()) {
                text.append("\n  ").append(line);
            }
        }
        return text.toString();
    }
}
