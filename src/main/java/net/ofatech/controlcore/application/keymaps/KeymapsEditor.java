package net.ofatech.controlcore.application.keymaps;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Shows a player's key bindings as text preferences, so the control panel lists and edits them like settings:
 * one row per key action, the value is the bindings ({@code CTRL + F | MOUSE_MIDDLE}), the default the server's.
 */
public final class KeymapsEditor implements SettingsEditor {

    static final int MAX_LENGTH = 200;

    private final KeymapRegistry keymaps;

    public KeymapsEditor(KeymapRegistry keymaps) {
        this.keymaps = keymaps;
    }

    @Override
    public List<SettingSnapshot> snapshot(SettingScope scope, Optional<PlayerId> player) {
        if (scope != SettingScope.PLAYER || player.isEmpty()) {
            return List.of();
        }
        return keymaps.actions().stream()
                .sorted(Comparator.comparing((KeyAction action) -> action.modId())
                        .thenComparing(KeyAction::id))
                .map(action -> snapshot(action, player.get()))
                .toList();
    }

    @Override
    public Result<SettingSnapshot> read(SettingId id, Optional<PlayerId> player) {
        return player(player).flatMap(viewer -> action(id).map(action -> snapshot(action, viewer)));
    }

    @Override
    public Result<SettingSnapshot> write(SettingId id, Optional<PlayerId> player, String text) {
        return player(player).flatMap(viewer -> keymaps.bind(id, viewer, text).flatMap(binds -> read(id, player)));
    }

    @Override
    public Result<SettingSnapshot> reset(SettingId id, Optional<PlayerId> player) {
        return player(player).flatMap(viewer -> keymaps.resetBind(id, viewer).flatMap(binds -> read(id, player)));
    }

    @Override
    public Subscription onAnyChange(Consumer<? super SettingChanged> listener) {
        return keymaps.onChange(listener);
    }

    private SettingSnapshot snapshot(KeyAction action, PlayerId player) {
        KeyBinds server = keymaps.serverKeys(action.id());
        Optional<KeyBinds> own = keymaps.ownKeys(action.id(), player);
        KeyBinds current = own.orElse(server);
        String problems = KeymapsText.problems(current);
        String description = joined(action.description(), KeymapsText.runs(action) + ".", problems);
        SettingDefinition<String> definition = SettingDefinition.player(
                        action.id().toString(), SettingType.text(MAX_LENGTH), server.toString())
                .named(action.displayName())
                .describedAs(description)
                .inCategory(action.modId());
        return new SettingSnapshot(definition, current.toString(), own.isPresent());
    }

    private Result<KeyAction> action(SettingId id) {
        return keymaps.find(id)
                .map(Result::success)
                .orElseGet(() ->
                        Result.failure(Error.notFound("Keys.UnknownAction", "There is no key action '" + id + "'.")));
    }

    private static Result<PlayerId> player(Optional<PlayerId> player) {
        return player.map(Result::success)
                .orElseGet(
                        () -> Result.failure(Error.validation("Keys.WrongScope", "Key bindings belong to a player.")));
    }

    private static String joined(String... parts) {
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                text.append(text.isEmpty() ? "" : " ").append(part.strip());
            }
        }
        return text.toString();
    }
}
