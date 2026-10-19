package net.ofatech.controlcore.domain.ports.command;

import java.util.Optional;

/** Text sent back to the command sender; empty sends nothing. */
public record Reply(Optional<String> text) {

    public static Reply of(String text) {
        return new Reply(Optional.of(text));
    }

    public static Reply none() {
        return new Reply(Optional.empty());
    }
}
