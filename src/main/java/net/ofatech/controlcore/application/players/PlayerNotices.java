package net.ofatech.controlcore.application.players;

import java.util.Optional;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class PlayerNotices {

    private static final int TOAST_SECONDS = 6;

    private final PlayerDirectory directory;
    private final Texts texts;
    private final NotificationService notifications;
    private final Log log;

    public PlayerNotices(PlayerDirectory directory, Texts texts, NotificationService notifications, Log log) {
        this.directory = directory;
        this.texts = texts;
        this.notifications = notifications;
        this.log = log;
    }

    Translator of(PlayerId player) {
        return texts.forPlayer(Optional.of(player));
    }

    Translator of(Optional<PlayerId> player) {
        return texts.forPlayer(player);
    }

    void toast(PlayerId player, String text) {
        if (!directory.online(player)) {
            return;
        }
        try {
            notifications.toast(
                    player, of(player).t("controlcore.players.toast.title"), text, ToastLevel.INFO, TOAST_SECONDS);
        } catch (RuntimeException e) {
            log.warn("Could not show a toast to " + directory.nameOf(player) + ": " + e);
        }
    }

    void message(PlayerId player, String text) {
        try {
            notifications.message(player, text);
        } catch (RuntimeException e) {
            log.warn("Could not message " + directory.nameOf(player) + ": " + e);
        }
    }

    String actor(Optional<PlayerId> who, Translator tr) {
        return who.map(directory::nameOf).orElse(tr.t("controlcore.players.console"));
    }
}
