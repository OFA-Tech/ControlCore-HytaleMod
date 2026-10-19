package net.ofatech.controlcore.domain.ports.command;

import net.ofatech.controlcore.domain.model.PlayerInfo;

public enum ArgType {
    STRING(String.class),
    GREEDY_STRING(String.class),
    INTEGER(Integer.class),
    DECIMAL(Double.class),
    BOOLEAN(Boolean.class),
    PLAYER(PlayerInfo.class);

    private final Class<?> valueType;

    ArgType(Class<?> valueType) {
        this.valueType = valueType;
    }

    public Class<?> valueType() {
        return valueType;
    }
}
