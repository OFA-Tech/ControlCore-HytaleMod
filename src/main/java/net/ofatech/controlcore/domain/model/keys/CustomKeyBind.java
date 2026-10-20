package net.ofatech.controlcore.domain.model.keys;

import net.ofatech.controlcore.domain.guard.Guard;

/**
 * A key action an admin created without a mod, saved by ControlCore and registered as {@code custom:<name>}.
 *
 * @param name the key part of the id, for example {@code warp-spawn}
 * @param keys the default keys as written, for example {@code CTRL + F}
 * @param command the command to run, without {@code /}
 * @param runAsServer whether the command runs as the server console
 */
public record CustomKeyBind(String name, String keys, String command, boolean runAsServer) {

    public CustomKeyBind {
        Guard.notBlank(name, "name");
        Guard.notNull(keys, "keys");
        Guard.notBlank(command, "command");
    }
}
