package net.ofatech.controlcore.application.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.PlainText;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class Recipients {

    static final String WARNING_ID = "command";
    static final int MAX_WARNING_SECONDS = 9999 * 60;
    static final String TOAST = "toast";
    static final String WARNING = "warning";
    static final String CLEAR = "clear";

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
    private final NotificationService notifications;

    public Recipients(Players players, SettingPermissionPolicy policy, NotificationService notifications) {
        this.players = players;
        this.policy = policy;
        this.notifications = notifications;
    }

    public Result<String> toast(Optional<PlayerId> who, String target, String style, String input, Translator tr) {
        Optional<ToastLevel> level = ToastLevel.of(style == null ? "" : style.strip());
        if (level.isEmpty()) {
            return Result.failure(badStyle());
        }
        return text(input)
                .flatMap(text -> resolve(who, target).map(found -> {
                    int bar = text.indexOf('|');
                    String title = bar < 0 ? "" : text.substring(0, bar).strip();
                    String body = (bar < 0 ? text : text.substring(bar + 1)).strip();
                    found.players()
                            .forEach(player -> notifications.toast(
                                    player, title, body, level.get(), AutomationService.TOAST_SECONDS));
                    return tr.t(
                            "controlcore.notify.toast_sent",
                            "target",
                            found.name(tr),
                            "count",
                            found.players().size());
                }));
    }

    public Result<String> warn(
            Optional<PlayerId> who,
            String target,
            int seconds,
            Optional<ToastLevel> style,
            String input,
            Translator tr) {
        if (seconds < 0 || seconds > MAX_WARNING_SECONDS) {
            return Result.failure(Error.validation(
                    "Notify.InvalidSeconds",
                    Text.key("controlcore.errors.notify.invalid_seconds", "Use 0 to {max} seconds.")
                            .with("max", MAX_WARNING_SECONDS)));
        }
        return text(input)
                .flatMap(text -> resolve(who, target).map(found -> {
                    AutomationService.Leveled leveled = style.map(level -> new AutomationService.Leveled(level, text))
                            .orElseGet(() -> AutomationService.Leveled.of(text));
                    if (found.kind() == Kind.EVERYONE) {
                        notifications.warningAll(WARNING_ID, leveled.text(), leveled.level(), seconds);
                    } else {
                        found.players()
                                .forEach(player -> notifications.warning(
                                        player, WARNING_ID, leveled.text(), leveled.level(), seconds));
                    }
                    return tr.t(
                            "controlcore.notify.warning_sent",
                            "target",
                            found.name(tr),
                            "count",
                            found.players().size());
                }));
    }

    public Result<String> clear(Optional<PlayerId> who, String target, Translator tr) {
        return resolve(who, target).map(found -> {
            if (found.kind() == Kind.EVERYONE) {
                notifications.clearWarningAll(WARNING_ID);
            } else {
                found.players().forEach(player -> notifications.clearWarning(player, WARNING_ID));
            }
            return tr.t("controlcore.notify.warning_cleared", "target", found.name(tr));
        });
    }

    Result<String> send(Optional<PlayerId> who, AutomationView.Notice notice, Translator tr) {
        String kind = notice.kind() == null ? "" : notice.kind().strip();
        return switch (kind) {
            case TOAST -> toast(who, notice.target(), notice.style(), notice.text(), tr);
            case CLEAR -> clear(who, notice.target(), tr);
            case WARNING -> {
                Optional<ToastLevel> style = ToastLevel.of(
                        notice.style() == null ? "" : notice.style().strip());
                if (style.isEmpty()) {
                    yield Result.failure(badStyle());
                }
                yield warn(who, notice.target(), seconds(notice.seconds()), style, notice.text(), tr);
            }
            default ->
                Result.failure(Error.validation(
                        "Notify.BadKind",
                        Text.key("controlcore.errors.players.bad_choice", "Use {choices}.")
                                .with("choices", String.join(", ", TOAST, WARNING, CLEAR))));
        };
    }

    List<AutomationView.Choice> targets(Translator tr) {
        List<AutomationView.Choice> choices = new ArrayList<>();
        choices.add(new AutomationView.Choice("everyone", tr.t("controlcore.panel.notice.everyone")));
        choices.add(new AutomationView.Choice("admins", tr.t("controlcore.panel.notice.admins")));
        players.online()
                .forEach(player -> choices.add(
                        new AutomationView.Choice(player.id().value().toString(), player.name())));
        return choices;
    }

    private Result<Target> resolve(Optional<PlayerId> who, String input) {
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
            return Result.success(new Target(Kind.ADMINS, notifications.admins(), "admins"));
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

    private static Error badStyle() {
        return Error.validation(
                "Notify.BadStyle",
                Text.key("controlcore.errors.players.bad_choice", "Use {choices}.")
                        .with("choices", "info, success, warning, error"));
    }

    private static int seconds(String text) {
        String value = text == null ? "" : text.strip();
        if (value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Optional<UUID> uuid(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
