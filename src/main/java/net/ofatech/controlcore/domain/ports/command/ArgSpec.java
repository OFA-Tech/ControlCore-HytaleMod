package net.ofatech.controlcore.domain.ports.command;

import net.ofatech.controlcore.domain.guard.Guard;

public record ArgSpec(String name, String description, ArgType type, boolean required) {

    public ArgSpec {
        Guard.notBlank(name, "name");
        Guard.notNull(description, "description");
        Guard.notNull(type, "type");
    }
}
