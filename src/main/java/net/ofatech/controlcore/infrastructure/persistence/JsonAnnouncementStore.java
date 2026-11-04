package net.ofatech.controlcore.infrastructure.persistence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.ofatech.controlcore.domain.model.automation.Announcements;
import net.ofatech.controlcore.domain.ports.config.AnnouncementStore;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class JsonAnnouncementStore implements AnnouncementStore {

    private final JsonFile file;

    public JsonAnnouncementStore(Path file, Log log) {
        this.file = new JsonFile(file, log, "announcements");
    }

    @Override
    public synchronized Announcements load() {
        return file.<FileForm>read(FileForm.class)
                .map(FileForm::toAnnouncements)
                .orElse(Announcements.DEFAULT);
    }

    @Override
    public synchronized boolean save(Announcements announcements) {
        return file.write(FileForm.of(announcements), FileForm.class);
    }

    static final class FileForm {
        private boolean enabled;
        private int intervalMinutes = Announcements.DEFAULT.intervalMinutes();
        private String order;
        private String channel;
        private int minPlayers;
        private List<FileItem> items;

        static FileForm of(Announcements announcements) {
            FileForm form = new FileForm();
            form.enabled = announcements.enabled();
            form.intervalMinutes = announcements.intervalMinutes();
            form.order = announcements.order().id();
            form.channel = announcements.channel().id();
            form.minPlayers = announcements.minPlayers();
            form.items = new ArrayList<>();
            for (Announcements.Item item : announcements.items()) {
                FileItem stored = new FileItem();
                stored.id = item.id();
                stored.text = item.text();
                stored.enabled = item.enabled();
                form.items.add(stored);
            }
            return form;
        }

        Announcements toAnnouncements() {
            List<Announcements.Item> read = new ArrayList<>();
            if (items != null) {
                items.stream()
                        .filter(Objects::nonNull)
                        .filter(item -> item.id != null && !item.id.isBlank())
                        .forEach(item -> read.add(new Announcements.Item(item.id, item.text, item.enabled)));
            }
            return new Announcements(
                    enabled,
                    intervalMinutes,
                    Announcements.Order.of(order).orElse(Announcements.Order.IN_TURN),
                    Announcements.Channel.of(channel).orElse(Announcements.Channel.CHAT),
                    minPlayers,
                    read);
        }
    }

    static final class FileItem {
        private String id;
        private String text;
        private boolean enabled;
    }
}
