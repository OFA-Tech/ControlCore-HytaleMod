package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.controlpanel.OpenControlPanelCommand;
import net.ofatech.controlcore.domain.ports.platform.ModPlatform;
import net.ofatech.controlcore.domain.ports.system.Clock;
import org.junit.jupiter.api.Test;

class InjectorTest {

    private final ComponentIndex index = new ComponentIndex(InjectorTest.class.getClassLoader());
    private final Injector injector = new Injector(index);

    public interface Unprovided {}

    public static final class Leaf {
        public Leaf() {}
    }

    public static final class Branch {
        final Leaf leaf;

        public Branch(Leaf leaf) {
            this.leaf = leaf;
        }
    }

    public static final class NeedsUnprovided {
        public NeedsUnprovided(Unprovided missing) {}
    }

    public static final class CycleA {
        public CycleA(CycleB b) {}
    }

    public static final class CycleB {
        public CycleB(CycleA a) {}
    }

    public static final class TwoConstructors {
        public TwoConstructors() {}

        public TwoConstructors(Leaf leaf) {}
    }

    @Test
    void createsOurClassesOnceThroughTheirConstructor() {
        Branch branch = injector.get(Branch.class);

        assertThat(injector.get(Branch.class)).isSameAs(branch);
        assertThat(branch.leaf).isSameAs(injector.get(Leaf.class));
    }

    @Test
    void returnsBoundInstances() {
        Clock clock = java.time.Instant::now;
        injector.bind(Clock.class, clock);

        assertThat(injector.get(Clock.class)).isSameAs(clock);
        assertThatThrownBy(() -> injector.bind(Clock.class, clock)).isInstanceOf(InjectionException.class);
    }

    @Test
    void reportsTheDependencyPathOfAMissingBinding() {
        assertThatThrownBy(() -> injector.get(NeedsUnprovided.class))
                .isInstanceOf(InjectionException.class)
                .hasMessageContaining("Nothing provides")
                .hasMessageContaining("NeedsUnprovided -> Unprovided");
    }

    @Test
    void detectsCycles() {
        assertThatThrownBy(() -> injector.get(CycleA.class))
                .isInstanceOf(InjectionException.class)
                .hasMessageContaining("CycleA -> CycleB -> CycleA");
    }

    @Test
    void requiresExactlyOnePublicConstructor() {
        assertThatThrownBy(() -> injector.get(TwoConstructors.class))
                .isInstanceOf(InjectionException.class)
                .hasMessageContaining("exactly one public constructor");
    }

    @Test
    void refusesToCreateForeignClasses() {
        assertThatThrownBy(() -> injector.get(StringBuilder.class))
                .isInstanceOf(InjectionException.class)
                .hasMessageContaining("only classes of this mod");
    }

    @Test
    void indexListsDiscoveredComponents() {
        List<Class<? extends ModCommand>> commands = index.providers(ModCommand.class);

        assertThat(commands).contains(OpenControlPanelCommand.class);
    }

    @Test
    void interfacesWithSeveralProvidersAreAmbiguous() {
        assertThatThrownBy(() -> injector.get(ModPlatform.class))
                .isInstanceOf(InjectionException.class)
                .hasMessageContaining("Several classes provide");
    }
}
