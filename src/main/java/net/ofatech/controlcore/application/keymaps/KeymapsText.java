package net.ofatech.controlcore.application.keymaps;

import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.keys.KeyCombo;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

public final class KeymapsText {

    private KeymapsText() {}

    public static String problems(Translator tr, KeyBinds binds) {
        List<String> problems = new ArrayList<>();
        if (binds.combos().isEmpty()) {
            problems.add(tr.t("controlcore.keys.no_key"));
        }
        for (KeyCombo combo : binds.combos()) {
            Result<?> trigger = combo.trigger();
            if (trigger.isFailure()) {
                problems.add(
                        tr.t("controlcore.keys.not_available", "combo", combo, "reason", tr.error(trigger.error())));
            }
        }
        return String.join(" ", problems);
    }

    public static String runs(Translator tr, KeyAction action) {
        if (action.command().isEmpty()) {
            return tr.t("controlcore.keys.runs_code", "mod", action.modId());
        }
        String key = action.runsAsServer() ? "controlcore.keys.runs_server" : "controlcore.keys.runs";
        if (action.onPress().isPresent()) {
            key += "_and_code";
        }
        return tr.t(key, "command", action.command().get(), "mod", action.modId());
    }

    public static String line(Translator tr, KeyAction action, KeyBinds binds, String source) {
        String problems = problems(tr, binds);
        return tr.t(
                        "controlcore.keys.line",
                        "name",
                        tr.translate(action.nameText()),
                        "keys",
                        binds,
                        "source",
                        tr.t("controlcore.keys.source." + source),
                        "id",
                        action.id())
                + (problems.isEmpty() ? "" : "\n  " + problems);
    }

    public static String created(Translator tr, KeyAction action) {
        String problems = problems(tr, action.defaultKeys());
        return tr.t(
                        "controlcore.keys.created",
                        "id",
                        action.id(),
                        "keys",
                        action.defaultKeys(),
                        "runs",
                        runs(tr, action))
                + (action.runsAsServer()
                        ? " " + tr.t("controlcore.keys.server_only", "node", KeymapRegistry.SERVER_KEYS_NODE)
                        : "")
                + (problems.isEmpty() ? "" : " " + problems);
    }

    public static String changed(Translator tr, KeyAction action, KeyBinds binds, boolean server) {
        String problems = problems(tr, binds);
        return tr.t(
                        server ? "controlcore.keys.changed_server" : "controlcore.keys.changed_yours",
                        "name",
                        tr.translate(action.nameText()),
                        "keys",
                        binds)
                + (problems.isEmpty() ? "" : " " + problems);
    }

    public static String removed(Translator tr, KeyAction action) {
        return tr.t("controlcore.keys.removed", "id", action.id());
    }
}
