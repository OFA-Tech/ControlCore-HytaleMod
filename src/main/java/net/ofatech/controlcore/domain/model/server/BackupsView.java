package net.ofatech.controlcore.domain.model.server;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.result.Result;

public interface BackupsView {

    record Row(String name, String info, boolean pending) {}

    boolean configured();

    String directory();

    String status();

    String settings();

    Optional<String> pending();

    boolean running();

    List<Row> backups();

    Result<String> setDirectory(String directory);

    CompletableFuture<Result<String>> backUpNow();

    Result<String> delete(String name);

    Result<String> restoreOnNextStart(String name);

    Result<String> cancelRestore();
}
