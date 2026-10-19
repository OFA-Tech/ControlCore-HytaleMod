package net.ofatech.controlcore.infrastructure.platforms.hytale.command;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import net.ofatech.controlcore.domain.ports.command.CommandHandler;
import net.ofatech.controlcore.domain.ports.command.CommandRegistrar;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class HytaleCommandRegistrar implements CommandRegistrar {

    private final CommandRegistry registry;
    private final String basePermission;
    private final Log log;

    public HytaleCommandRegistrar(CommandRegistry registry, String basePermission, Log log) {
        this.registry = registry;
        this.basePermission = basePermission;
        this.log = log;
    }

    @Override
    public void register(CommandSpec spec, CommandHandler handler) {
        registry.registerCommand(new SpecCommand(spec, handler, basePermission, log));
    }
}
