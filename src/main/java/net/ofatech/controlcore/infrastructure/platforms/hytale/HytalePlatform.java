package net.ofatech.controlcore.infrastructure.platforms.hytale;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.command.CommandRegistrar;
import net.ofatech.controlcore.domain.ports.config.ActiveProfileStore;
import net.ofatech.controlcore.domain.ports.config.AutomationRuleStore;
import net.ofatech.controlcore.domain.ports.config.ChangeLogStore;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;
import net.ofatech.controlcore.domain.ports.config.KeyBindStore;
import net.ofatech.controlcore.domain.ports.config.KeyGameActionStore;
import net.ofatech.controlcore.domain.ports.config.ReportWriter;
import net.ofatech.controlcore.domain.ports.config.SettingPermissionStore;
import net.ofatech.controlcore.domain.ports.config.SettingStore;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;
import net.ofatech.controlcore.domain.ports.config.SnapshotStore;
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
import net.ofatech.controlcore.domain.ports.game.AccessLists;
import net.ofatech.controlcore.domain.ports.game.Bans;
import net.ofatech.controlcore.domain.ports.game.ChatGate;
import net.ofatech.controlcore.domain.ports.game.CommandRunner;
import net.ofatech.controlcore.domain.ports.game.EmptyHandInput;
import net.ofatech.controlcore.domain.ports.game.HudDisplay;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.domain.ports.game.OverlayDisplay;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;
import net.ofatech.controlcore.domain.ports.game.PlayerContext;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;
import net.ofatech.controlcore.domain.ports.game.PlayerTuning;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.game.Scheduler;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.ports.game.Worlds;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.ports.mods.ModLookup;
import net.ofatech.controlcore.domain.ports.platform.Platform;
import net.ofatech.controlcore.domain.ports.platform.PortBinder;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.ports.system.RecentProblems;
import net.ofatech.controlcore.domain.ports.system.ServerHealth;
import net.ofatech.controlcore.domain.ports.system.Translations;
import net.ofatech.controlcore.infrastructure.i18n.BundledTranslations;
import net.ofatech.controlcore.infrastructure.persistence.FileReportWriter;
import net.ofatech.controlcore.infrastructure.persistence.JsonActiveProfileStore;
import net.ofatech.controlcore.infrastructure.persistence.JsonAutomationRuleStore;
import net.ofatech.controlcore.infrastructure.persistence.JsonCustomKeyBindStore;
import net.ofatech.controlcore.infrastructure.persistence.JsonKeyBindStore;
import net.ofatech.controlcore.infrastructure.persistence.JsonKeyGameActionStore;
import net.ofatech.controlcore.infrastructure.persistence.JsonLinesChangeLog;
import net.ofatech.controlcore.infrastructure.persistence.JsonPlayerDataStore;
import net.ofatech.controlcore.infrastructure.persistence.JsonSettingPermissionStore;
import net.ofatech.controlcore.infrastructure.persistence.JsonSettingStore;
import net.ofatech.controlcore.infrastructure.persistence.JsonSnapshotStore;
import net.ofatech.controlcore.infrastructure.platforms.hytale.command.HytaleCommandRegistrar;
import net.ofatech.controlcore.infrastructure.platforms.hytale.config.HytaleSettingsProvider;
import net.ofatech.controlcore.infrastructure.platforms.hytale.event.HytaleGameEvents;
import net.ofatech.controlcore.infrastructure.platforms.hytale.i18n.HytaleTranslations;
import net.ofatech.controlcore.infrastructure.platforms.hytale.input.HytaleCommandRunner;
import net.ofatech.controlcore.infrastructure.platforms.hytale.input.HytaleEmptyHandInput;
import net.ofatech.controlcore.infrastructure.platforms.hytale.input.HytaleInputProbe;
import net.ofatech.controlcore.infrastructure.platforms.hytale.input.HytalePlayerInput;
import net.ofatech.controlcore.infrastructure.platforms.hytale.log.HytaleLog;
import net.ofatech.controlcore.infrastructure.platforms.hytale.mods.HytaleModCatalog;
import net.ofatech.controlcore.infrastructure.platforms.hytale.mods.HytaleModLookup;
import net.ofatech.controlcore.infrastructure.platforms.hytale.player.HytaleAccessLists;
import net.ofatech.controlcore.infrastructure.platforms.hytale.player.HytaleHudDisplay;
import net.ofatech.controlcore.infrastructure.platforms.hytale.player.HytaleMessenger;
import net.ofatech.controlcore.infrastructure.platforms.hytale.player.HytaleOverlayDisplay;
import net.ofatech.controlcore.infrastructure.platforms.hytale.player.HytalePermissions;
import net.ofatech.controlcore.infrastructure.platforms.hytale.player.HytalePlayerAdmin;
import net.ofatech.controlcore.infrastructure.platforms.hytale.player.HytalePlayerContext;
import net.ofatech.controlcore.infrastructure.platforms.hytale.player.HytalePlayerTuning;
import net.ofatech.controlcore.infrastructure.platforms.hytale.player.HytalePlayers;
import net.ofatech.controlcore.infrastructure.platforms.hytale.server.HytaleBans;
import net.ofatech.controlcore.infrastructure.platforms.hytale.server.HytaleServerConfiguration;
import net.ofatech.controlcore.infrastructure.platforms.hytale.server.HytaleServerHealth;
import net.ofatech.controlcore.infrastructure.platforms.hytale.server.HytaleWorlds;
import net.ofatech.controlcore.infrastructure.platforms.hytale.ui.HytaleControlPanelView;
import net.ofatech.controlcore.infrastructure.platforms.hytale.world.HytaleScheduler;
import net.ofatech.controlcore.infrastructure.system.SystemClock;

