package net.ofatech.controlcore.domain.model.keys;

import net.ofatech.controlcore.domain.guard.Guard;

/**
 * A key bind an admin created that runs a command, shown as the key action {@code custom:<name>}. Listed by
 * {@link net.ofatech.controlcore.api.KeymapsApi#customBinds()}.
 *
 * @param name the bind's name, the part after {@code custom:}
 * @param keys the default keys as text, for example {@code ctrl+f}
 * @param command the command it runs, without {@code /}
 * @param runAsServer {@code true} if the command runs as the server, {@code false} as the player who pressed the
 *     keys
 * @since API 2.4.0
 */
public record CustomKeyBind(String name, String keys, String command, boolean runAsServer) {

    /**
     * Creates a custom bind.
     *
     * @param name the bind's name, the part after {@code custom:}
     * @param keys the default keys as text, for example {@code ctrl+f}
     * @param command the command it runs, without {@code /}
     * @param runAsServer {@code true} if the command runs as the server, {@code false} as the player who pressed
     *     the keys
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code name} or {@code command} is blank or
     *     {@code keys} is {@code null}
     */
    public CustomKeyBind {
        Guard.notBlank(name, "name");
        Guard.notNull(keys, "keys");
        Guard.notBlank(command, "command");
    }
}
