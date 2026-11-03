package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.List;
import net.ofatech.controlcore.api.ProfilesApi;
import net.ofatech.controlcore.api.SnapshotsApi;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.snapshot.ProfileService;
import net.ofatech.controlcore.application.snapshot.SnapshotService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ProfilesApi.class)
public final class ProfilesApiService implements ProfilesApi {

    private final ProfileService profiles;
    private final SnapshotService snapshots;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public ProfilesApiService(
            ProfileService profiles, SnapshotService snapshots, Texts texts, PlayerDirectory directory) {
        this(profiles, snapshots, texts, directory, ApiActor.MOD);
    }

    private ProfilesApiService(
            ProfileService profiles,
            SnapshotService snapshots,
            Texts texts,
            PlayerDirectory directory,
            ApiActor actor) {
        this.profiles = profiles;
        this.snapshots = snapshots;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public ProfilesApi actingFor(PlayerId player) {
        return new ProfilesApiService(profiles, snapshots, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public List<Profile> list() {
        if (!snapshots.mayUse(actor.who())) {
            return List.of();
        }
        return profiles.list(tr()).stream()
                .map(entry -> new Profile(entry.id(), entry.name(), entry.details(), entry.builtIn(), entry.active()))
                .toList();
    }

    @Override
    public String status() {
        return profiles.status(actor.who(), tr());
    }

    @Override
    public Result<SnapshotsApi.Preview> preview(String id) {
        return profiles.preview(id, actor.who(), tr()).map(SnapshotsApiService::preview);
    }

    @Override
    public Result<String> switchTo(String id) {
        return actor.run(() -> profiles.switchTo(id, actor.who(), actor.actor(), tr()));
    }

    @Override
    public Result<String> mark(String name) {
        return profiles.mark(name, actor.who(), tr());
    }

    @Override
    public Result<String> unmark(String id) {
        return profiles.unmark(id, actor.who(), tr());
    }

    private Translator tr() {
        return texts.forPlayer(actor.who());
    }
}
