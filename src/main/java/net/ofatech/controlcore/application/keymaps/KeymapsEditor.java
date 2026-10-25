package net.ofatech.controlcore.application.keymaps;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.application.settings.ModNames;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBindEditor;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class KeymapsEditor implements SettingsEditor, CustomKeyBindEditor {

    static final int MAX_LENGTH = 200;

    private final KeymapRegistry keymaps;
    private final Texts texts;
    private final Translator tr;
    private final ModNames modNames;

    public KeymapsEditor(KeymapRegistry keymaps, Texts texts, ModNames modNames) {
        this(keymaps, texts, texts.forServer(), modNames);
    }

    private KeymapsEditor(KeymapRegistry keymaps, Texts texts, Translator tr, ModNames modNames) {
        this.keymaps = keymaps;
        this.texts = texts;
        this.tr = tr;
        this.modNames = modNames;
    }

    public KeymapsEditor viewedBy(PlayerId viewer) {
        return new KeymapsEditor(keymaps, texts, texts.forPlayer(Optional.of(viewer)), modNames);
    }

    @Override
    public List<SettingSnapshot> snapshot(SettingScope scope, Optional<PlayerId> player) {
        if (scope == SettingScope.SERVER) {
            return keymaps.actions().stream()
                    .sorted(Comparator.comparing((KeyAction action) -> action.modId())
                            .thenComparing(KeyAction::id))
                    .map(this::serverSnapshot)
                    .toList();
        }
        if (player.isEmpty()) {
            return List.of();
        }
        return keymaps.actionsFor(player.get()).stream()
                .sorted(Comparator.comparing((KeyAction action) -> action.modId())
                        .thenComparing(KeyAction::id))
                .map(action -> snapshot(action, player.get()))
                .toList();
    }

    @Override
    public Result<SettingSnapshot> read(SettingId id, Optional<PlayerId> player) {
        if (player.isEmpty()) {
            return known(id).map(this::serverSnapshot);
        }
        return player(player).flatMap(viewer -> action(id, viewer).map(action -> snapshot(action, viewer)));
    }

    @Override
    public Result<SettingSnapshot> write(SettingId id, Optional<PlayerId> player, String text) {
        if (player.isEmpty()) {
            return keymaps.bindServer(id, text).flatMap(binds -> read(id, player));
        }
        return player(player).flatMap(viewer -> keymaps.bind(id, viewer, text).flatMap(binds -> read(id, player)));
    }

    @Override
    public Result<SettingSnapshot> reset(SettingId id, Optional<PlayerId> player) {
        if (player.isEmpty()) {
            return keymaps.resetServerBind(id).flatMap(binds -> read(id, player));
        }
        return player(player).flatMap(viewer -> keymaps.resetBind(id, viewer).flatMap(binds -> read(id, player)));
    }

    @Override
    public Result<String> add(String name, String keys, String command, boolean runAsServer) {
        return keymaps.addCustom(name, keys, command, runAsServer).map(action -> KeymapsText.created(tr, action));
    }

    @Override
    public Optional<CustomKeyBind> find(String name) {
        return keymaps.findCustom(name);
    }

    @Override
    public Result<String> remove(String name) {
        return keymaps.removeCustom(name).map(action -> KeymapsText.removed(tr, action));
    }

    @Override
    public Subscription onAnyChange(Consumer<? super SettingChanged> listener) {
        return keymaps.onChange(listener);
    }

    private SettingSnapshot snapshot(KeyAction action, PlayerId player) {
        KeyBinds server = keymaps.serverKeys(action.id());
        Optional<KeyBinds> own = keymaps.ownKeys(action.id(), player);
        KeyBinds current = own.orElse(server);
        String problems = KeymapsText.problems(tr, current);
        String description =
                joined(tr.translate(action.descriptionText()), KeymapsText.runs(tr, action) + ".", problems);
        SettingDefinition<String> definition = SettingDefinition.player(
                        action.id().namespace(), action.id().key(), SettingType.text(MAX_LENGTH), server.toString())
                .named(action.nameText())
                .describedAs(description)
                .withModName(modNames.of(action.modId()));
        return new SettingSnapshot(definition, current.toString(), own.isPresent());
    }

    private SettingSnapshot serverSnapshot(KeyAction action) {
        KeyBinds modKeys = keymaps.modKeys(action.id());
        KeyBinds current = keymaps.serverKeys(action.id());
        String description = joined(
                tr.translate(action.descriptionText()),
                KeymapsText.runs(tr, action) + ".",
                KeymapsText.problems(tr, current));
        SettingDefinition<String> definition = SettingDefinition.server(
                        action.id().namespace(), action.id().key(), SettingType.text(MAX_LENGTH), modKeys.toString())
                .named(action.nameText())
                .describedAs(description)
                .withModName(modNames.of(action.modId()));
        return new SettingSnapshot(definition, current.toString(), keymaps.hasServerKeys(action.id()));
    }

    private Result<KeyAction> known(SettingId id) {
        return keymaps.find(id)
                .map(Result::success)
                .orElseGet(() -> Result.failure(Error.notFound(
                        "Keys.UnknownAction",
                        Text.key("controlcore.errors.keys.unknown_action", "There is no key action '{id}'.")
                                .with("id", id))));
    }

    private Result<KeyAction> action(SettingId id, PlayerId viewer) {
        return keymaps.find(id)
                .filter(action -> keymaps.canUse(action, viewer))
                .map(Result::success)
                .orElseGet(() -> Result.failure(Error.notFound(
                        "Keys.UnknownAction",
                        Text.key("controlcore.errors.keys.unknown_action", "There is no key action '{id}'.")
                                .with("id", id))));
    }

    private static Result<PlayerId> player(Optional<PlayerId> player) {
        return player.map(Result::success)
                .orElseGet(() -> Result.failure(Error.validation(
                        "Keys.WrongScope",
                        Text.key("controlcore.errors.keys.wrong_scope", "Key bindings belong to a player."))));
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

    @Override
    public GameActionMode gameAction(SettingId action) {
        return keymaps.gameAction(action);
    }

    @Override
    public Result<GameActionMode> setGameAction(SettingId action, GameActionMode mode) {
        return keymaps.setGameAction(action, mode);
    }
}
