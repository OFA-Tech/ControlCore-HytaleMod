package net.ofatech.controlcore.application.keymaps;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;
import net.ofatech.controlcore.domain.ports.game.Scheduler;

public final class KeyDetector implements PlayerInput.Listener {

    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    public record Detected(String keys) {}

    private static final List<Map.Entry<KeySignal, String>> HOLDS = List.of(
            Map.entry(KeySignal.CROUCHING, "CTRL"),
            Map.entry(KeySignal.WALKING, "ALT"),
            Map.entry(KeySignal.SPRINTING, "SHIFT"),
            Map.entry(KeySignal.JUMPING, "SPACE"));

    private static final Map<KeySignal, String> PRESSES = Map.ofEntries(
            Map.entry(KeySignal.ABILITY_1, "Q"),
            Map.entry(KeySignal.ABILITY_2, "E"),
            Map.entry(KeySignal.ABILITY_3, "R"),
            Map.entry(KeySignal.ABILITY_4, "ABILITY_4"),
            Map.entry(KeySignal.USE, "F"),
            Map.entry(KeySignal.GAME_MODE_SWAP, "O"),
            Map.entry(KeySignal.DODGE, "DODGE"),
            Map.entry(KeySignal.WALK_TAP, "ALT"),
            Map.entry(KeySignal.PRIMARY, "MOUSE_LEFT"),
            Map.entry(KeySignal.SECONDARY, "MOUSE_RIGHT"),
            Map.entry(KeySignal.PICK, "MOUSE_MIDDLE"));

    private record Pending(boolean fromGame, Consumer<Optional<Detected>> done, Scheduler.ScheduledTask timeout) {}

    private final KeyDispatcher dispatcher;
    private final Scheduler scheduler;
    private final Map<PlayerId, Pending> pending = new ConcurrentHashMap<>();
    private final Map<PlayerId, Set<KeySignal>> held = new ConcurrentHashMap<>();

    public KeyDetector(KeyDispatcher dispatcher, Scheduler scheduler) {
        this.dispatcher = dispatcher;
        this.scheduler = scheduler;
    }

    public void fromGame(PlayerId player, Consumer<Optional<Detected>> done) {
        start(player, true, done);
    }

    public void fromScreen(PlayerId player, Consumer<Optional<Detected>> done) {
        start(player, false, done);
    }

    public void offer(PlayerId player, Optional<Detected> result) {
        Pending current = pending.get(player);
        if (current != null && !current.fromGame()) {
            finish(player, current, result);
        }
    }

    public void cancel(PlayerId player) {
        Pending current = pending.remove(player);
        if (current != null) {
            current.timeout().cancel();
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
            if (key.isPresent() && finish(player, current, Optional.of(new Detected(withHold(player, key.get()))))) {
                return !signal.isHotbar();
            }
        }
        return dispatcher.pressed(player, signal);
    }

    @Override
    public void held(PlayerId player, Set<KeySignal> now) {
        held.put(player, Set.copyOf(now));
        dispatcher.held(player, now);
    }

    private void start(PlayerId player, boolean fromGame, Consumer<Optional<Detected>> done) {
        Pending[] slot = new Pending[1];
        Scheduler.ScheduledTask timeout = fromGame
                ? scheduler.runLater(TIMEOUT, () -> {
                    if (slot[0] != null) {
                        finish(player, slot[0], Optional.empty());
                    }
                })
                : () -> {};
        slot[0] = new Pending(fromGame, done, timeout);
        Pending previous = pending.put(player, slot[0]);
        if (previous != null) {
            previous.timeout().cancel();
        }
    }

    private boolean finish(PlayerId player, Pending expected, Optional<Detected> result) {
        if (!pending.remove(player, expected)) {
            return false;
        }
        expected.timeout().cancel();
        expected.done().accept(result);
        return true;
    }

    private String withHold(PlayerId player, String key) {
        Set<KeySignal> now = held.getOrDefault(player, Set.of());
        return HOLDS.stream()
                .filter(hold -> now.contains(hold.getKey()) && !hold.getValue().equals(key))
                .findFirst()
                .map(hold -> hold.getValue() + "+" + key)
                .orElse(key);
    }

    private static Optional<String> key(KeySignal signal) {
        if (signal.isHotbar()) {
            return Optional.of(Integer.toString(signal.ordinal() - KeySignal.HOTBAR_1.ordinal() + 1));
        }
        return Optional.ofNullable(PRESSES.get(signal));
    }
}
