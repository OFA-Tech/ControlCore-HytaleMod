package net.ofatech.controlcore.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PanelRightsTest {

    @Test
    void losingAnySingleRightIsNoticedEvenForRightsAddedLater() throws ReflectiveOperationException {
        RecordComponent[] components = PanelRights.class.getRecordComponents();
        PanelRights all = rights(components, -1);

        for (int i = 0; i < components.length; i++) {
            assertThat(rights(components, i).keepsEverythingOf(all))
                    .as("losing " + components[i].getName())
                    .isFalse();
            assertThat(all.keepsEverythingOf(rights(components, i)))
                    .as("gaining " + components[i].getName())
                    .isTrue();
        }
    }

    @Test
    void theMasterSwitchOnlyCountsForSomeoneWhoHadTheAdminPanel() throws ReflectiveOperationException {
        RecordComponent[] components = PanelRights.class.getRecordComponents();
        Object[] player = new Object[components.length];
        Arrays.fill(player, false);
        player[0] = true;
        Object[] switchedOff = new Object[components.length];
        Arrays.fill(switchedOff, false);
        PanelRights before = make(player);

        assertThat(make(switchedOff).keepsEverythingOf(before)).isTrue();
    }

    private static PanelRights rights(RecordComponent[] components, int without) throws ReflectiveOperationException {
        Object[] values = new Object[components.length];
        Arrays.fill(values, true);
        if (without >= 0) {
            values[without] = false;
        }
        return make(values);
    }

    private static PanelRights make(Object[] values) throws ReflectiveOperationException {
        Class<?>[] types = new Class<?>[values.length];
        Arrays.fill(types, boolean.class);
        return PanelRights.class.getDeclaredConstructor(types).newInstance(values);
    }
}
