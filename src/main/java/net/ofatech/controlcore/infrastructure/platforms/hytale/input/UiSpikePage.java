package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.domain.ports.system.Log;

final class UiSpikePage extends InteractiveCustomUIPage<UiSpikePage.Event> {

    static final String LAYOUT = "Pages/ControlCore/UiSpike.ui";
    private static final List<String> TABS = List.of("#TabA", "#TabB", "#TabC");
    private static final Map<String, String> LEGEND =
            Map.of("#LegendAll", "all", "#LegendChanged", "changed", "#LegendRestart", "restart");

    private final Log log;

    UiSpikePage(PlayerRef player, Log log) {
        super(player, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.log = log;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        for (String tab : TABS) {
            events.addEventBinding(CustomUIEventBindingType.Activating, tab, EventData.of(Event.SOURCE, tab));
        }
        for (String legend : LEGEND.keySet()) {
            events.addEventBinding(CustomUIEventBindingType.Activating, legend, EventData.of(Event.SOURCE, legend));
        }
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#Field",
                EventData.of(Event.SOURCE, "typed").append(Event.VALUE, "#Field.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SaveButton",
                EventData.of(Event.SOURCE, "save").append(Event.VALUE, "#Field.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#CloseButton", EventData.of(Event.SOURCE, "close"));
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Event event) {
        log.info("[ui test] " + playerRef.getUsername() + ": " + event.source + " " + event.value);
        UICommandBuilder commands = new UICommandBuilder();
        if ("close".equals(event.source)) {
            close();
            return;
        }
        if (TABS.contains(event.source)) {
            for (String tab : TABS) {
                commands.set(
                        tab + ".Style", Value.ref(LAYOUT, tab.equals(event.source) ? "SelectedTabStyle" : "TabStyle"));
            }
        } else if (LEGEND.containsKey(event.source)) {
            for (String legend : LEGEND.keySet()) {
                commands.set(
                        legend + ".Style",
                        Value.ref(LAYOUT, legend.equals(event.source) ? "LegendOnStyle" : "LegendStyle"));
            }
            commands.set("#LegendState.Text", "showing: " + LEGEND.get(event.source));
        } else if ("typed".equals(event.source)) {
            commands.set("#FieldState.Text", "◐ unsaved: " + event.value);
        } else if ("save".equals(event.source)) {
            commands.set("#FieldState.Text", "● saved: " + event.value);
        }
        sendUpdate(commands, false);
    }

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
