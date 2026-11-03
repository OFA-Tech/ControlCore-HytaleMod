package net.ofatech.controlcore.application.automation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.PlainText;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class Recipients {

    static final String WARNING_ID = "command";
    static final int MAX_WARNING_SECONDS = 9999 * 60;

    enum Kind {
        PLAYER,
        EVERYONE,
        ADMINS
    }

    record Target(Kind kind, List<PlayerId> players, String shown) {

        String name(Translator tr) {
            return kind == Kind.PLAYER
                    ? shown
                    : tr.t("controlcore.notify." + kind.name().toLowerCase(Locale.ROOT));
        }
    }

    private final Players players;
    private final SettingPermissionPolicy policy;

    public Recipients(Players players, SettingPermissionPolicy policy) {
        this.players = players;
        this.policy = policy;
    }

    Result<Target> resolve(CommandContext context, String input) {
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        if (who.isPresent() && !policy.isAdmin(who.get())) {
            return Result.failure(Error.forbidden(
                    "Notify.Forbidden",
                    Text.key("controlcore.errors.notify.forbidden", "Only admins can send toasts and warnings.")));
        }
        String wanted = input == null ? "" : input.strip();
        if (wanted.equalsIgnoreCase("everyone")) {
            return Result.success(new Target(
                    Kind.EVERYONE, players.online().stream().map(PlayerInfo::id).toList(), "everyone"));
        }
        if (wanted.equalsIgnoreCase("admins")) {
            return Result.success(new Target(
                    Kind.ADMINS,
                    players.online().stream()
                            .map(PlayerInfo::id)
                            .filter(policy::isAdmin)
                            .toList(),
                    "admins"));
        }
        Optional<PlayerInfo> found = players.findByName(wanted);
        if (found.isEmpty()) {
            found = uuid(wanted).flatMap(id -> players.find(PlayerId.of(id)));
        }
        if (found.isEmpty()) {
            return Result.failure(Error.validation(
                    "Notify.NotOnline",
                    Text.key("controlcore.errors.players.not_online", "{player} is not online.")
                            .with("player", PlainText.oneLine(wanted, PlainText.MAX_SHOWN_INPUT))));
        }
        return Result.success(
                new Target(Kind.PLAYER, List.of(found.get().id()), found.get().name()));
    }

    static Result<String> text(String input) {
        String text = input == null ? "" : input.strip();
        if (text.isEmpty() || text.length() > AutomationService.MAX_TEXT) {
            return Result.failure(Error.validation(
                    "Notify.InvalidText",
                    Text.key("controlcore.errors.notify.invalid_text", "Write a text of 1 to {max} characters.")
                            .with("max", AutomationService.MAX_TEXT)));
        }
        return Result.success(text);
    }

    private static Optional<UUID> uuid(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
