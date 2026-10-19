package net.ofatech.controlcore.infrastructure.platforms.hytale.mapping;

import com.hypixel.hytale.server.core.Message;
import net.ofatech.controlcore.domain.result.Error;

public final class Messages {

    private static final String ERROR_COLOR = "#ff5555";

    private Messages() {}

    public static Message text(String text) {
        return Message.raw(text);
    }

    public static Message error(String text) {
        return Message.raw(text).color(ERROR_COLOR);
    }

    public static Message error(Error error) {
        return error(error.description());
    }
}
