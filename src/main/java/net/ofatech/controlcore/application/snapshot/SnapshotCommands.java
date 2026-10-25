package net.ofatech.controlcore.application.snapshot;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot.Part;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

final class SnapshotCommands {

    record Selection(Set<Part> parts, Set<String> mods) {}

    private SnapshotCommands() {}

    static Optional<PlayerId> who(CommandContext context) {
        return context.sender().player().map(PlayerInfo::id);
    }

    static Actor actor(CommandContext context) {
        return context.sender()
                .player()
                .map(player -> Actor.player(player.id(), player.name()))
                .orElseGet(() -> Actor.console(context.sender().name()));
    }

    static Result<Selection> parts(String text) {
        String value = text == null || text.isBlank() ? "all" : text.strip();
        Set<Part> parts = EnumSet.noneOf(Part.class);
        Set<String> mods = new LinkedHashSet<>();
        for (String piece : value.split("[,+\\s]+")) {
            String part = piece.strip();
            if (part.isEmpty()) {
                continue;
            }
            if (part.equalsIgnoreCase("all")) {
                parts.addAll(
                        EnumSet.of(Part.CONFIG, Part.WORLDS, Part.SETTINGS, Part.KEYS, Part.PERMISSIONS, Part.MODS));
                continue;
            }
            if (part.toLowerCase(java.util.Locale.ROOT).startsWith("settings:")) {
                parts.add(Part.SETTINGS);
                for (String mod : part.substring("settings:".length()).split("/")) {
                    if (!mod.isBlank()) {
                        mods.add(mod.strip());
                    }
                }
                continue;
            }
            Optional<Part> found = Part.of(part);
            if (found.isEmpty()) {
                return Result.failure(Error.validation(
                        "Snapshots.UnknownPart",
                        Text.key(
                                        "controlcore.errors.snapshots.unknown_part",
                                        "'{part}' is not a part. Use all, config, settings, settings:<mod>/<mod>,"
                                                + " keys, permissions, mods or players.")
                                .with("part", part)));
            }
            parts.add(found.get());
        }
        return Result.success(new Selection(parts, mods));
    }
}
