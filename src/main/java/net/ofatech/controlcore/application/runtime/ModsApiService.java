package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.api.ModsApi;
import net.ofatech.controlcore.application.mods.ModsEditor;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModsApi.class)
public final class ModsApiService implements ModsApi {

    private final ModsEditor mods;
    private final ServerConfigEditor serverConfig;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public ModsApiService(ModsEditor mods, ServerConfigEditor serverConfig, PlayerDirectory directory) {
        this(mods, serverConfig, directory, ApiActor.MOD);
    }

    private ModsApiService(
            ModsEditor mods, ServerConfigEditor serverConfig, PlayerDirectory directory, ApiActor actor) {
        this.mods = mods;
        this.serverConfig = serverConfig;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public ModsApi actingFor(PlayerId player) {
        return new ModsApiService(mods, serverConfig, directory, ApiActor.of(player, directory));
    }

    @Override
    public List<Mod> list() {
        return view().list().stream().map(ModsApiService::mod).toList();
    }

    @Override
    public List<Mod> alsoChanged(String modId, boolean enabled) {
        return view().alsoChanged(modId, enabled).stream()
                .map(ModsApiService::mod)
                .toList();
    }

    @Override
    public Result<List<Mod>> setEnabled(String modId, boolean enabled) {
        ModsView view = view();
        List<Mod> others = alsoChanged(modId, enabled);
        return actor.run(() -> view.setEnabled(modId, enabled)).map(changed -> {
            List<Mod> all = new ArrayList<>();
            all.add(mod(changed));
            all.addAll(others);
            return List.copyOf(all);
        });
    }

    @Override
    public List<ConfigFile> configFiles(String modId) {
        return view().configFiles(modId).stream()
                .map(file -> new ConfigFile(file.name(), file.lines()))
                .toList();
    }

    @Override
    public List<Text> pendingRestart() {
        if (!view().canManage()) {
            return List.of();
        }
        List<Text> names = new ArrayList<>(serverConfig.pendingRestart());
        names.addAll(mods.pendingRestart());
        return List.copyOf(names);
    }

    private ModsView view() {
        return mods.viewedBy(actor.who());
    }

    private static Mod mod(ModsView.Entry entry) {
        ModInfo mod = entry.mod();
        return new Mod(
                mod.id(),
                mod.name(),
                mod.version(),
                mod.description(),
                mod.authors(),
                mod.website(),
                Kind.valueOf(mod.kind().name()),
                State.valueOf(mod.state().name()),
                mod.core(),
                mod.enabledAfterRestart(),
                mod.serverVersion(),
                mod.serverVersionOk(),
                mod.dependencies().stream()
                        .map(dependency -> new Dependency(
                                dependency.id(),
                                dependency.range(),
                                dependency.optional(),
                                DependencyStatus.valueOf(dependency.status().name())))
                        .toList(),
                entry.neededBy(),
                entry.manageable(),
                entry.hasProblems());
    }
}
