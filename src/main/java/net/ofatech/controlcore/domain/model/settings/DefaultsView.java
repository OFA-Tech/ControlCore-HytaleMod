package net.ofatech.controlcore.domain.model.settings;

import java.util.function.Predicate;
import net.ofatech.controlcore.domain.result.Result;

public interface DefaultsView {

    enum Section {
        MOD_SETTINGS(true),
        PREFS(false),
        SERVER_CONFIGS(true),
        KEYMAPS_SERVER(true),
        KEYMAPS_MINE(false),
        PERMISSIONS(true);

        private final boolean serverWide;

        Section(boolean serverWide) {
            this.serverWide = serverWide;
        }

        public boolean serverWide() {
            return serverWide;
        }
    }

    int count(Section section, Predicate<String> shown);

    boolean savesFirst(Section section);

    Result<String> restore(Section section, Predicate<String> shown);
}
