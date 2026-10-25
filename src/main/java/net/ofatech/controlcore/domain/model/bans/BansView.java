package net.ofatech.controlcore.domain.model.bans;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.result.Result;

public interface BansView {

    record Entry(String id, String name, String details, boolean active) {}

    record Length(String id, String label) {}

    List<Entry> list();

    List<Length> lengths();

    CompletableFuture<Result<String>> ban(String player, String length, String reason);

    Result<String> unban(String id);
}
