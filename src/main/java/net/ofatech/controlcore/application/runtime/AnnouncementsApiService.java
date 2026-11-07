package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.api.AnnouncementsApi;
import net.ofatech.controlcore.application.automation.AnnouncementService;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.automation.Announcements;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(AnnouncementsApi.class)
public final class AnnouncementsApiService implements AnnouncementsApi {

    private final AnnouncementService announcements;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public AnnouncementsApiService(AnnouncementService announcements, Texts texts, PlayerDirectory directory) {
        this(announcements, texts, directory, ApiActor.MOD);
    }

    private AnnouncementsApiService(
            AnnouncementService announcements, Texts texts, PlayerDirectory directory, ApiActor actor) {
        this.announcements = announcements;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public AnnouncementsApi actingFor(PlayerId player) {
        return new AnnouncementsApiService(announcements, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public List<Announcement> list() {
        if (!announcements.mayManage(actor.who())) {
            return List.of();
        }
        return announcements.state().items().stream()
                .map(item -> new Announcement(item.id(), item.text(), item.enabled()))
                .toList();
    }

    @Override
    public Result<String> add(String text) {
        return actor.run(() -> announcements.add(text, actor.who(), tr()));
    }

    @Override
    public Result<String> remove(String id) {
        return actor.run(() -> announcements.remove(id, actor.who(), tr()));
    }

    @Override
    public Result<String> setEnabled(String id, boolean enabled) {
        return actor.run(() -> announcements.setItemEnabled(id, enabled, actor.who(), tr()));
    }

    @Override
    public Result<String> move(String id, int by) {
        return actor.run(() -> announcements.move(id, by, actor.who(), tr()));
    }

    @Override
    public Settings settings() {
        Announcements now = announcements.state();
        return new Settings(
                now.enabled(),
                now.intervalMinutes(),
                now.order().id(),
                now.channel().id(),
                now.minPlayers());
    }

    @Override
    public Result<String> setSettings(Settings settings) {
        if (settings == null) {
            return actor.run(() -> announcements.setSettings(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    actor.who(),
                    tr()));
        }
        return actor.run(() -> announcements.setSettings(
                Optional.of(settings.enabled()),
                Optional.of(Integer.toString(settings.intervalMinutes())),
                Optional.ofNullable(settings.order()),
                Optional.ofNullable(settings.channel()),
                Optional.of(Integer.toString(settings.minPlayers())),
                actor.who(),
                tr()));
    }

    @Override
    public Result<String> sendNow() {
        return announcements.sendNow(Optional.empty(), actor.who(), tr());
    }

    @Override
    public Result<String> send(String id) {
        return announcements.sendNow(Optional.ofNullable(id), actor.who(), tr());
    }

    private Translator tr() {
        return texts.forPlayer(actor.who());
    }
}
