package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.ofatech.controlcore.api.PermissionsApi;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.PermissionsEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(PermissionsApi.class)
public final class PermissionsApiService implements PermissionsApi {

    private final PermissionsEditor editor;
    private final SettingPermissionPolicy policy;
    private final SettingsRegistry settings;
    private final ServerConfigEditor serverConfig;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public PermissionsApiService(
            PermissionsEditor editor,
            SettingPermissionPolicy policy,
            SettingsRegistry settings,
            ServerConfigEditor serverConfig,
            Texts texts,
            PlayerDirectory directory) {
        this(editor, policy, settings, serverConfig, texts, directory, ApiActor.MOD);
    }

    private PermissionsApiService(
            PermissionsEditor editor,
            SettingPermissionPolicy policy,
            SettingsRegistry settings,
            ServerConfigEditor serverConfig,
            Texts texts,
            PlayerDirectory directory,
            ApiActor actor) {
        this.editor = editor;
        this.policy = policy;
        this.settings = settings;
        this.serverConfig = serverConfig;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public PermissionsApi actingFor(PlayerId player) {
        return new PermissionsApiService(
                editor, policy, settings, serverConfig, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public List<Entry> rules() {
        if (!admin()) {
            return List.of();
        }
        return editor.viewedBy(texts.forPlayer(actor.who())).list().stream()
                .map(entry -> new Entry(
                        Level.valueOf(entry.level().name()),
                        entry.target(),
                        entry.heading(),
                        entry.name(),
                        SettingPermissionPolicy.target(entry.target())
                                .map(policy::rule)
                                .orElse(SettingPermission.INHERIT)))
                .toList();
    }

    @Override
    public Result<SettingPermission> set(String target, String side, String node) {
        return allowed(() -> policy.setRule(target, side, node));
    }

    @Override
    public Result<SettingPermission> reset(String target) {
        return allowed(() -> policy.replaceRule(target, "", ""));
    }

    @Override
    public Result<Check> check(SettingId id) {
        return allowed(() -> definition(id).map(definition -> {
            SettingPermissionPolicy.Resolved resolved = policy.resolve(definition);
            return new Check(
                    definition.id(),
                    resolved.view().node(),
                    resolved.view().source(),
                    resolved.edit().node(),
                    resolved.edit().source());
        }));
    }

    private Result<SettingDefinition<?>> definition(SettingId id) {
        Optional<SettingDefinition<?>> registered = id == null ? Optional.empty() : settings.definition(id);
        if (registered.isPresent()) {
            return Result.success(registered.get());
        }
        return id != null && ServerConfigEditor.owns(id)
                ? serverConfig.read(id, Optional.empty()).map(snapshot -> snapshot.definition())
                : Result.failure(Error.notFound(
                        "Settings.UnknownSetting",
                        Text.key("controlcore.errors.settings.unknown_setting", "There is no setting {id}.")
                                .with("id", id)));
    }

    private boolean admin() {
        return actor.who().map(policy::isAdmin).orElse(true);
    }

    private <T> Result<T> allowed(Supplier<Result<T>> work) {
        return admin()
                ? actor.run(work)
                : ApiActor.forbidden("Permissions.Forbidden", "Only admins can see and change permission rules.");
    }
}
