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
import java.time.Duration;
import java.util.List;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.domain.model.ServerSettings;

/**
 * The control panel as a native Hytale custom page ({@code Common/UI/Custom/Pages/ControlCore/ControlPanel.ui}).
 * Tabs are text buttons that show one {@code #...Tab} group and disable their own button.
 */
final class ControlPanelPage extends InteractiveCustomUIPage<ControlPanelPage.Action> {

    static final String LAYOUT = "Pages/ControlCore/ControlPanel.ui";

    private static final String CLOSE = "Close";
    private static final String TAB_PREFIX = "Tab:";

    private enum Tab {
        SERVER("#ServerTab", "#ServerTabButton"),
        PLAYER("#PlayerTab", "#PlayerTabButton"),
        KEYMAP("#KeymapTab", "#KeymapTabButton");

        private final String group;
        private final String button;

        Tab(String group, String button) {
            this.group = group;
            this.button = button;
        }
    }

    /** Buttons shown for upcoming features; disabled until they are wired. */
    private static final List<String> NOT_WIRED_YET = List.of(
            "#RebindOpenPanel",
            "#ResetOpenPanel",
            "#RebindReloadServer",
            "#ResetReloadServer",
            "#RebindToggleDebug",
            "#ResetToggleDebug");

    private final ServerSettings server;
    private Tab selected = Tab.SERVER;

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
        commands.set("#PlayTimeout.Text", describe(server.playTimeout()));
        NOT_WIRED_YET.forEach(button -> commands.set(button + ".Disabled", true));
        showTab(commands);

        for (Tab tab : Tab.values()) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating, tab.button, EventData.of(Action.KEY, TAB_PREFIX + tab.name()));
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of(Action.KEY, CLOSE));
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Action action) {
        String name = action.name != null ? action.name : "";
        if (CLOSE.equals(name)) {
            close();
        } else if (name.startsWith(TAB_PREFIX)) {
            selectTab(name.substring(TAB_PREFIX.length()));
        }
    }

    private void selectTab(String tabName) {
        for (Tab tab : Tab.values()) {
            if (tab.name().equals(tabName) && tab != selected) {
                selected = tab;
                UICommandBuilder update = new UICommandBuilder();
                showTab(update);
                sendUpdate(update, false);
                return;
            }
        }
    }

    private void showTab(UICommandBuilder commands) {
        for (Tab tab : Tab.values()) {
            commands.set(tab.group + ".Visible", tab == selected);
            commands.set(tab.button + ".Disabled", tab == selected);
        }
    }

    private static String describe(Duration duration) {
        if (duration.isZero()) {
            return "-";
        }
        long minutes = duration.toMinutes();
        String readable = duration.toSecondsPart() == 0 && minutes > 0
                ? minutes + (minutes == 1 ? " minute" : " minutes")
                : duration.toSeconds() + " seconds";
        return readable + " (" + duration + ")";
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
