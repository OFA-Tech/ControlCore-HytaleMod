package net.ofatech.controlcore.domain.ports.command;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerInfo;

/** Who ran a command: a player, or the console when {@code player} is empty. */
public record CommandSender(String name, Optional<PlayerInfo> player) {

    public static CommandSender of(PlayerInfo player) {
        return new CommandSender(player.name(), Optional.of(player));
    }

    public static CommandSender console(String name) {
        return new CommandSender(name, Optional.empty());
    }

    public boolean isPlayer() {
        return player.isPresent();
    }
}