public final class HytalePlatform implements Platform {

    private final JavaPlugin plugin;

    public HytalePlatform(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "Hytale";
    }

    @Override
    public void bind(PortBinder binder) {
        HytaleLog log = new HytaleLog(plugin.getLogger());
        HytalePlayers players = new HytalePlayers();
        binder.bind(Log.class, log);
        binder.bind(RecentProblems.class, log);
        binder.bind(ServerHealth.class, new HytaleServerHealth());
        binder.bind(
                ReportWriter.class,
                new FileReportWriter(
                        plugin.getDataDirectory().resolve("diagnostics"),
                        "mods/" + plugin.getDataDirectory().getFileName() + "/diagnostics",
                        log));
        binder.bind(Clock.class, new SystemClock());
        binder.bind(SettingsProvider.class, new HytaleSettingsProvider(plugin.getDataDirectory(), log));
        binder.bind(Players.class, players);
        binder.bind(Messenger.class, new HytaleMessenger());
        binder.bind(Permissions.class, new HytalePermissions(plugin.getBasePermission()));
        binder.bind(Translations.class, new HytaleTranslations(new BundledTranslations()));
        binder.bind(Scheduler.class, new HytaleScheduler(plugin.getTaskRegistry(), log));
        binder.bind(
                PlayerData.class,
                new JsonPlayerDataStore(plugin.getDataDirectory().resolve("players.json"), log));
        binder.bind(
                SettingStore.class,
                new JsonSettingStore(plugin.getDataDirectory().resolve("SettingValues.json"), log));
        binder.bind(
                CommandRegistrar.class,
                new HytaleCommandRegistrar(plugin.getCommandRegistry(), plugin.getBasePermission(), log));
        binder.bind(ModLookup.class, new HytaleModLookup());
        HytaleServerConfiguration serverConfiguration = new HytaleServerConfiguration(log);
        binder.bind(ServerConfiguration.class, serverConfiguration);
        binder.bind(
                Worlds.class,
                new HytaleWorlds(
                        new JsonSettingStore(plugin.getDataDirectory().resolve("WorldSettings.json"), log), log));
        binder.bind(PlayerTuning.class, new HytalePlayerTuning(log));
        binder.bind(PlayerAdmin.class, new HytalePlayerAdmin(log));
        binder.bind(AccessLists.class, new HytaleAccessLists());
        binder.bind(
                ChatGate.class,
                new net.ofatech.controlcore.infrastructure.platforms.hytale.event.HytaleChatGate(
                        plugin.getEventRegistry(), log));
        binder.bind(Bans.class, new HytaleBans(log));
        binder.bind(ModCatalog.class, new HytaleModCatalog(serverConfiguration, log));
        binder.bind(ControlPanelView.class, new HytaleControlPanelView(log));
        binder.bind(HudDisplay.class, new HytaleHudDisplay(log));
        binder.bind(OverlayDisplay.class, new HytaleOverlayDisplay(log));
        binder.bind(PlayerContext.class, new HytalePlayerContext());
        binder.bind(InputProbe.class, new HytaleInputProbe(log));
        binder.bind(PlayerInput.class, new HytalePlayerInput(log));
        binder.bind(
                EmptyHandInput.class,
                new HytaleEmptyHandInput(plugin.getIdentifier().toString(), log));
        binder.bind(CommandRunner.class, new HytaleCommandRunner(log));
        binder.bind(
                KeyBindStore.class,
                new JsonKeyBindStore(plugin.getDataDirectory().resolve("KeyBinds.json"), log));
        binder.bind(
                CustomKeyBindStore.class,
                new JsonCustomKeyBindStore(plugin.getDataDirectory().resolve("CustomKeyBinds.json"), log));
        binder.bind(
                KeyGameActionStore.class,
                new JsonKeyGameActionStore(plugin.getDataDirectory().resolve("KeyGameActions.json"), log));
        binder.bind(
                AutomationRuleStore.class,
                new JsonAutomationRuleStore(plugin.getDataDirectory().resolve("AutomationRules.json"), log));
        binder.bind(
                ActiveProfileStore.class,
                new JsonActiveProfileStore(plugin.getDataDirectory().resolve("ActiveProfile.json"), log));
        binder.bind(
                SnapshotStore.class,
                new JsonSnapshotStore(plugin.getDataDirectory().resolve("snapshots"), log));
        binder.bind(
                ChangeLogStore.class,
                new JsonLinesChangeLog(plugin.getDataDirectory().resolve("history"), log));
        binder.bind(
                SettingPermissionStore.class,
                new JsonSettingPermissionStore(plugin.getDataDirectory().resolve("SettingPermissions.json"), log));
    }

    @Override
    public void connect(EventPublisher publisher) {
        new HytaleGameEvents(plugin.getEventRegistry(), plugin.getEntityStoreRegistry(), publisher).register();
    }
}
