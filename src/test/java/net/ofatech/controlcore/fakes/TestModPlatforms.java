package net.ofatech.controlcore.fakes;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.platform.CapabilityBinder;
import net.ofatech.controlcore.domain.ports.platform.ModPlatform;
import net.ofatech.controlcore.domain.result.Result;

public final class TestModPlatforms {

    public static final String WORKING = "Test:Greeter";
    public static final String BROKEN = "Test:Broken";

    public static final List<PlayerId> OPENED = new CopyOnWriteArrayList<>();

    public interface Greeter {
        Result<Void> greet(PlayerId player);
    }

    private TestModPlatforms() {}

    @AutoService(ModPlatform.class)
    public static final class Working implements ModPlatform {

        @Override
        public String modId() {
            return WORKING;
        }

        @Override
        public void bind(CapabilityBinder binder) {
            binder.provide(Greeter.class, () -> player -> {
                OPENED.add(player);
                return Result.success();
            });
        }
    }

    @AutoService(ModPlatform.class)
    public static final class Broken implements ModPlatform {

        @Override
        public String modId() {
            return BROKEN;
        }

        @Override
        public void bind(CapabilityBinder binder) {
            throw new NoClassDefFoundError("at/example/MissingApi");
        }
    }
}
