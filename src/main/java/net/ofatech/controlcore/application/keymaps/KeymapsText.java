package net.ofatech.controlcore.application.keymaps;

import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.keys.KeyCombo;
import net.ofatech.controlcore.domain.result.Result;

/** Texts shared by the keymap commands and the Keymaps tab. */
public final class KeymapsText {

    private KeymapsText() {}

    /** @return why some of the combinations cannot fire, or an empty string if they all can */
    public static String problems(KeyBinds binds) {
        List<String> problems = new ArrayList<>();
        for (KeyCombo combo : binds.combos()) {
            Result<?> trigger = combo.trigger();
            if (trigger.isFailure()) {
                problems.add(combo + " is not available: " + trigger.error().description());
            }
        }
        return String.join(" ", problems);
    }

    /** @return what the action does, for example {@code Runs /prefs} */
    public static String runs(KeyAction action) {
        String code = action.onPress().isPresent() ? "runs " + action.modId() + " code" : "";
        String command = action.command()
                .map(text -> "Runs /" + text + (action.runsAsServer() ? " as the server" : ""))
                .orElse("");
        if (command.isEmpty()) {
            return "R" + code.substring(1);
        }
        return code.isEmpty() ? command : command + " and " + code;
    }

    /** @return one line of {@code /cc-keys} */
    public static String line(KeyAction action, KeyBinds binds, String source) {
        String problems = problems(binds);
        return action.displayName() + ": " + binds + " (" + source + ") · " + action.id()
                + (problems.isEmpty() ? "" : "\n  " + problems);
    }

    /** @return the reply after an admin created a key bind */
    public static String created(KeyAction action) {
        String problems = problems(action.defaultKeys());
        String runs = runs(action);
        return "Key bind " + action.id() + " (" + action.defaultKeys() + ") "
                + Character.toLowerCase(runs.charAt(0)) + runs.substring(1) + "."
                + (action.runsAsServer()
                        ? " Only players with " + KeymapRegistry.SERVER_KEYS_NODE + " can use it."
                        : "")
                + (problems.isEmpty() ? "" : " " + problems);
    }

    /** @return the reply after a change */
    public static String changed(KeyAction action, KeyBinds binds, String whose) {
        String problems = problems(binds);
        return whose + " keys for " + action.displayName() + " are now " + binds + "."
                + (problems.isEmpty() ? "" : " " + problems);
    }
}
