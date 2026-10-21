package net.ofatech.controlcore.application.runtime;

import net.ofatech.controlcore.fakes.FakePlatform;
import net.ofatech.controlcore.fakes.TestModPlatforms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class RuntimeTestBase {

    static final String COMMAND = "open-admin-config";

    final FakePlatform platform = new FakePlatform();
    final ModRuntime runtime = new ModRuntime(platform, RuntimeTestBase.class.getClassLoader());

    @BeforeEach
    void reset() {
        TestModPlatforms.OPENED.clear();
    }

    @AfterEach
    void shutDown() {
        runtime.shutdown();
    }
}
