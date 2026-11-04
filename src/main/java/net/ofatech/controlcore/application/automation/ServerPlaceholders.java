package net.ofatech.controlcore.application.automation;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.ServerHealth;

public final class ServerPlaceholders {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("H:mm");

    private final Players players;
    private final ServerHealth health;
    private final Clock clock;

    public ServerPlaceholders(Players players, ServerHealth health, Clock clock) {
        this.players = players;
        this.health = health;
        this.clock = clock;
    }

    Map<String, String> values(Optional<String> world) {
        Map<String, String> values = new HashMap<>();
        values.put("online", Integer.toString(players.online().size()));
        values.put("max", Integer.toString(health.maxPlayers()));
        values.put(
                "tps",
                health.worlds().stream()
                        .filter(found ->
                                world.map(found.name()::equalsIgnoreCase).orElse(true))
                        .findFirst()
                        .map(found -> Long.toString(Math.round(AutomationService.effectiveTps(found))))
                        .orElse(""));
        values.put("time", CLOCK.format(LocalTime.ofInstant(clock.now(), ZoneId.systemDefault())));
        return values;
    }

    String fill(String text) {
        return AutomationService.fill(text, values(Optional.empty()));
    }
}
