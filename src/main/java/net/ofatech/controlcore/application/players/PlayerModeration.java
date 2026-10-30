package net.ofatech.controlcore.application.players;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.application.history.ChangeHistory;
import net.ofatech.controlcore.application.text.PlainText;
import net.ofatech.controlcore.application.worlds.WorldsService;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.ChatGate;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin.Spot;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.game.Scheduler;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class PlayerModeration {

    static final String MUTED_UNTIL = "players.muted-until";
    static final String FROZEN = "players.frozen";
    static final String PERMANENT = "permanent";
    private static final Duration HOLD_EVERY = Duration.ofSeconds(1);
    private static final Duration SETTLE = Duration.ofSeconds(1);

    private final PlayerDirectory directory;
    private final PlayerGuard guard;
    private final PlayerNotices notices;
    private final PlayerData data;
    private final PlayerAdmin admin;
    private final WorldsService worlds;
    private final ChangeHistory history;
    private final Clock clock;
    private final Scheduler scheduler;
    private final PlayersChanges changes;
    private final Map<PlayerId, Spot> held = new ConcurrentHashMap<>();
    private Scheduler.ScheduledTask holding;

    public PlayerModeration(
            PlayerDirectory directory,
            PlayerGuard guard,
            PlayerNotices notices,
            PlayerData data,
            PlayerAdmin admin,
            WorldsService worlds,
            ChangeHistory history,
            Clock clock,
            Scheduler scheduler,
            PlayersChanges changes) {
        this.directory = directory;
        this.guard = guard;
        this.notices = notices;
        this.data = data;
        this.admin = admin;
        this.worlds = worlds;
        this.history = history;
        this.clock = clock;
        this.scheduler = scheduler;
        this.changes = changes;
    }

    public void listenTo(ChatGate chatGate) {
        chatGate.listen(this::allowChat);
    }

    boolean allowChat(PlayerId sender, String name, String text) {
        Optional<String> muted = activeMute(sender);
        if (muted.isEmpty()) {
            if (data.get(sender, MUTED_UNTIL).isPresent()) {
                clearExpiredMute(sender);
            }
            return true;
        }
        Translator theirs = notices.of(sender);
        notices.message(
                sender,
                PERMANENT.equals(muted.get())
                        ? theirs.t("controlcore.players.you_are_muted")
                        : theirs.t(
                                "controlcore.players.you_are_muted_until",
                                "when",
                                PlainText.when(Instant.parse(muted.get()))));
        return false;
    }

    Optional<String> activeMute(PlayerId id) {
        Optional<String> stored = data.get(id, MUTED_UNTIL).filter(value -> !value.isBlank());
        if (stored.isEmpty() || PERMANENT.equals(stored.get())) {
            return stored;
        }
        return stored.filter(this::inFuture);
    }

    private synchronized void clearExpiredMute(PlayerId id) {
        if (data.get(id, MUTED_UNTIL).isPresent() && activeMute(id).isEmpty()) {
            writeMute(id, "");
        }
    }

    boolean frozen(PlayerId id) {
        return data.get(id, FROZEN).isPresent();
    }

    public CompletableFuture<Result<String>> setMuted(
            Optional<PlayerId> who, String player, String length, boolean add, Translator tr) {
        if (!guard.may(who, PlayerGuard.MUTE)) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.forbidden()));
        }
        Result<Optional<Duration>> duration = add ? BansService.duration(length) : Result.success(Optional.empty());
        if (duration.isFailure()) {
            return PlayerDirectory.completed(Result.failure(duration.error()));
        }
        return directory.resolve(player, id -> mute(who, id, duration.value(), add, tr));
    }

    synchronized Result<String> mute(
            Optional<PlayerId> who, PlayerId id, Optional<Duration> length, boolean add, Translator tr) {
        if (!guard.may(who, PlayerGuard.MUTE)) {
            return Result.failure(PlayerErrors.forbidden());
        }
        String name = directory.nameOf(id);
        Optional<Error> refused = refusal(who, id, name, add);
        if (refused.isPresent()) {
            return Result.failure(refused.get());
        }
        String before = activeMute(id).orElse("");
        if (!add && before.isEmpty()) {
            return Result.failure(PlayerErrors.nothingToDo(
                    "controlcore.errors.players.not_muted", "{player} is not muted.", "player", name));
        }
        String after =
                add ? length.map(time -> clock.now().plus(time).toString()).orElse(PERMANENT) : "";
        if (add && after.equals(before)) {
            return Result.failure(PlayerErrors.nothingToDo(
                    "controlcore.errors.players.already_muted",
                    "{player} is already muted ({length}).",
                    "player",
                    name,
                    "length",
                    shownMute(before, tr)));
        }
        writeMute(id, after);
        history.record(Kind.PLAYER, PlayerChange.MUTE.target(id, name), before, after, false);
        toastMute(who, id, after);
        changes.fire();
        if (add && !before.isEmpty()) {
            return Result.success(tr.t(
                    "controlcore.players.mute_changed",
                    "player",
                    name,
                    "before",
                    shownMute(before, tr),
                    "length",
                    shownMute(after, tr)));
        }
        return Result.success(tr.t(
                add ? "controlcore.players.muted" : "controlcore.players.unmuted",
                "player",
                name,
                "length",
                shownMute(after, tr)));
    }

    synchronized Result<?> restoreMute(Optional<PlayerId> who, PlayerId id, String old) {
        if (!guard.may(who, PlayerGuard.MUTE)) {
            return Result.failure(PlayerErrors.forbidden());
        }
        String wanted = old == null ? "" : old;
        if (!wanted.isEmpty() && !PERMANENT.equals(wanted) && !inFuture(wanted)) {
            return Result.failure(PlayerErrors.muteRanOut());
        }
        String name = directory.nameOf(id);
        Optional<Error> refused = refusal(who, id, name, !wanted.isEmpty());
        if (refused.isPresent()) {
            return Result.failure(refused.get());
        }
        String current = activeMute(id).orElse("");
        if (current.equals(wanted)) {
            Translator tr = notices.of(who);
            return Result.failure(PlayerErrors.nothingToDo(
                    "controlcore.errors.players.mute_unchanged",
                    "{player} is already {state}.",
                    "player",
                    name,
                    "state",
                    shownMute(wanted, tr)));
        }
        writeMute(id, wanted);
        history.record(Kind.PLAYER, PlayerChange.MUTE.target(id, name), current, wanted, false);
        toastMute(who, id, wanted);
        changes.fire();
        return Result.success(true);
    }

    public CompletableFuture<Result<String>> setFrozen(
            Optional<PlayerId> who, String player, boolean add, Translator tr) {
        if (!guard.may(who, PlayerGuard.FREEZE)) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.forbidden()));
        }
        return directory.resolve(player, id -> freeze(who, id, add, tr));
    }

    synchronized Result<String> freeze(Optional<PlayerId> who, PlayerId id, boolean add, Translator tr) {
        if (!guard.may(who, PlayerGuard.FREEZE)) {
            return Result.failure(PlayerErrors.forbidden());
        }
        String name = directory.nameOf(id);
        Optional<Error> refused = refusal(who, id, name, add);
        if (refused.isPresent()) {
            return Result.failure(refused.get());
        }
        if (frozen(id) == add) {
            return Result.failure(PlayerErrors.nothingToDo(
                    add ? "controlcore.errors.players.already_frozen" : "controlcore.errors.players.not_frozen",
                    add ? "{player} is already frozen." : "{player} is not frozen.",
                    "player",
                    name));
        }
        if (add) {
            data.put(id, FROZEN, "true");
        } else {
            data.remove(id, FROZEN);
            held.remove(id);
        }
        data.flush();
        worlds.setFrozen(id, add);
        if (add && directory.online(id)) {
            hold(id);
        }
        history.record(
                Kind.PLAYER, PlayerChange.FREEZE.target(id, name), String.valueOf(!add), String.valueOf(add), false);
        Translator theirs = notices.of(id);
        notices.toast(
                id,
                theirs.t(
                        add ? "controlcore.players.toast.frozen" : "controlcore.players.toast.unfrozen",
                        "actor",
                        notices.actor(who, theirs)));
        changes.fire();
        String key = add
                ? directory.online(id) ? "controlcore.players.frozen" : "controlcore.players.frozen_on_join"
                : "controlcore.players.unfrozen";
        return Result.success(tr.t(key, "player", name));
    }

    public Result<String> kick(Optional<PlayerId> who, PlayerId target, String reason, Translator tr) {
        if (!guard.may(who, PlayerGuard.KICK)) {
            return Result.failure(PlayerErrors.forbidden());
        }
        String name = directory.nameOf(target);
        Optional<Error> refused = refusal(who, target, name, true);
        if (refused.isPresent()) {
            return Result.failure(refused.get());
        }
        if (!directory.online(target)) {
            return Result.failure(PlayerErrors.notOnline(name));
        }
        String cleaned = PlainText.oneLine(reason, PlainText.MAX_REASON);
        String shown = cleaned.isEmpty() ? notices.of(target).t("controlcore.players.kick.default_reason") : cleaned;
        if (!admin.kick(target, shown)) {
            return Result.failure(PlayerErrors.failed());
        }
        history.record(Kind.PLAYER, PlayerChange.KICK.target(target, name), "", shown, false);
        changes.fire();
        return Result.success(tr.t("controlcore.players.kicked", "player", name));
    }

    Result<String> kickNamed(Optional<PlayerId> who, String player, String reason, Translator tr) {
        String input = PlayerDirectory.clean(player);
        if (input.isEmpty()) {
            return Result.failure(PlayerErrors.noPlayer());
        }
        Optional<PlayerId> online = directory.onlineNamed(input);
        if (online.isPresent()) {
            return kick(who, online.get(), reason, tr);
        }
        return directory
                .local(input)
                .<Result<String>>map(found -> Result.failure(PlayerErrors.notOnline(directory.nameOf(found))))
                .orElseGet(() -> Result.failure(PlayerErrors.unknown(input)));
    }

    public void joined(PlayerId id) {
        if (!frozen(id)) {
            return;
        }
        worlds.setFrozen(id, true);
        scheduler.runLater(SETTLE, () -> hold(id));
    }

    public void left(PlayerId id) {
        held.remove(id);
        stopHoldingIfIdle();
    }

    private void hold(PlayerId id) {
        admin.spot(id).thenAccept(spot -> spot.ifPresent(found -> holdAt(id, found)));
    }

    private synchronized void holdAt(PlayerId id, Spot spot) {
        if (!frozen(id) || !directory.online(id)) {
            return;
        }
        held.put(id, spot);
        if (holding == null) {
            holding = scheduler.schedule(HOLD_EVERY, HOLD_EVERY, this::holdFrozen);
        }
    }

    void holdFrozen() {
        held.forEach((id, spot) -> {
            if (directory.online(id) && frozen(id)) {
                admin.keepAt(id, spot);
            } else {
                held.remove(id, spot);
            }
        });
        stopHoldingIfIdle();
    }

    private synchronized void stopHoldingIfIdle() {
        if (held.isEmpty() && holding != null) {
            holding.cancel();
            holding = null;
        }
    }

    private Optional<Error> refusal(Optional<PlayerId> who, PlayerId target, String name, boolean against) {
        return PlayerGuard.self(who, target).or(() -> against ? guard.protects(who, target, name) : Optional.empty());
    }

    private void writeMute(PlayerId id, String value) {
        if (value == null || value.isBlank()) {
            data.remove(id, MUTED_UNTIL);
        } else {
            data.put(id, MUTED_UNTIL, value);
        }
        data.flush();
    }

    private void toastMute(Optional<PlayerId> who, PlayerId id, String after) {
        Translator theirs = notices.of(id);
        notices.toast(
                id,
                after.isEmpty()
                        ? theirs.t("controlcore.players.toast.unmuted", "actor", notices.actor(who, theirs))
                        : theirs.t(
                                "controlcore.players.toast.muted",
                                "actor",
                                notices.actor(who, theirs),
                                "length",
                                shownMute(after, theirs)));
    }

    String shownMute(String value, Translator tr) {
        if (value == null || value.isEmpty()) {
            return tr.t("controlcore.players.not_muted");
        }
        if (PERMANENT.equals(value)) {
            return tr.t("controlcore.players.muted_forever");
        }
        try {
            return tr.t("controlcore.players.muted_until", "when", PlainText.when(Instant.parse(value)));
        } catch (DateTimeParseException broken) {
            return value;
        }
    }

    private boolean inFuture(String value) {
        try {
            return Instant.parse(value).isAfter(clock.now());
        } catch (DateTimeParseException broken) {
            return false;
        }
    }
}
