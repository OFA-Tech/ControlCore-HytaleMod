package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.api.NotificationsApi;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.domain.ports.game.Players;

@AutoService(NotificationsApi.class)
public final class NotificationService implements NotificationsApi {

    private final Messenger messenger;
    private final Players players;
    private final SettingPermissionPolicy policy;

    public NotificationService(Messenger messenger, Players players, SettingPermissionPolicy policy) {
        this.messenger = messenger;
        this.players = players;
        this.policy = policy;
    }

    @Override
    public void message(PlayerId player, String text) {
        messenger.send(player, text);
    }

    @Override
    public void message(PlayerId player, String text, String color) {
        messenger.send(player, text, color);
    }

    @Override
    public void broadcast(String text) {
        messenger.broadcast(text);
    }

    @Override
    public void toAdmins(String text) {
        for (PlayerInfo player : players.online()) {
            if (policy.isAdmin(player.id())) {
                messenger.send(player.id(), text);
            }
        }
    }

    @Override
    public void title(PlayerId player, String title, String subtitle) {
        messenger.title(player, title, subtitle == null ? "" : subtitle);
    }

    @Override
    public void titleAll(String title, String subtitle) {
        messenger.titleAll(title, subtitle == null ? "" : subtitle);
    }
}
