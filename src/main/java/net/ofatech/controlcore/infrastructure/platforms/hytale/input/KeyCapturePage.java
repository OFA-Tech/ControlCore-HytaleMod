package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.domain.model.text.Translator;

final class KeyCapturePage extends InteractiveCustomUIPage<KeyCapturePage.Event> {

    static final String LAYOUT = "Pages/ControlCore/KeyCapture.ui";
    private static final Pattern KEYCODE = Pattern.compile("\"Keycode\"\\s*:\\s*(\\d{1,10})");
    private static final Pattern CANCEL = Pattern.compile("\"Source\"\\s*:\\s*\"cancel\"");
    private static final Pattern REPEAT = Pattern.compile("\"Repeat\"\\s*:\\s*true");

    private final Consumer<Optional<String>> done;
    private final Translator tr;
    private final KeyCaptureLogic logic;
    private final BooleanSupplier anyKey;
    private final AtomicBoolean finished = new AtomicBoolean();

    KeyCapturePage(PlayerRef player, Translator tr, BooleanSupplier anyKey, Consumer<Optional<String>> done) {
        super(player, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.tr = tr;
        this.anyKey = anyKey;
        this.logic = new KeyCaptureLogic(anyKey);
        this.done = done;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        if (!anyKey.getAsBoolean()) {
            commands.set("#CaptureHint.Text", tr.t("controlcore.ui.keyCapture.hintHeard"));
        }
        events.addEventBinding(CustomUIEventBindingType.KeyDown, "#CaptureInput", EventData.of(Event.SOURCE, "key"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#CaptureCancel", EventData.of(Event.SOURCE, "cancel"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String raw) {
        if (CANCEL.matcher(raw).find()) {
            finish(Optional.empty());
            return;
        }
        Matcher keycode = KEYCODE.matcher(raw);
        long code = keycode.find() ? Long.parseLong(keycode.group(1)) : -1;
        KeyCaptureLogic.Outcome outcome = code >= 0 && code <= Integer.MAX_VALUE
                ? logic.accept((int) code, REPEAT.matcher(raw).find(), System.currentTimeMillis())
                : new KeyCaptureLogic.Outcome.Waiting();
        UICommandBuilder update = new UICommandBuilder();
        switch (outcome) {
            case KeyCaptureLogic.Outcome.Captured captured -> {
                finish(Optional.of(captured.keys()));
                return;
            }
            case KeyCaptureLogic.Outcome.Cancelled cancelled -> {
                finish(Optional.empty());
                return;
            }
            case KeyCaptureLogic.Outcome.Holding holding ->
                update.set("#CaptureHint.Text", tr.t("controlcore.ui.keyCapture.holding", "keys", holding.holds()));
            case KeyCaptureLogic.Outcome.Unsupported unsupported ->
                update.set("#CaptureHint.Text", tr.t("controlcore.ui.keyCapture.unsupported"));
            case KeyCaptureLogic.Outcome.NotInLists notInLists ->
                update.set(
                        "#CaptureHint.Text", tr.t("controlcore.ui.keyCapture.notInLists", "key", notInLists.label()));
            case KeyCaptureLogic.Outcome.Waiting waiting -> {}
        }
        sendUpdate(update, false);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);
        if (finished.compareAndSet(false, true)) {
            done.accept(Optional.empty());
        }
    }

    private void finish(Optional<String> result) {
        if (finished.compareAndSet(false, true)) {
            close();
            done.accept(result);
        }
    }

    static final class Event {

        static final String SOURCE = "Source";

        static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
                .append(new KeyedCodec<>(SOURCE, Codec.STRING), (event, value) -> event.source = value, e -> e.source)
                .add()
                .build();

        private String source;
    }
}
