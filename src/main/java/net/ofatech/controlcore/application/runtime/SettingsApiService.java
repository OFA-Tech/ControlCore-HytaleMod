package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(SettingsApi.class)
public final class SettingsApiService implements SettingsApi {

    private final SettingsRegistry registry;
    private final ServerConfigEditor serverConfig;
    private final SettingPermissionPolicy policy;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public SettingsApiService(
            SettingsRegistry registry,
            ServerConfigEditor serverConfig,
            SettingPermissionPolicy policy,
            PlayerDirectory directory) {
        this(registry, serverConfig, policy, directory, ApiActor.MOD);
    }

    private SettingsApiService(
            SettingsRegistry registry,
            ServerConfigEditor serverConfig,
            SettingPermissionPolicy policy,
            PlayerDirectory directory,
            ApiActor actor) {
        this.registry = registry;
        this.serverConfig = serverConfig;
        this.policy = policy;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public SettingsApi actingFor(PlayerId player) {
        return new SettingsApiService(registry, serverConfig, policy, directory, ApiActor.of(player, directory));
    }

    @Override
    public <T> Result<Setting<T>> register(SettingDefinition<T> definition) {
        return registry.register(definition);
    }

    @Override
    public Optional<SettingDefinition<?>> definition(SettingId id) {
        return registry.definition(id);
    }

    @Override
    public List<SettingDefinition<?>> definitions() {
        return registry.definitions();
    }

    @Override
    public void setModName(String modId, Text name) {
        registry.setModName(modId, name);
    }

    @Override
    public Text modName(String modId) {
        return registry.modName(modId);
    }

    @Override
    public void setCategoryPermission(String category, SettingPermission rule) {
        registry.setCategoryPermission(category, rule);
    }

    @Override
    public SettingPermission categoryPermission(String category) {
        return registry.categoryPermission(category);
    }

    @Override
    public List<SettingSnapshot> snapshot(SettingScope scope, Optional<PlayerId> player) {
        return actor.self(player) ? permitted(registry).snapshot(scope, player) : List.of();
    }

    @Override
    public Result<SettingSnapshot> read(SettingId id, Optional<PlayerId> player) {
        return actor.self(player) ? editor(id).read(id, player) : othersRefused();
    }

    @Override
    public Result<SettingSnapshot> write(SettingId id, Optional<PlayerId> player, String text) {
        return actor.self(player) ? actor.run(() -> editor(id).write(id, player, text)) : othersRefused();
    }

    @Override
    public Result<SettingSnapshot> reset(SettingId id, Optional<PlayerId> player) {
        return actor.self(player) ? actor.run(() -> editor(id).reset(id, player)) : othersRefused();
    }

    @Override
    public Subscription onAnyChange(Consumer<? super SettingChanged> listener) {
        return registry.onAnyChange(listener);
    }

    @Override
    public List<SettingSnapshot> serverConfigs() {
        return permitted(serverConfig).snapshot(SettingScope.SERVER, Optional.empty());
    }

    @Override
    public List<String> worlds() {
        return serverConfig.worldNames();
    }

    private SettingsEditor editor(SettingId id) {
        return permitted(id != null && ServerConfigEditor.owns(id) ? serverConfig : registry);
    }

    private SettingsEditor permitted(SettingsEditor editor) {
        return actor.plain() ? editor : policy.editor(editor, actor.who());
    }

    private static Result<SettingSnapshot> othersRefused() {
        return ApiActor.forbidden("Settings.Forbidden", "Players may only see and change their own preferences.");
    }
}
