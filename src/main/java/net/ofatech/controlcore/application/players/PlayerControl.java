package net.ofatech.controlcore.application.players;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.history.ChangeHistory;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class PlayerControl {

    private final PlayerDirectory directory;
    private final PlayerGuard guard;
    private final PlayerModeration moderation;
    private final PlayerAdmin admin;
    private final ChangeHistory history;
    private final Texts texts;
    private final NotificationService notifications;
    private final PlayersChanges changes;
    private final Log log;

    public PlayerControl(
            PlayerDirectory directory,
            PlayerGuard guard,
            PlayerModeration moderation,
            PlayerAdmin admin,
            ChangeHistory history,
            Texts texts,
            NotificationService notifications,
            PlayersChanges changes,
            Log log) {
        this.directory = directory;
        this.guard = guard;
        this.moderation = moderation;
        this.admin = admin;
        this.history = history;
        this.texts = texts;
        this.notifications = notifications;
        this.changes = changes;
        this.log = log;
    }

    public CompletableFuture<Result<String>> toggleGameMode(Optional<PlayerId> who, PlayerId target, Translator tr) {
        if (!guard.may(who, PlayerGuard.GAME_MODE)) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.forbidden()));
        }
        String name = directory.nameOf(target);
        if (!directory.online(target)) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.notOnline(name)));
        }
        ChangeContext.Attribution attribution = ChangeContext.current();
        return admin.gameMode(target).thenCompose(current -> {
            String from = current.orElse(PlayerAdmin.ADVENTURE);
            String to = PlayerAdmin.CREATIVE.equals(from) ? PlayerAdmin.ADVENTURE : PlayerAdmin.CREATIVE;
            return admin.setGameMode(target, to)
                    .thenApply(ok -> ChangeContext.as(attribution, () -> {
                        if (!ok) {
                            return Result.<String>failure(PlayerErrors.failed());
                        }
                        recordGameMode(who, target, from, to);
                        return Result.success(
                                tr.t("controlcore.players.gamemode_set", "player", name, "mode", modeName(to, tr)));
                    }));
        });
    }

    Result<?> restoreGameMode(Optional<PlayerId> who, PlayerId target, String old, String current) {
        if (!(PlayerAdmin.ADVENTURE.equals(old) || PlayerAdmin.CREATIVE.equals(old))) {
            return Result.failure(PlayerErrors.failed());
        }
        if (!guard.may(who, PlayerGuard.GAME_MODE)) {
            return Result.failure(PlayerErrors.forbidden());
        }
        String name = directory.nameOf(target);
        if (!directory.online(target)) {
            return Result.failure(PlayerErrors.notOnline(name));
        }
        ChangeContext.Attribution attribution = ChangeContext.current();
        admin.setGameMode(target, old).whenComplete((ok, failure) -> {
            if (Boolean.TRUE.equals(ok)) {
                ChangeContext.as(attribution, () -> {
                    recordGameMode(who, target, current, old);
                    return null;
                });
                return;
            }
            log.warn("Rolling back the game mode of " + name + " did not work"
                    + (failure == null ? "" : ": " + failure));
            who.ifPresent(actor -> notifications.message(
                    actor, texts.forPlayer(who).t("controlcore.players.gamemode_rollback_failed", "player", name)));
        });
        return Result.success(true);
    }

    private void recordGameMode(Optional<PlayerId> who, PlayerId target, String from, String to) {
        String name = directory.nameOf(target);
        history.record(Kind.PLAYER, PlayerChange.GAME_MODE.target(target, name), from, to, false);
        Translator theirs = texts.forPlayer(Optional.of(target));
        moderation.toast(
                target,
                theirs.t(
                        "controlcore.players.toast.gamemode",
                        "actor",
                        moderation.actor(who, theirs),
                        "mode",
                        modeName(to, theirs)));
        changes.fire();
    }

    public CompletableFuture<Result<String>> teleport(
            Optional<PlayerId> who, PlayerId mover, PlayerId to, boolean bring, Translator tr) {
        if (!guard.may(who, PlayerGuard.TELEPORT)) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.forbidden()));
        }
        if (who.isEmpty()) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.playersOnly()));
        }
        if (mover.equals(to)) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.self()));
        }
        for (PlayerId needed : List.of(mover, to)) {
            if (!directory.online(needed)) {
                return PlayerDirectory.completed(Result.failure(PlayerErrors.notOnline(directory.nameOf(needed))));
            }
        }
        if (bring) {
            Optional<Error> refused = guard.protects(who, mover, directory.nameOf(mover));
            if (refused.isPresent()) {
                return PlayerDirectory.completed(Result.failure(refused.get()));
            }
        }
        PlayerId other = bring ? mover : to;
        return admin.teleport(mover, to).thenApply(ok -> {
            if (!ok) {
                return Result.<String>failure(PlayerErrors.failed());
            }
            if (bring) {
                Translator theirs = texts.forPlayer(Optional.of(mover));
                moderation.toast(
                        mover, theirs.t("controlcore.players.toast.brought", "actor", moderation.actor(who, theirs)));
            }
            return Result.success(tr.t(
                    bring ? "controlcore.players.brought" : "controlcore.players.teleported",
                    "player",
                    directory.nameOf(other)));
        });
    }

    static String modeName(String mode, Translator tr) {
        return tr.t("controlcore.players.mode." + mode);
    }
}
