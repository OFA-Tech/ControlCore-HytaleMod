package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.domain.model.ServerSettings;

/** The control panel as a native Hytale custom page ({@code Common/UI/Custom/Pages/ControlCore/ControlPanel.ui}). */
final class ControlPanelPage extends InteractiveCustomUIPage<ControlPanelPage.Action> {

    static final String LAYOUT = "Pages/ControlCore/ControlPanel.ui";
    private static final String CLOSE = "Close";

    private final ServerSettings server;

    ControlPanelPage(PlayerRef player, ServerSettings server) {
        super(player, CustomPageLifetime.CanDismiss, Action.CODEC);
        this.server = server;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        commands.set("#ServerName.Text", orDash(server.serverName()));
        commands.set("#Motd.Text", orDash(server.motd()));
        commands.set("#Password.Text", server.passwordProtected() ? "Set (private server)" : "None (public server)");
        commands.set("#MaxPlayers.Text", Integer.toString(server.maxPlayers()));
        commands.set("#MaxViewRadius.Text", Integer.toString(server.maxViewRadius()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of(Action.KEY, CLOSE));
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Action action) {
        if (CLOSE.equals(action.name)) {
            close();
        }
    }

    private static String orDash(String text) {
        return text.isBlank() ? "-" : text;
    }

    /** Data sent by the client when a bound element is used. */
    static final class Action {

        static final String KEY = "@Action";

        static final BuilderCodec<Action> CODEC = BuilderCodec.builder(Action.class, Action::new)
                .append(
                        new KeyedCodec<>(KEY, Codec.STRING),
                        (action, value) -> action.name = value,
                        action -> action.name)
                .add()
                .build();

        private String name;
    }
}
