package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.server.RestartView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class RestartControls {

    static final String BANNER = "RestartNow";
    static final String HEALTH = "HealthRestart";

    private static final long CONFIRM_MILLIS = 10_000;
    private static final int HEALTH_LIST = 620;
    private static final int HEALTH_ROW = 82;

    interface Host {
        void send(UICommandBuilder update);

        void showRestartNotice(UICommandBuilder update);

        void resize(String element, int pixels, UICommandBuilder update);

        void whileOpen(Runnable work);
    }

    private final Optional<RestartView> view;
    private final Translator tr;
    private final Host host;
    private Optional<String> armed = Optional.empty();
    private long armedAt;

    RestartControls(Optional<RestartView> view, Translator tr, Host host) {
        this.view = view;
        this.tr = tr;
        this.host = host;
    }

    Optional<Subscription> listen() {
        return view.map(shown -> shown.onChange(() -> host.whileOpen(() -> {
            UICommandBuilder update = new UICommandBuilder();
            host.showRestartNotice(update);
            showHealth(update);
            host.send(update);
        })));
    }

    void build(UICommandBuilder commands, UIEventBuilder events, boolean health) {
        commands.set("#HealthRestartRow.Visible", view.isPresent() && health);
        commands.set("#HealthRestartNote.Visible", view.isPresent() && health);
        if (view.isEmpty()) {
            return;
        }
        List<DropdownEntryInfo> delays = view.get().delays().stream()
                .map(choice -> new DropdownEntryInfo(LocalizableString.fromString(choice.label()), choice.id()))
                .toList();
        for (String[] place : List.of(
                new String[] {"#RestartNow", "#RestartDelay", BANNER},
                new String[] {"#HealthRestart", "#HealthRestartDelay", HEALTH})) {
            commands.set(place[1] + ".Entries", delays);
            commands.set(place[1] + ".Value", "default");
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    place[0],
                    new EventData().append(PanelAction.KEY, place[2]).append(PanelAction.VALUE, place[1] + ".Value"));
        }
        if (health) {
            host.resize("#HealthList", HEALTH_LIST - HEALTH_ROW, commands);
            showHealth(commands);
        }
    }

    void showBanner(UICommandBuilder commands, boolean pending) {
        if (view.isEmpty()) {
            commands.set("#RestartNow.Visible", false);
            commands.set("#RestartDelay.Visible", false);
            return;
        }
        boolean counting = view.get().counting();
        if (counting) {
            commands.set("#RestartBanner.Visible", true);
            commands.set("#RestartText.Text", view.get().status());
        }
        commands.set("#RestartNow.Visible", counting || pending);
        commands.set("#RestartDelay.Visible", !counting && pending);
        commands.set("#RestartNow.Text", label(counting));
    }

    private void showHealth(UICommandBuilder commands) {
        if (view.isEmpty()) {
            return;
        }
        boolean counting = view.get().counting();
        commands.set("#HealthRestartText.Text", view.get().status());
        commands.set("#HealthRestartDelay.Visible", !counting);
        commands.set("#HealthRestart.Text", label(counting));
    }

    private String label(boolean counting) {
        return tr.t(counting ? "controlcore.panel.restart.cancel" : "controlcore.panel.restart.now");
    }

    boolean handle(PanelAction action) {
        String name = action.name();
        if (!BANNER.equals(name) && !HEALTH.equals(name)) {
            return false;
        }
        if (view.isEmpty()) {
            host.send(new UICommandBuilder());
            return true;
        }
        if (view.get().counting()) {
            armed = Optional.empty();
            show(view.get().cancel());
            return true;
        }
        boolean fresh = System.currentTimeMillis() - armedAt < CONFIRM_MILLIS;
        if (armed.filter(name::equals).isEmpty() || !fresh) {
            armed = Optional.of(name);
            armedAt = System.currentTimeMillis();
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.t("controlcore.panel.restart.confirm"));
            host.send(update);
            return true;
        }
        armed = Optional.empty();
        show(view.get().restart(action.value == null ? "" : action.value));
        return true;
    }

    private void show(Result<String> result) {
        UICommandBuilder update = new UICommandBuilder();
        host.showRestartNotice(update);
        showHealth(update);
        update.set("#FooterStatus.Text", result.isSuccess() ? result.value() : tr.error(result.error()));
        host.send(update);
    }
}
