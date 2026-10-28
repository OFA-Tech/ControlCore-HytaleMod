package net.ofatech.controlcore.application.players;

import java.util.Optional;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

public final class PlayerHistory {

    public static final String TARGET = PlayerChange.TARGET;

    private final PlayerModeration moderation;
    private final PlayerAccess access;
    private final PlayerControl control;
    private final Texts texts;

    public PlayerHistory(PlayerModeration moderation, PlayerAccess access, PlayerControl control, Texts texts) {
        this.moderation = moderation;
        this.access = access;
        this.control = control;
        this.texts = texts;
    }

    public Result<?> restore(String target, String old, String current, Optional<PlayerId> who) {
        Optional<PlayerChange> change = PlayerChange.of(target);
        if (change.isEmpty()) {
            return Result.failure(PlayerErrors.failed());
        }
        PlayerChange.Parts parts = change.get().parts(target);
        boolean wanted = Boolean.parseBoolean(old);
        Translator tr = texts.forPlayer(who);
        if (change.get() == PlayerChange.KICK) {
            return Result.failure(PlayerErrors.kickUndo());
        }
        if (change.get() == PlayerChange.GROUP_NODE) {
            return access.setGroupPermission(who, parts.extra(), parts.node(), wanted, tr);
        }
        if (parts.player().isEmpty()) {
            return Result.failure(PlayerErrors.failed());
        }
        PlayerId id = parts.player().get();
        return switch (change.get()) {
            case MUTE -> moderation.restoreMute(who, id, old, current);
            case FREEZE -> moderation.freeze(who, id, wanted, tr);
            case WHITELIST -> access.whitelist(who, id, wanted, tr);
            case GROUP -> access.group(who, id, parts.extra(), wanted, tr);
            case GAME_MODE -> control.restoreGameMode(who, id, old, current);
            case KICK, GROUP_NODE -> Result.failure(PlayerErrors.failed());
        };
    }

    public String what(String target, Translator tr) {
        Optional<PlayerChange> change = PlayerChange.of(target);
        if (change.isEmpty()) {
            return target;
        }
        PlayerChange.Parts parts = change.get().parts(target);
        if (change.get() == PlayerChange.GROUP_NODE) {
            return tr.t(change.get().whatKey(), "group", access.label(parts.extra()), "node", parts.node());
        }
        return tr.t(change.get().whatKey(), "player", parts.name(), "group", access.label(parts.extra()));
    }

    public String shown(String target, String value, Translator tr) {
        Optional<PlayerChange> change = PlayerChange.of(target);
        if (change.isEmpty() || value == null) {
            return value == null ? "" : value;
        }
        return switch (change.get()) {
            case GAME_MODE ->
                value.isEmpty() ? tr.t("controlcore.history.value.empty") : PlayerControl.modeName(value, tr);
            case MUTE -> moderation.shownMute(value, tr);
            case KICK -> value.isEmpty() ? tr.t("controlcore.history.value.empty") : value;
            case WHITELIST, GROUP, GROUP_NODE, FREEZE ->
                tr.t(Boolean.parseBoolean(value) ? "controlcore.players.value.yes" : "controlcore.players.value.no");
        };
    }
}
