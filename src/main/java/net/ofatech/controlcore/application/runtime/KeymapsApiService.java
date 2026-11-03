package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.ofatech.controlcore.api.KeymapsApi;
import net.ofatech.controlcore.application.keymaps.KeymapRegistry;
import net.ofatech.controlcore.application.keymaps.KeymapsEditor;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(KeymapsApi.class)
public final class KeymapsApiService implements KeymapsApi {

    private final KeymapRegistry registry;
    private final KeymapsEditor editor;
    private final SettingPermissionPolicy policy;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public KeymapsApiService(
            KeymapRegistry registry, KeymapsEditor editor, SettingPermissionPolicy policy, PlayerDirectory directory) {
        this(registry, editor, policy, directory, ApiActor.MOD);
    }

    private KeymapsApiService(
            KeymapRegistry registry,
            KeymapsEditor editor,
            SettingPermissionPolicy policy,
            PlayerDirectory directory,
            ApiActor actor) {
        this.registry = registry;
        this.policy = policy;
        this.directory = directory;
        this.actor = actor;
        this.editor = actor.who().map(player -> editor.viewedBy(player, policy)).orElse(editor);
    }

    @Override
    public KeymapsApi actingFor(PlayerId player) {
        return new KeymapsApiService(registry, editor, policy, directory, ApiActor.of(player, directory));
    }

    @Override
    public Result<KeyAction> register(KeyAction action) {
        return registry.register(action);
    }

    @Override
    public void unregister(SettingId id) {
        registry.unregister(id);
    }

    @Override
    public Optional<KeyAction> find(SettingId id) {
        return registry.find(id);
    }

    @Override
    public List<KeyAction> actions() {
        return registry.actions();
    }

    @Override
    public KeyBinds keysFor(SettingId id, PlayerId player) {
        return registry.keysFor(id, player);
    }

    @Override
    public KeyBinds serverKeys(SettingId id) {
        return registry.serverKeys(id);
    }

    @Override
    public Result<KeyBinds> bind(SettingId id, PlayerId player, String keys) {
        return own(player, () -> registry.bind(id, player, keys));
    }

    @Override
    public Result<KeyBinds> resetBind(SettingId id, PlayerId player) {
        return own(player, () -> registry.resetBind(id, player));
    }

    @Override
    public Result<KeyBinds> bindServer(SettingId id, String keys) {
        if (actor.plain()) {
            return registry.bindServer(id, keys);
        }
        return actor.run(() -> editor.write(id, Optional.empty(), keys)).map(changed -> registry.serverKeys(id));
    }

    @Override
    public Result<KeyBinds> resetServerBind(SettingId id) {
        if (actor.plain()) {
            return registry.resetServerBind(id);
        }
        return actor.run(() -> editor.reset(id, Optional.empty())).map(changed -> registry.serverKeys(id));
    }

    @Override
    public List<CustomKeyBind> customBinds() {
        return customActions()
                .flatMap(action -> registry.findCustom(action.id().key()).stream())
                .sorted(Comparator.comparing(CustomKeyBind::name))
                .toList();
    }

    @Override
    public Optional<CustomKeyBind> findCustom(String name) {
        return registry.findCustom(name)
                .filter(bind ->
                        customActions().anyMatch(action -> action.id().key().equals(bind.name())));
    }

    private Stream<KeyAction> customActions() {
        return registry.actions().stream()
                .filter(action -> action.modId().equals(KeymapRegistry.CUSTOM))
                .filter(action -> actor.plain()
                        || actor.who()
                                .map(player -> registry.canUse(action, player))
                                .orElse(true));
    }

    @Override
    public Result<String> addCustom(String name, String keys, String command, boolean runAsServer) {
        return actor.run(() -> editor.add(name, keys, command, runAsServer));
    }

    @Override
    public Result<String> removeCustom(String name) {
        return actor.run(() -> editor.remove(name));
    }

    @Override
    public GameActionMode gameActionMode(SettingId id) {
        return registry.gameAction(id);
    }

    @Override
    public Result<GameActionMode> setGameActionMode(SettingId id, GameActionMode mode) {
        return actor.run(() -> editor.setGameAction(id, mode));
    }

    private Result<KeyBinds> own(PlayerId player, Supplier<Result<KeyBinds>> work) {
        if (!actor.self(Optional.ofNullable(player))) {
            return ApiActor.forbidden("Keys.Forbidden", "Players may only change their own key bindings.");
        }
        return actor.run(work);
    }
}
