package net.ofatech.controlcore.domain.model.automation;

import java.util.List;
import net.ofatech.controlcore.domain.result.Result;

public interface AnnouncementsView {

    record Choice(String id, String label) {}

    Announcements state();

    List<Choice> orders();

    List<Choice> channels();

    Result<String> setEnabled(boolean on);

    Result<String> saveSettings(String interval, String order, String channel, String minPlayers);

    Result<String> add(String text);

    Result<String> remove(String id);

    Result<String> toggle(String id);

    Result<String> move(String id, int by);

    Result<String> send(String id);
}
