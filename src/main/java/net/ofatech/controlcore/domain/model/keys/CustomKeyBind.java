package net.ofatech.controlcore.domain.model.keys;

import net.ofatech.controlcore.domain.guard.Guard;

public record CustomKeyBind(String name, String keys, String command, boolean runAsServer) {

    public CustomKeyBind {
        Guard.notBlank(name, "name");
        Guard.notNull(keys, "keys");
        Guard.notBlank(command, "command");
    }
}
