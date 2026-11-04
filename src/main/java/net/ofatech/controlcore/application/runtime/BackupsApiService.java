package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.api.BackupsApi;
import net.ofatech.controlcore.application.backups.BackupService;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.server.BackupFile;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(BackupsApi.class)
public final class BackupsApiService implements BackupsApi {

    private final BackupService backups;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public BackupsApiService(BackupService backups, Texts texts, PlayerDirectory directory) {
        this(backups, texts, directory, ApiActor.MOD);
    }

    private BackupsApiService(BackupService backups, Texts texts, PlayerDirectory directory, ApiActor actor) {
        this.backups = backups;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public BackupsApi actingFor(PlayerId player) {
        return new BackupsApiService(backups, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public boolean configured() {
        return backups.configured();
    }

    @Override
    public Result<String> setDirectory(String folder) {
        return actor.run(() -> backups.setDirectory(folder, actor.who(), tr()));
    }

    @Override
    public CompletableFuture<Result<Backup>> create() {
        return actor.run(() -> backups.create(actor.who(), tr()))
                .thenApply(result -> result.map(BackupsApiService::of));
    }

    @Override
    public List<Backup> list() {
        return backups.mayManage(actor.who())
                ? backups.list().stream().map(BackupsApiService::of).toList()
                : List.of();
    }

    @Override
    public Result<String> delete(String name) {
        return actor.run(() -> backups.delete(name, actor.who(), tr()));
    }

    @Override
    public Result<String> restoreOnNextStart(String name) {
        return actor.run(() -> backups.restoreOnNextStart(name, actor.who(), tr()));
    }

    @Override
    public Result<String> cancelRestore() {
        return actor.run(() -> backups.cancelRestore(actor.who(), tr()));
    }

    @Override
    public Optional<PendingRestore> pendingRestore() {
        return backups.mayManage(actor.who())
                ? backups.pending().map(restore -> new PendingRestore(restore.name(), restore.by(), restore.at()))
                : Optional.empty();
    }

    private static Backup of(BackupFile file) {
        return new Backup(file.name(), file.at(), file.size(), file.archived());
    }

    private Translator tr() {
        return texts.forPlayer(actor.who());
    }
}
