package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.ofatech.controlcore.api.KeymapsApi;
import net.ofatech.controlcore.application.history.ChangeRecorder;
import net.ofatech.controlcore.application.history.HistoryValues;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;
import net.ofatech.controlcore.domain.ports.config.KeyBindStore;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(KeymapsApi.class)
public final class KeymapRegistry implements KeymapsApi {

    public static final String PLAYER_KEY_PREFIX = "keybind:";
    public static final String CUSTOM = "custom";
    public static final String SERVER_KEYS_NODE = "keys.server";

    private final KeyBindStore serverBinds;
    private final CustomKeyBindStore customBinds;
    private final PlayerData playerData;
    private final Permissions permissions;
    private final Log log;
    private final ChangeRecorder history;
    private final Map<SettingId, KeyAction> actions = new ConcurrentHashMap<>();
    private final List<Consumer<? super SettingChanged>> listeners = new CopyOnWriteArrayList<>();

    public KeymapRegistry(
            KeyBindStore serverBinds,
            CustomKeyBindStore customBinds,
            PlayerData playerData,
            Permissions permissions,
            Log log,
            ChangeRecorder history) {
        this.serverBinds = serverBinds;
        this.customBinds = customBinds;
        this.playerData = playerData;
        this.permissions = permissions;
        this.log = log;
        this.history = history;
    }

    @Override
    public Result<KeyAction> register(KeyAction action) {
        if (action != null && CUSTOM.equals(action.modId())) {
            return Result.failure(Error.validation(
                    "Keys.ReservedId",
                    Text.key(
                                    "controlcore.errors.keys.reserved_namespace",
                                    "The namespace '{namespace}' is for key actions admins create.")
                            .with("namespace", CUSTOM)));
        }
        return put(action);
    }

    @Override
    public void unregister(SettingId id) {
        if (id != null && !CUSTOM.equals(id.namespace())) {
            actions.remove(id);
        }
    }

    @Override
    public Optional<KeyAction> find(SettingId id) {
        return Optional.ofNullable(id).map(actions::get);
    }

    @Override
    public List<KeyAction> actions() {
        return actions.values().stream()
                .sorted(Comparator.comparing(KeyAction::id))
                .toList();
    }

    public List<KeyAction> actionsFor(PlayerId player) {
        return actions().stream().filter(action -> canUse(action, player)).toList();
    }

    public boolean canUse(KeyAction action, PlayerId player) {
        if (action.permission().isPresent()) {
            return permissions.has(player, action.permission().get());
        }
        return !action.runsAsServer() || permissions.hasOwn(player, SERVER_KEYS_NODE);
    }

    @Override
    public KeyBinds keysFor(SettingId id, PlayerId player) {
        return ownKeys(id, player).orElseGet(() -> serverKeys(id));
    }

    @Override
    public KeyBinds serverKeys(SettingId id) {
        return serverBinds.get(id).flatMap(text -> parsed(id, text)).orElseGet(() -> defaults(id));
    }

    public boolean hasServerKeys(SettingId id) {
        return serverBinds.get(id).flatMap(text -> parsed(id, text)).isPresent();
    }

    public KeyBinds modKeys(SettingId id) {
        return defaults(id);
    }

    public Optional<KeyBinds> ownKeys(SettingId id, PlayerId player) {
        return playerData.get(player, PLAYER_KEY_PREFIX + id).flatMap(text -> parsed(id, text));
    }

    @Override
    public Result<KeyBinds> bind(SettingId id, PlayerId player, String keys) {
        return usable(id, player).flatMap(action -> KeyBinds.parse(keys)).map(binds -> {
            String before = keysFor(id, player).toString();
            playerData.put(player, PLAYER_KEY_PREFIX + id, binds.toString());
            playerData.flush();
            changed(id, Optional.of(player), before, binds.toString());
            return binds;
        });
    }

    @Override
    public Result<KeyBinds> resetBind(SettingId id, PlayerId player) {
        return usable(id, player).map(action -> {
            String before = keysFor(id, player).toString();
            playerData.remove(player, PLAYER_KEY_PREFIX + id);
            playerData.flush();
            KeyBinds after = keysFor(id, player);
            changed(id, Optional.of(player), before, after.toString());
            return after;
        });
    }

    @Override
    public Result<KeyBinds> bindServer(SettingId id, String keys) {
        return known(id).flatMap(action -> KeyBinds.parse(keys)).map(binds -> {
            String before = serverKeys(id).toString();
            serverBinds.put(id, binds.toString());
            serverBinds.flush();
            changed(id, Optional.empty(), before, binds.toString());
            return binds;
        });
    }

    @Override
    public Result<KeyBinds> resetServerBind(SettingId id) {
        return known(id).map(action -> {
            String before = serverKeys(id).toString();
            serverBinds.remove(id);
            serverBinds.flush();
            changed(id, Optional.empty(), before, defaults(id).toString());
            return defaults(id);
        });
    }

    public void loadCustom() {
        for (CustomKeyBind bind : customBinds.all()) {
            customAction(bind.name(), bind.keys(), bind.command(), bind.runAsServer())
                    .flatMap(this::put)
                    .onFailure(error ->
                            log.warn("Ignoring custom key bind '" + bind.name() + "': " + error.description()));
        }
    }

