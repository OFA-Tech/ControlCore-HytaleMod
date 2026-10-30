package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import net.ofatech.controlcore.domain.model.PanelRights;
import org.junit.jupiter.api.Test;

class PanelKeyDetectRightsBanca6Test {

    private static final int FIELDS = PanelRights.class.getRecordComponents().length;

    @Test
    void losingAnySingleRightOfAFullAdminIsALoss() {
        PanelRights all = rights(allTrue());
        for (int i = 0; i < FIELDS; i++) {
            boolean[] values = allTrue();
            values[i] = false;
            assertThat(PanelKeyDetect.nothingLost(all, rights(values)))
                    .as("lost " + name(i))
                    .isFalse();
        }
    }

    @Test
    void gainingAnySingleRightIsNeverALoss() {
        PanelRights none = rights(new boolean[FIELDS]);
        for (int i = 0; i < FIELDS; i++) {
            boolean[] values = new boolean[FIELDS];
            values[i] = true;
            assertThat(PanelKeyDetect.nothingLost(none, rights(values)))
                    .as("gained " + name(i))
                    .isTrue();
        }
    }

    @Test
    void losingAnySingleRightWhileGainingAnotherStillCountsAsALoss() {
        for (int lost = 0; lost < FIELDS; lost++) {
            for (int gained = 0; gained < FIELDS; gained++) {
                if (lost == gained || index("panelEnabled") == lost) {
                    continue;
                }
                boolean[] before = new boolean[FIELDS];
                before[lost] = true;
                boolean[] after = new boolean[FIELDS];
                after[gained] = true;
                assertThat(PanelKeyDetect.nothingLost(rights(before), rights(after)))
                        .as("lost " + name(lost) + ", gained " + name(gained))
                        .isFalse();
            }
        }
    }

    @Test
    void panelTurnedOffOnlyCountsWhenThereWasAnAdminPanel() {
        int enabled = index("panelEnabled");
        int adminPanel = index("adminPanel");

        boolean[] player = new boolean[FIELDS];
        player[enabled] = true;
        assertThat(PanelKeyDetect.nothingLost(rights(player), rights(new boolean[FIELDS])))
                .as("plain player, admin panel switched off")
                .isTrue();

        boolean[] staff = new boolean[FIELDS];
        staff[enabled] = true;
        staff[adminPanel] = true;
        boolean[] staffOff = staff.clone();
        staffOff[enabled] = false;
        assertThat(PanelKeyDetect.nothingLost(rights(staff), rights(staffOff)))
                .as("staff with admin panel, panel switched off")
                .isFalse();

        boolean[] staffAlreadyOff = new boolean[FIELDS];
        staffAlreadyOff[adminPanel] = true;
        assertThat(PanelKeyDetect.nothingLost(rights(staffAlreadyOff), rights(staffAlreadyOff)))
                .as("panel was already off when detect started")
                .isTrue();
    }

    @Test
    void identicalRightsAreNeverALoss() {
        for (int mask = 0; mask < (1 << FIELDS); mask += 37) {
            boolean[] values = new boolean[FIELDS];
            for (int i = 0; i < FIELDS; i++) {
                values[i] = (mask & (1 << i)) != 0;
            }
            assertThat(PanelKeyDetect.nothingLost(rights(values), rights(values)))
                    .as(Arrays.toString(values))
                    .isTrue();
        }
    }

    private static boolean[] allTrue() {
        boolean[] values = new boolean[FIELDS];
        Arrays.fill(values, true);
        return values;
    }

    private static String name(int index) {
        return PanelRights.class.getRecordComponents()[index].getName();
    }

    private static int index(String name) {
        RecordComponent[] components = PanelRights.class.getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i].getName().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static PanelRights rights(boolean[] values) {
        try {
            Class<?>[] types = new Class<?>[FIELDS];
            Arrays.fill(types, boolean.class);
            Object[] args = new Object[FIELDS];
            for (int i = 0; i < FIELDS; i++) {
                args[i] = values[i];
            }
            return PanelRights.class.getDeclaredConstructor(types).newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
