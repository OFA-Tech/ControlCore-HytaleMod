package net.ofatech.controlcore.fakes;

import net.ofatech.controlcore.domain.model.automation.Announcements;
import net.ofatech.controlcore.domain.ports.config.AnnouncementStore;

public final class FakeAnnouncementStore implements AnnouncementStore {

    public Announcements state = Announcements.DEFAULT;
    public int saves;

    @Override
    public Announcements load() {
        return state;
    }

    @Override
    public boolean save(Announcements changed) {
        state = changed;
        saves++;
        return true;
    }
}