    public Result<KeyAction> addCustom(String name, String keys, String command, boolean runAsServer) {
        return customAction(name, keys, command, runAsServer)
                .flatMap(action -> put(action).map(registered -> {
                    String before = HistoryValues.bind(findCustom(action.id().key()));
                    CustomKeyBind bind = new CustomKeyBind(
                            action.id().key(),
                            action.defaultKeys().toString(),
                            action.command().orElseThrow(),
                            runAsServer);
                    customBinds.put(bind);
                    history.record(
                            Kind.CUSTOM_BIND,
                            action.id().toString(),
                            before,
                            HistoryValues.bind(Optional.of(bind)),
                            false);
                    return registered;
                }));
    }

    public Result<KeyAction> removeCustom(String name) {
        return customId(name).flatMap(this::known).map(action -> {
            String before = HistoryValues.bind(findCustom(action.id().key()));
            actions.remove(action.id());
            customBinds.remove(action.id().key());
            serverBinds.remove(action.id());
            serverBinds.flush();
            history.record(Kind.CUSTOM_BIND, action.id().toString(), before, "", false);
            return action;
        });
    }

    public Optional<CustomKeyBind> findCustom(String name) {
        Result<SettingId> parsed = customId(name);
        return Optional.of(parsed)
                .filter(Result::isSuccess)
                .map(Result::value)
                .flatMap(id -> customBinds.all().stream()
                        .filter(bind -> bind.name().equals(id.key()))
                        .findFirst());
    }

    public Subscription onChange(Consumer<? super SettingChanged> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private Result<KeyAction> put(KeyAction action) {
        if (action == null) {
            return Result.failure(Error.validation(
                    "Keys.NothingToRun",
                    Text.key("controlcore.errors.keys.missing_action", "The key action is missing.")));
        }
        if (action.command().isEmpty() && action.onPress().isEmpty()) {
            return Result.failure(Error.validation(
                    "Keys.NothingToRun",
                    Text.key("controlcore.errors.keys.nothing_to_run", "Key action {id} needs a command or onPress.")
                            .with("id", action.id())));
        }
        actions.put(action.id(), action);
        log.debug("Registered key action " + action.id() + " (" + action.defaultKeys() + ")");
        return Result.success(action);
    }

    private Result<KeyAction> customAction(String name, String keys, String command, boolean runAsServer) {
        if (command == null || KeyAction.normalizeCommand(command).isBlank()) {
            return Result.failure(Error.validation(
                    "Keys.NothingToRun",
                    Text.key("controlcore.errors.keys.no_command", "Write the command the keys should run.")));
        }
        return customId(name)
                .flatMap(id -> KeyBinds.parse(keys).map(binds -> {
                    KeyAction action = KeyAction.of(CUSTOM, id.key())
                            .displayName(id.key())
                            .describedAs(Text.key("controlcore.keys.custom.added_by_admin", "Added by an admin."))
                            .command(command)
                            .defaultKeys(binds);
                    return runAsServer ? action.runAsServer() : action;
                }));
    }

    private static Result<SettingId> customId(String name) {
        String text = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
        if (text.startsWith(CUSTOM + ":")) {
            text = text.substring(CUSTOM.length() + 1);
        }
        Result<SettingId> id = SettingId.parse(CUSTOM + ":" + text);
        return id.isSuccess()
                ? id
                : Result.failure(Error.validation(
                        "Keys.InvalidName",
                        Text.key(
                                        "controlcore.errors.keys.invalid_name",
                                        "'{text}' is not a valid name: use lower case letters, digits, - and _.")
                                .with("text", text)));
    }

    private Result<KeyAction> known(SettingId id) {
        return find(id).map(Result::success)
                .orElseGet(() -> Result.failure(Error.notFound(
                        "Keys.UnknownAction",
                        Text.key(
                                        "controlcore.errors.keys.unknown_action_list",
                                        "There is no key action '{id}'. See /cc-keys for the list.")
                                .with("id", id))));
    }

    private Result<KeyAction> usable(SettingId id, PlayerId player) {
        return known(id)
                .flatMap(action -> canUse(action, player)
                        ? Result.success(action)
                        : Result.failure(Error.notFound(
                                "Keys.UnknownAction",
                                Text.key(
                                                "controlcore.errors.keys.unknown_action_list",
                                                "There is no key action '{id}'. See /cc-keys for the list.")
                                        .with("id", id))));
    }

    private KeyBinds defaults(SettingId id) {
        return find(id).map(KeyAction::defaultKeys).orElse(KeyBinds.UNBOUND);
    }

    private Optional<KeyBinds> parsed(SettingId id, String text) {
        Result<KeyBinds> binds = KeyBinds.parse(text);
        if (binds.isFailure()) {
            log.warn("Ignoring saved keys '" + text + "' of " + id + ": "
                    + binds.error().description());
            return Optional.empty();
        }
        return Optional.of(binds.value());
    }

    private void changed(SettingId id, Optional<PlayerId> player, String before, String after) {
        SettingScope scope = player.isPresent() ? SettingScope.PLAYER : SettingScope.SERVER;
        SettingChanged event = new SettingChanged(id, scope, player, before, after);
        for (Consumer<? super SettingChanged> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                log.error("A key binding listener failed", e);
            }
        }
    }
}
