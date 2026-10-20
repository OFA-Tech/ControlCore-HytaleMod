package net.ofatech.controlcore.application.keymaps;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeyCombo;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.model.keys.KeyTrigger;
import net.ofatech.controlcore.domain.ports.game.CommandRunner;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;

/**
 * Turns the signals players' clients send into key actions. A press fires the actions bound to it whose held keys
 * are all down; when several match, only those needing the most held keys fire (Ctrl+F wins over F). Combinations
 * without a press fire when their held keys all go down. Commands and code run in the background.
 */
public final class KeyDispatcher implements PlayerInput.Listener {

    static final Duration COOLDOWN = Duration.ofMillis(250);

    private final KeymapRegistry keymaps;
    private final CommandRunner runner;
    private final Players players;
    private final Clock clock;
    private final Log log;
    private final Map<PlayerId, Set<KeySignal>> held = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastFired = new ConcurrentHashMap<>();

    public KeyDispatcher(KeymapRegistry keymaps, CommandRunner runner, Players players, Clock clock, Log log) {
        this.keymaps = keymaps;
        this.runner = runner;
        this.players = players;
        this.clock = clock;
        this.log = log;
    }

    @Override
    public boolean pressed(PlayerId player, KeySignal signal) {
        Set<KeySignal> current = held.getOrDefault(player, Set.of());
        List<KeyAction> best = new ArrayList<>();
        int bestHeld = -1;
        for (KeyAction action : keymaps.actions()) {
            int matched = matchedHeld(action, player, signal, current);
            if (matched > bestHeld) {
                best.clear();
                bestHeld = matched;
            }
            if (matched >= 0 && matched == bestHeld) {
                best.add(action);
            }
        }
        boolean block = false;
        for (KeyAction action : best) {
            if (fire(action, player)) {
                block |= action.blocksGameAction();
            }
        }
        return block && !signal.isHotbar();
    }

    @Override
    public void held(PlayerId player, Set<KeySignal> now) {
        Set<KeySignal> before = held.getOrDefault(player, Set.of());
        held.put(player, Set.copyOf(now));
        if (before.equals(now)) {
            return;
        }
        for (KeyAction action : keymaps.actions()) {
            for (KeyCombo combo : keymaps.keysFor(action.id(), player).combos()) {
                KeyTrigger trigger = combo.trigger().orElse(null);
                if (trigger != null && trigger.firesOnChange(before, now)) {
                    fire(action, player);
                    break;
                }
            }
        }
    }

    /** Forgets a player who left. */
    public void forget(PlayerId player) {
        held.remove(player);
        lastFired.keySet().removeIf(key -> key.startsWith(player + "|"));
    }

    /** @return the number of held keys of the best combination of {@code action} that fires, or -1 */
    private int matchedHeld(KeyAction action, PlayerId player, KeySignal signal, Set<KeySignal> current) {
        int best = -1;
        for (KeyCombo combo : keymaps.keysFor(action.id(), player).combos()) {
            KeyTrigger trigger = combo.trigger().orElse(null);
            if (trigger != null && trigger.firesOn(signal, current)) {
                best = Math.max(best, trigger.held().size());
            }
        }
        return best;
    }

    private boolean fire(KeyAction action, PlayerId player) {
        Instant now = clock.now();
        String key = player + "|" + action.id();
        Instant last = lastFired.get(key);
        if (last != null && Duration.between(last, now).compareTo(COOLDOWN) < 0) {
            return true;
        }
        lastFired.put(key, now);
        String name = players.find(player).map(info -> info.name()).orElse(player.toString());
        log.debug("Key action " + action.id() + " for " + name);
        action.commandFor(player, name).ifPresent(command -> runner.runAs(player, command));
        action.onPress()
                .ifPresent(code -> runner.runInBackground("key action " + action.id(), () -> code.accept(player)));
        return true;
    }
}
