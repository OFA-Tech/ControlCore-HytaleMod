package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.ofatech.controlcore.api.SnapshotsApi;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.snapshot.SnapshotService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(SnapshotsApi.class)
public final class SnapshotsApiService implements SnapshotsApi {

    private final SnapshotService snapshots;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public SnapshotsApiService(SnapshotService snapshots, Texts texts, PlayerDirectory directory) {
        this(snapshots, texts, directory, ApiActor.MOD);
    }

    private SnapshotsApiService(SnapshotService snapshots, Texts texts, PlayerDirectory directory, ApiActor actor) {
        this.snapshots = snapshots;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public SnapshotsApi actingFor(PlayerId player) {
        return new SnapshotsApiService(snapshots, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public List<Snapshot> list() {
        if (!snapshots.mayUse(actor.who())) {
            return List.of();
        }
        return snapshots.all().stream()
                .map(snapshot -> new Snapshot(
                        snapshot.name(),
                        snapshot.createdAt(),
                        snapshot.createdBy(),
                        snapshot.parts().stream()
                                .map(part -> Part.valueOf(part.name()))
                                .collect(Collectors.toUnmodifiableSet()),
                        snapshot.mods(),
                        snapshot.profile(),
                        snapshots.locked(snapshot, actor.who())))
                .toList();
    }

    @Override
    public Result<String> save(String name, Set<SnapshotsApi.Part> parts, Optional<String> mod) {
        Set<net.ofatech.controlcore.domain.model.snapshot.Snapshot.Part> wanted =
                EnumSet.noneOf(net.ofatech.controlcore.domain.model.snapshot.Snapshot.Part.class);
        if (parts != null) {
            parts.forEach(part ->
                    wanted.add(net.ofatech.controlcore.domain.model.snapshot.Snapshot.Part.valueOf(part.name())));
        }
        boolean replacing = snapshots.exists(name);
        return actor.run(() ->
                        snapshots.save(name, wanted, mod.map(Set::of).orElse(Set.of()), actor.who(), actor.actor()))
                .map(saved -> snapshots.savedText(saved, replacing, tr()));
    }

    @Override
    public Result<Preview> preview(String name) {
        return snapshots.plan(name, actor.who(), tr()).map(plan -> preview(snapshots.preview(plan, tr())));
    }

    @Override
    public Result<String> restore(String name) {
        return actor.run(() -> snapshots.restore(name, false, actor.who(), actor.actor(), tr()));
    }

    @Override
    public Result<String> restoreSafe(String name) {
        return actor.run(() -> snapshots.restore(name, true, actor.who(), actor.actor(), tr()));
    }

    @Override
    public Result<String> delete(String name) {
        return snapshots
                .delete(name, actor.who())
                .map(deleted -> tr().t("controlcore.snapshots.deleted", "name", deleted));
    }

    @Override
    public Result<String> deleteAll() {
        return snapshots.deleteAll(actor.who()).map(count -> snapshots.deletedText(count, actor.who(), tr()));
    }

    static Preview preview(SnapshotsView.Preview preview) {
        return new Preview(preview.name(), preview.lines(), preview.changes(), preview.skipped(), preview.adminOnly());
    }

    private Translator tr() {
        return texts.forPlayer(actor.who());
    }
}
