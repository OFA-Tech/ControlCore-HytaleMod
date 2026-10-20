package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.domain.ports.system.Log;

/**
 * Test screen for the key system: binds the client's {@code KeyDown} event to one target and shows the raw data
 * the client sends for each key, to learn whether a ControlCore screen can see every key on the keyboard.
 */
final class KeyProbePage extends InteractiveCustomUIPage<KeyProbePage.Event> {

    static final String LAYOUT = "Pages/ControlCore/KeyProbe.ui";
    private static final int SHOWN = 8;

    /** Where the {@code KeyDown} binding is attached. */
    enum Target {
        /** The text field, focused as soon as the screen opens. Binding KeyDown to a group crashes the client. */
        INPUT("#KeyInput");

        final String selector;

        Target(String selector) {
            this.selector = selector;
        }
    }

    private final Target target;
    private final Log log;
    private final Deque<String> lines = new ArrayDeque<>();

    KeyProbePage(PlayerRef player, Target target, Log log) {
        super(player, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.target = target;
        this.log = log;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        commands.set(
                "#Status.Text",
                "KeyDown bound to " + target.name().toLowerCase(java.util.Locale.ROOT)
                        + ". Press keys without clicking anything first.");
        events.addEventBinding(CustomUIEventBindingType.KeyDown, target.selector, EventData.of(Event.SOURCE, "key"));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#KeyInput",
                EventData.of(Event.SOURCE, "typed").append(Event.VALUE, "#KeyInput.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#CloseButton", EventData.of(Event.SOURCE, "close"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String raw) {
        if (raw.contains("\"close\"")) {
            close();
            return;
        }
        log.info("[key probe] " + playerRef.getUsername() + " (" + target + "): " + raw);
        playerRef.sendMessage(Message.raw("[key] " + raw).color("#9ad0ff"));
        lines.addFirst(raw);
        while (lines.size() > SHOWN) {
            lines.removeLast();
        }
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#Status.Text", String.join("\n", lines));
        sendUpdate(commands, false);
    }

    /** Decoded form of the event data; only used as the page's codec, the raw text is what gets shown. */
    static final class Event {

        static final String SOURCE = "Source";
        static final String VALUE = "@Value";

        static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
                .append(new KeyedCodec<>(SOURCE, Codec.STRING), (event, value) -> event.source = value, e -> e.source)
                .add()
                .append(new KeyedCodec<>(VALUE, Codec.STRING), (event, value) -> event.value = value, e -> e.value)
                .add()
                .build();

        private String source;
        private String value;
    }
}
