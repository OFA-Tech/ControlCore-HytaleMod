package net.ofatech.controlcore.application.serverconfig;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.Messenger;

/** Tells admins who join which server config changes still wait for a restart. */
@SuppressWarnings("rawtypes") // AutoService cannot name generic interfaces exactly
@AutoService(ModEventHandler.class)
public final class RestartReminderHandler implements ModEventHandler<PlayerJoined> {

    private final ServerConfigEditor serverConfig;
    private final SettingPermissionPolicy permissions;
    private final Messenger messenger;
    private final Texts texts;

    public RestartReminderHandler(
            ServerConfigEditor serverConfig, SettingPermissionPolicy permissions, Messenger messenger, Texts texts) {
        this.serverConfig = serverConfig;
        this.permissions = permissions;
        this.messenger = messenger;
        this.texts = texts;
    }

    @Override
    public Class<PlayerJoined> eventType() {
        return PlayerJoined.class;
    }

    @Override
    public void handle(PlayerJoined event) {
        List<Text> names = serverConfig.pendingRestart();
        if (names.isEmpty() || !permissions.isAdmin(event.player().id())) {
            return;
        }
        Translator tr = texts.forPlayer(Optional.of(event.player().id()));
        messenger.send(
                event.player().id(),
                tr.t(
                        "controlcore.panel.restart_pending",
                        "keys",
                        names.stream().map(tr::translate).collect(Collectors.joining(", "))));
    }
}
