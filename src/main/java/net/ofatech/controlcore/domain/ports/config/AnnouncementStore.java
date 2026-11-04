package net.ofatech.controlcore.domain.ports.config;

import net.ofatech.controlcore.domain.model.automation.Announcements;

public interface AnnouncementStore {

    Announcements load();

    boolean save(Announcements announcements);
}
