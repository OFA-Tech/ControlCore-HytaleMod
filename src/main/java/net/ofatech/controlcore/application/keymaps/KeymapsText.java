package net.ofatech.controlcore.application.keymaps;

import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.keys.KeyCombo;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

/** Texts shared by the keymap commands and the Keymaps tab, in the reader's language. */
public final class KeymapsText {

    private KeymapsText() {}

    /** @return why some of the combinations cannot fire, or an empty string if they all can */
    public static String problems(Translator tr, KeyBinds binds) {
        List<String> problems = new ArrayList<>();
        for (KeyCombo combo : binds.combos()) {
            Result<?> trigger = combo.trigger();
            if (trigger.isFailure()) {
                problems.add(
                        tr.t("controlcore.keys.not_available", "combo", combo, "reason", tr.error(trigger.error())));
            }
        }
        return String.join(" ", problems);
    }

    /** @return what the action does, for example {@code Runs /prefs} */
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

    /**
     * @param source where the keys come from: {@code yours}, {@code server} or {@code default}
     * @return one line of {@code /cc-keys}
     */
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

    /** @return the reply after an admin created a key bind */
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

    /**
     * @param server whether the server's keys changed (otherwise the player's own)
     * @return the reply after a change
     */
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

    /** @return the reply after an admin removed a key bind */
    public static String removed(Translator tr, KeyAction action) {
        return tr.t("controlcore.keys.removed", "id", action.id());
    }
}
