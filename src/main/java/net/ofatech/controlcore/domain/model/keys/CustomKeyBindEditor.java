package net.ofatech.controlcore.domain.model.keys;

import net.ofatech.controlcore.domain.result.Result;

/** Creates admins' own key actions ({@code custom:<name>}) for UIs. */
public interface CustomKeyBindEditor {

    /**
     * @param name lower case name, for example {@code warp-spawn}
     * @param keys default keys, for example {@code ctrl+r}
     * @param command the command to run
     * @param runAsServer whether it runs as the server console
     * @return a confirmation for the player, or why it failed
     */
    Result<String> add(String name, String keys, String command, boolean runAsServer);
}
