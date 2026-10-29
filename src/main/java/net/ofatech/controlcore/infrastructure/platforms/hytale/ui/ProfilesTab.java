package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.snapshot.ProfilesView;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class ProfilesTab {

    static final int LIST_HEIGHT = 470;
    static final int PREVIEW_HEIGHT = 240;

    private static final String PREVIEW = "ProfilePreview";
    private static final String SWITCH = "ProfileSwitch";
    private static final String CLOSE_PREVIEW = "ProfilePreviewClose";
    private static final String UNMARK = "ProfileUnmark";
    private static final String ROW = "Pages/ControlCore/ProfileRow.ui";
    private static final String LIST = "#ProfileList";
    private static final String ICONS = "Pages/ControlCore/Icons/";

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);

        void showRestartNotice(UICommandBuilder update);

        void resize(String element, int height, UICommandBuilder update);
    }

    private final ProfilesView view;
    private final Translator tr;
    private final Host host;
    private Optional<String> previewing = Optional.empty();

    ProfilesTab(ProfilesView view, Translator tr, Host host) {
        this.view = view;
        this.tr = tr;
        this.host = host;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        commands.set("#ProfileActive.Text", view.status());
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#ProfileSwitch", EventData.of(PanelAction.KEY, SWITCH));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ProfilePreviewClose",
                EventData.of(PanelAction.KEY, CLOSE_PREVIEW));
        List<ProfilesView.Entry> entries = view.list();
        for (int index = 0; index < entries.size(); index++) {
            ProfilesView.Entry entry = entries.get(index);
            String row = LIST + "[" + index + "]";
            commands.append(LIST, ROW);
            commands.set(row + " #Name.Text", entry.name());
            commands.set(row + " #Details.Text", entry.details());
            commands.set(
                    row + " #Badge.Text",
                    tr.t(entry.builtIn() ? "controlcore.panel.profiles.built_in" : "controlcore.panel.profiles.own"));
            commands.set(row + " #ActiveBadge.Visible", entry.active());
            commands.set(row + " #ActiveBadge.Text", tr.t("controlcore.panel.profiles.active"));
            commands.set(row + " #State.Background", ICONS + (entry.active() ? "Changed.png" : "Default.png"));
            commands.set(row + " #Unmark.Visible", !entry.builtIn());
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #Switch",
                    new EventData().append(PanelAction.KEY, PREVIEW).append(PanelAction.ID, entry.id()));
            if (!entry.builtIn()) {
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        row + " #Unmark",
                        new EventData().append(PanelAction.KEY, UNMARK).append(PanelAction.ID, entry.id()));
            }
        }
        Optional<String> open = previewing;
        previewing = Optional.empty();
        open.ifPresentOrElse(profile -> showPreview(profile, commands), () -> hidePreview(commands));
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case PREVIEW -> {
                UICommandBuilder update = new UICommandBuilder();
                showPreview(action.id, update);
                host.send(update);
            }
            case CLOSE_PREVIEW -> {
                UICommandBuilder update = new UICommandBuilder();
                previewing = Optional.empty();
                hidePreview(update);
                host.send(update);
            }
            case SWITCH -> switchProfile();
            case UNMARK -> {
                if (action.id == null) {
                    host.send(new UICommandBuilder());
                } else {
                    Result<String> result = view.unmark(action.id);
                    if (result.isSuccess()) {
                        previewing = previewing.filter(open -> !open.equals(action.id));
                        host.rebuild();
                    }
                    footer(result);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void switchProfile() {
        if (previewing.isEmpty()) {
            host.send(new UICommandBuilder());
            return;
        }
        Result<String> result = view.switchTo(previewing.get());
        if (result.isSuccess()) {
            previewing = Optional.empty();
            host.rebuild();
        }
        footer(result);
    }

    private void showPreview(String profile, UICommandBuilder update) {
        if (profile == null) {
            hidePreview(update);
            return;
        }
        Result<SnapshotsView.Preview> result = view.preview(profile);
        if (result.isFailure()) {
            update.set("#FooterStatus.Text", tr.error(result.error()));
            hidePreview(update);
            return;
        }
        SnapshotsView.Preview preview = result.value();
        previewing = Optional.of(profile);
        List<String> lines = preview.allLines(tr);
        update.set(
                "#ProfilePreviewTitle.Text",
                tr.t(
                        preview.changes() > 0
                                ? "controlcore.profiles.preview.header"
                                : preview.adminOnly() > 0
                                        ? "controlcore.snapshots.preview.only_admin"
                                        : "controlcore.profiles.preview.same",
                        "name",
                        preview.name(),
                        "count",
                        preview.changes()));
        update.set("#ProfilePreviewText.Text", String.join("\n", lines));
        update.set("#ProfilePreview.Visible", true);
        host.resize(LIST, LIST_HEIGHT - PREVIEW_HEIGHT, update);
    }

    private void hidePreview(UICommandBuilder update) {
        update.set("#ProfilePreview.Visible", false);
        host.resize(LIST, LIST_HEIGHT, update);
    }

    private void footer(Result<String> result) {
        UICommandBuilder update = new UICommandBuilder();
        update.set("#FooterStatus.Text", result.isSuccess() ? result.value() : tr.error(result.error()));
        host.showRestartNotice(update);
        host.send(update);
    }
}
