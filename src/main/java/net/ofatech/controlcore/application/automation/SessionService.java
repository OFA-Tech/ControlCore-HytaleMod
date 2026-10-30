package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.api.PlayerSession;
import net.ofatech.controlcore.api.SessionsApi;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Clock;

@AutoService(SessionsApi.class)
public final class SessionService implements SessionsApi {

    public static final String FIRST_JOIN = "session.first-join";
    public static final String LAST_SEEN = "session.last-seen";
    static final String PLAYTIME = "session.playtime-seconds";
    public static final String NAME = "session.name";

    private final PlayerData data;
    private final Players players;
    private final Clock clock;
    private final Map<PlayerId, Instant> onlineSince = new ConcurrentHashMap<>();
    private final Map<PlayerId, String> worlds = new ConcurrentHashMap<>();

    public SessionService(PlayerData data, Players players, Clock clock) {
        this.data = data;
        this.players = players;
        this.clock = clock;
    }

    public synchronized boolean joined(PlayerInfo player) {
        if (onlineSince.containsKey(player.id())) {
            return false;
        }
        Instant now = clock.now();
        boolean first = data.get(player.id(), FIRST_JOIN).isEmpty();
        if (first) {
            data.put(player.id(), FIRST_JOIN, now.toString());
        }
        data.put(player.id(), LAST_SEEN, now.toString());
        data.put(player.id(), NAME, player.name());
        data.flush();
        onlineSince.put(player.id(), now);
        return first;
    }

    public synchronized void left(PlayerInfo player) {
        Instant since = onlineSince.remove(player.id());
        worlds.remove(player.id());
        Instant now = clock.now();
        if (since != null) {
            long seconds = storedPlaytime(player.id())
                    + Math.max(0, Duration.between(since, now).toSeconds());
            data.put(player.id(), PLAYTIME, Long.toString(seconds));
        }
        data.put(player.id(), LAST_SEEN, now.toString());
        data.flush();
    }

    public void enteredWorld(PlayerId player, String world) {
        if (world != null && !world.isBlank()) {
            worlds.put(player, world);
        }
    }

    @Override
    public Optional<PlayerSession> find(PlayerId player) {
        Optional<Instant> first = instant(player, FIRST_JOIN);
        if (first.isEmpty()) {
            return Optional.empty();
        }
        Optional<Instant> since = Optional.ofNullable(onlineSince.get(player));
        Duration playtime = Duration.ofSeconds(storedPlaytime(player))
                .plus(since.map(start -> Duration.between(start, clock.now()))
                        .filter(running -> !running.isNegative())
                        .orElse(Duration.ZERO));
        String name = players.find(player)
                .map(PlayerInfo::name)
                .or(() -> data.get(player, NAME))
                .orElse(player.toString());
        return Optional.of(new PlayerSession(
                player, name, first.get(), instant(player, LAST_SEEN).orElse(first.get()), since, playtime));
    }

    @Override
    public Collection<PlayerSession> online() {
        List<PlayerSession> sessions = new ArrayList<>();
        for (PlayerId player : onlineSince.keySet()) {
            find(player).ifPresent(sessions::add);
        }
        return sessions;
    }

    @Override
    public Optional<String> world(PlayerId player) {
        return Optional.ofNullable(worlds.get(player));
    }

    private long storedPlaytime(PlayerId player) {
        return data.get(player, PLAYTIME)
                .filter(text -> text.matches("\\d{1,15}"))
                .map(Long::parseLong)
                .orElse(0L);
    }

    private Optional<Instant> instant(PlayerId player, String key) {
        try {
            return data.get(player, key).map(Instant::parse);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
