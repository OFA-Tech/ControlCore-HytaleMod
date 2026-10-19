package net.ofatech.controlcore;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.application.runtime.ModRuntime;
import net.ofatech.controlcore.infrastructure.platforms.hytale.HytalePlatform;

/** Plugin entry point loaded by Hytale ({@code Main} in manifest.json). Only delegates. */
public final class Main extends JavaPlugin {

    private final ModRuntime runtime;

    @SuppressWarnings("this-escape")
    public Main(@Nonnull JavaPluginInit init) {
        super(init);
        runtime = new ModRuntime(new HytalePlatform(this), Main.class.getClassLoader());
    }

    @Override
    protected void setup() {
        runtime.setup();
    }

    @Override
    protected void start() {
        runtime.start();
    }

    @Override
    protected void shutdown() {
        runtime.shutdown();
    }
}
