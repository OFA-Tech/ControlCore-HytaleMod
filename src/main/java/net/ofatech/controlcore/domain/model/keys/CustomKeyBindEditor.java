package net.ofatech.controlcore.domain.model.keys;

import java.util.Optional;
import net.ofatech.controlcore.domain.result.Result;

/** Creates, edits and removes admins' own key actions ({@code custom:<name>}) for UIs. */
public interface CustomKeyBindEditor {

    /**
     * Creates the bind, or replaces the one with the same name.
     *
     * @param name lower case name, for example {@code warp-spawn}
     * @param keys default keys, for example {@code ctrl+r}
     * @param command the command to run
     * @param runAsServer whether it runs as the server console
     * @return a confirmation for the player, or why it failed
     */
    Result<String> add(String name, String keys, String command, boolean runAsServer);

    /** @param name the bind's name, with or without {@code custom:} */
    Optional<CustomKeyBind> find(String name);

    /**
     * @param name the bind's name, with or without {@code custom:}
     * @return a confirmation for the player, or why it failed
     */
    Result<String> remove(String name);
}
