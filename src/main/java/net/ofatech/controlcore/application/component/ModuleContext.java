package net.ofatech.controlcore.application.component;

import net.ofatech.controlcore.api.ControlCoreEvents;
import net.ofatech.controlcore.domain.ports.command.CommandRegistrar;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;
import net.ofatech.controlcore.domain.ports.game.Scheduler;
import net.ofatech.controlcore.domain.ports.platform.Capabilities;
import net.ofatech.controlcore.domain.ports.system.Log;

/** What a {@link FeatureModule} receives in its lifecycle hooks. */
public record ModuleContext(
        SettingsProvider settings,
        Capabilities capabilities,
        CommandRegistrar commands,
        ControlCoreEvents events,
        Scheduler scheduler,
        Log log) {}
