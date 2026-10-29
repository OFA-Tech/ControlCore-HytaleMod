package net.ofatech.controlcore.application.keymaps;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.GameAction;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyCombo;
import net.ofatech.controlcore.domain.model.keys.KeyInput;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;
import net.ofatech.controlcore.domain.ports.game.Scheduler;

public final class KeyDetector implements PlayerInput.Listener {

    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final Duration SCREEN_TIMEOUT = Duration.ofMinutes(5);

    public record Detected(String keys) {}

    private static final Map<KeySignal, String> HOLDS = holds();
    private static final Map<KeySignal, String> PRESSES = presses();
    private static final Set<KeySignal> CLICKS = Set.of(KeySignal.PRIMARY, KeySignal.SECONDARY);

    private record Pending(
            long token,
            boolean fromGame,
            Set<KeySignal> heldAtStart,
            Consumer<Optional<Detected>> done,
            AtomicReference<Scheduler.ScheduledTask> timeout) {

        void stopTimer() {
            Scheduler.ScheduledTask task = timeout.get();
            if (task != null) {
                task.cancel();
            }
        }
    }

    private final KeyDispatcher dispatcher;
    private final Scheduler scheduler;
    private final AtomicLong tokens = new AtomicLong();
    private final Map<PlayerId, Pending> pending = new ConcurrentHashMap<>();
    private final Map<PlayerId, Set<KeySignal>> held = new ConcurrentHashMap<>();

    public KeyDetector(KeyDispatcher dispatcher, Scheduler scheduler) {
        this.dispatcher = dispatcher;
        this.scheduler = scheduler;
    }

    public long fromGame(PlayerId player, Consumer<Optional<Detected>> done) {
        return start(player, true, done);
    }

    public long fromScreen(PlayerId player, Consumer<Optional<Detected>> done) {
        return start(player, false, done);
    }

    public void offer(PlayerId player, long token, Optional<Detected> result) {
        Pending current = pending.get(player);
        if (current != null && !current.fromGame() && current.token() == token) {
            finish(player, current, result);
        }
    }

    public void cancel(PlayerId player) {
        Pending current = pending.remove(player);
        if (current != null) {
            current.stopTimer();
        }
    }

    public void forget(PlayerId player) {
        cancel(player);
        held.remove(player);
    }

    @Override
    public boolean pressed(PlayerId player, KeySignal signal) {
        Pending current = pending.get(player);
        if (current != null && current.fromGame()) {
            Optional<String> key = key(signal);
            List<String> holds =
                    key.map(pressed -> holds(player, current, pressed)).orElse(List.of());
            boolean bareClick = CLICKS.contains(signal) && holds.isEmpty();
            if (key.isPresent()
                    && !bareClick
                    && finish(player, current, Optional.of(new Detected(join(holds, key.get()))))) {
                return !signal.isHotbar();
            }
        }
        return dispatcher.pressed(player, signal);
    }

    @Override
    public void held(PlayerId player, Set<KeySignal> now) {
        held.put(player, Set.copyOf(now));
        Pending current = pending.get(player);
        if (current != null) {
            current.heldAtStart().retainAll(now);
        }
        dispatcher.held(player, now);
    }

    private long start(PlayerId player, boolean fromGame, Consumer<Optional<Detected>> done) {
        long token = tokens.incrementAndGet();
        Set<KeySignal> heldAtStart = ConcurrentHashMap.newKeySet();
        heldAtStart.addAll(held.getOrDefault(player, Set.of()));
        Pending next = new Pending(token, fromGame, heldAtStart, done, new AtomicReference<>());
        Pending previous = pending.put(player, next);
        if (previous != null) {
            previous.stopTimer();
        }
        next.timeout()
                .set(scheduler.runLater(
                        fromGame ? TIMEOUT : SCREEN_TIMEOUT, () -> finish(player, next, Optional.empty())));
        return token;
    }

    private boolean finish(PlayerId player, Pending expected, Optional<Detected> result) {
        if (!pending.remove(player, expected)) {
            return false;
        }
        expected.stopTimer();
        expected.done().accept(result);
        return true;
    }

    private List<String> holds(PlayerId player, Pending detection, String key) {
        Set<KeySignal> now = held.getOrDefault(player, Set.of());
        return HOLDS.entrySet().stream()
                .filter(hold -> now.contains(hold.getKey())
                        && !detection.heldAtStart().contains(hold.getKey())
                        && !hold.getValue().equals(key))
                .map(Map.Entry::getValue)
                .toList();
    }

    private static String join(List<String> holds, String key) {
        return Stream.concat(holds.stream(), Stream.of(key)).collect(Collectors.joining("+"));
    }

    private static Optional<String> key(KeySignal signal) {
        return Optional.ofNullable(PRESSES.get(signal));
    }

    private static Map<KeySignal, String> holds() {
        Map<KeySignal, String> holds = new LinkedHashMap<>();
        for (Key key : Stream.concat(Key.HOLDS.stream(), Stream.of(Key.SPACE)).toList()) {
            key.signal(false).ifPresent(signal -> holds.put(signal, KeyCombo.write(key)));
        }
        return holds;
    }

    private static Map<KeySignal, String> presses() {
        Map<KeySignal, String> presses = new EnumMap<>(KeySignal.class);
        Stream.<KeyInput>concat(Stream.of(Key.values()), Stream.of(GameAction.values()))
                .forEach(input -> input.signal(true)
                        .filter(KeySignal::isPress)
                        .ifPresent(signal -> presses.putIfAbsent(signal, KeyCombo.write(input))));
        return presses;
    }
}
