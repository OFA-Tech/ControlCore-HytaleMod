package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.List;
import net.ofatech.controlcore.domain.model.diagnostics.DiagnosticsView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class HealthTab {

    static final int MAX_VALUE = 150;

    private static final String REFRESH = "HealthRefresh";
    private static final String REPORT = "HealthReport";
    private static final String LIST = "#HealthList";
    private static final String ROW = "Pages/ControlCore/HealthRow.ui";
    private static final String HEADING = "Pages/ControlCore/SettingCategory.ui";
    private static final String LAYOUT = "Pages/ControlCore/ControlPanel.ui";
    private static final String ICONS = "Pages/ControlCore/Icons/";

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);
    }

    private final DiagnosticsView view;
    private final Translator tr;
    private final Host host;

    HealthTab(DiagnosticsView view, Translator tr, Host host) {
        this.view = view;
        this.tr = tr;
        this.host = host;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#HealthRefresh", EventData.of(PanelAction.KEY, REFRESH));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#HealthReport", EventData.of(PanelAction.KEY, REPORT));
        int index = 0;
        for (DiagnosticsView.Section section : view.sections()) {
            String heading = LIST + "[" + index++ + "]";
            commands.append(LIST, HEADING);
            commands.set(heading + " #Text.Text", section.title());
            long warnings = section.lines().stream()
                    .filter(DiagnosticsView.Line::warning)
                    .count();
            commands.set(
                    heading + " #Count.Text",
                    warnings == 0 ? "" : tr.t("controlcore.panel.health.warnings", "count", warnings));
            for (DiagnosticsView.Line line : section.lines()) {
                String row = LIST + "[" + index++ + "]";
                commands.append(LIST, ROW);
                commands.set(row + " #Label.Text", line.label());
                commands.set(row + " #Value.Text", shorten(line.value()));
                commands.set(row + " #State.Background", ICONS + (line.warning() ? "Error.png" : "Saved.png"));
                if (line.warning()) {
                    commands.set(row + " #Value.Style", Value.ref(LAYOUT, "HealthWarnStyle"));
                }
            }
        }
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case REFRESH -> {
                host.rebuild();
                footer(Result.success(tr.t("controlcore.panel.health.refreshed")));
            }
            case REPORT -> footer(view.writeReport());
            default -> {
                return false;
            }
        }
        return true;
    }

    private void footer(Result<String> result) {
        UICommandBuilder update = new UICommandBuilder();
        update.set("#FooterStatus.Text", result.isSuccess() ? result.value() : tr.error(result.error()));
        host.send(update);
    }

    private static String shorten(String value) {
        String single = String.join(" ", List.of(value.split("\\R")));
        return single.length() <= MAX_VALUE ? single : single.substring(0, MAX_VALUE - 1) + "…";
    }
}
