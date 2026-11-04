package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.domain.model.settings.SettingId;

final class ServerScope {

    private static final String SERVER_MODE_CONFIG = "ServerMode:Config";
    private static final String SERVER_MODE_WORLDS = "ServerMode:Worlds";
    private static final String SERVER_MODE_PLAYERS = "ServerMode:Players";
    private static final String SERVER_MODE_BACKUPS = "ServerMode:Backups";
    private static final String SERVER_WORLD = "ServerWorld";
    private static final String LAYOUT = ControlPanelPage.LAYOUT;

    interface Host {
        PlayerRef viewer();

        void send(UICommandBuilder update);

        void applySearch(PanelSection key, SettingsSection section, UICommandBuilder update, boolean report);

        void showLegend(UICommandBuilder update);

        void refresh(
                Ref<EntityStore> ref,
                Store<EntityStore> store,
                PanelSection key,
                SettingsSection section,
                SettingId id);
    }

    private final Map<PanelSection, SettingsSection> sections;
    private final List<String> worlds;
    private final boolean hasPlayers;
    private final boolean hasBackups;
    private final Host host;
    private String world;
    private boolean showingWorlds;
    private boolean showingPlayers;
    private boolean showingBackups;

    ServerScope(
            Map<PanelSection, SettingsSection> sections,
            List<String> worlds,
            boolean hasPlayers,
            boolean hasBackups,
            Host host) {
        this.sections = sections;
        this.worlds = worlds;
        this.hasPlayers = hasPlayers;
        this.hasBackups = hasBackups;
        this.host = host;
        this.world = worlds.contains("default")
                ? "default"
                : worlds.stream().findFirst().orElse("");
    }

    boolean showingPlayers() {
        return showingPlayers;
    }

    boolean showingRows() {
        return !showingPlayers && !showingBackups;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        commands.set(
                "#ServerModeButtons.Visible",
                sections.containsKey(PanelSection.SERVER_CONFIG) && (!worlds.isEmpty() || hasPlayers || hasBackups));
        commands.set("#ServerModeWorlds.Visible", !worlds.isEmpty());
        commands.set("#ServerModePlayers.Visible", hasPlayers);
        commands.set("#ServerModeBackups.Visible", hasBackups);
        commands.set(
                "#ServerWorld.Entries",
                worlds.stream()
                        .map(name -> new DropdownEntryInfo(LocalizableString.fromString(name), name))
                        .toList());
        commands.set("#ServerWorld.Value", world);
        if (!worlds.isEmpty() || hasPlayers || hasBackups) {
            commands.setObject("#ServerList.Anchor", ControlPanelPage.height(540));
        }
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ServerModePlayers",
                EventData.of(PanelAction.KEY, SERVER_MODE_PLAYERS));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ServerModeBackups",
                EventData.of(PanelAction.KEY, SERVER_MODE_BACKUPS));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ServerModeConfig",
                EventData.of(PanelAction.KEY, SERVER_MODE_CONFIG));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ServerModeWorlds",
                EventData.of(PanelAction.KEY, SERVER_MODE_WORLDS));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#ServerWorld",
                new EventData().append(PanelAction.KEY, SERVER_WORLD).append(PanelAction.VALUE, "#ServerWorld.Value"));
        show(commands);
    }

    private void show(UICommandBuilder commands) {
        boolean config = !showingWorlds && !showingPlayers && !showingBackups;
        commands.set("#ServerModeConfig.Style", Value.ref(LAYOUT, config ? "SegmentOnStyle" : "SegmentStyle"));
        commands.set("#ServerModeWorlds.Style", Value.ref(LAYOUT, showingWorlds ? "SegmentOnStyle" : "SegmentStyle"));
        commands.set("#ServerModePlayers.Style", Value.ref(LAYOUT, showingPlayers ? "SegmentOnStyle" : "SegmentStyle"));
        commands.set("#ServerModeBackups.Style", Value.ref(LAYOUT, showingBackups ? "SegmentOnStyle" : "SegmentStyle"));
        commands.set("#ServerWorld.Visible", showingWorlds);
        commands.set("#ServerIntro.Visible", config);
        commands.set("#WorldsIntro.Visible", showingWorlds);
        commands.set("#ServerRows.Visible", showingRows());
        commands.set("#BackupsPanel.Visible", showingBackups);
        commands.set("#PlayersPanel.Visible", showingPlayers);
    }

    boolean handle(PanelAction action) {
        String name = action.name();
        if (SERVER_MODE_CONFIG.equals(name)
                || SERVER_MODE_WORLDS.equals(name)
                || SERVER_MODE_PLAYERS.equals(name)
                || SERVER_MODE_BACKUPS.equals(name)) {
            showingWorlds = SERVER_MODE_WORLDS.equals(name) && !worlds.isEmpty();
            showingPlayers = SERVER_MODE_PLAYERS.equals(name) && hasPlayers;
            showingBackups = SERVER_MODE_BACKUPS.equals(name) && hasBackups;
            refresh();
        } else if (SERVER_WORLD.equals(name)) {
            if (action.value != null && worlds.contains(action.value)) {
                world = action.value;
            }
            refresh();
        } else {
            return false;
        }
        return true;
    }

    private void refresh() {
        UICommandBuilder update = new UICommandBuilder();
        show(update);
        SettingsSection section = sections.get(PanelSection.SERVER_CONFIG);
        if (section != null) {
            host.applySearch(PanelSection.SERVER_CONFIG, section, update, true);
        }
        host.showLegend(update);
        host.send(update);
    }

    boolean inScope(PanelSection key, SettingId id) {
        if (key != PanelSection.SERVER_CONFIG) {
            return true;
        }
        if (!showingWorlds) {
            return !ServerConfigEditor.isWorld(id);
        }
        return ServerConfigEditor.isWorld(id) && id.key().startsWith(ServerConfigEditor.worldPrefix(world));
    }

    boolean headingInScope(PanelSection key, List<SettingsSection.Entry> entries, int heading) {
        for (int index = heading + 1; index < entries.size(); index++) {
            if (entries.get(index) instanceof SettingsSection.Row row) {
                return inScope(key, row.id());
            }
            return false;
        }
        return false;
    }

    void worldChanged(SettingId changed) {
        if (!ServerConfigEditor.isWorld(changed)) {
            return;
        }
        String path = changed.key();
        int dot = path.indexOf('.');
        SettingsSection section = sections.get(PanelSection.SERVER_CONFIG);
        Ref<EntityStore> ref = host.viewer().getReference();
        if (dot < 0 || section == null || ref == null || !ref.isValid()) {
            return;
        }
        SettingId status = new SettingId(ServerConfigEditor.WORLD_NAMESPACE, path.substring(0, dot + 1) + "status");
        Store<EntityStore> store = ref.getStore();
        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
                .execute(() -> store.getExternalData()
                        .getWorld()
                        .execute(() -> host.refresh(ref, store, PanelSection.SERVER_CONFIG, section, status)));
    }
}
