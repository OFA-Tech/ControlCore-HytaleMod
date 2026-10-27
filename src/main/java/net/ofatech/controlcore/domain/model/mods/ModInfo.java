package net.ofatech.controlcore.domain.model.mods;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.Guard;

public record ModInfo(
        String id,
        String name,
        String group,
        String version,
        String description,
        List<String> authors,
        String website,
        Kind kind,
        State state,
        boolean core,
        boolean enabledAfterRestart,
        Optional<String> logo,
        List<ModDependency> dependencies,
        String serverVersion,
        boolean serverVersionOk) {

    public ModInfo {
        Guard.notBlank(id, "id");
        Guard.notNull(name, "name");
        Guard.notNull(group, "group");
        version = version == null ? "" : version;
        description = description == null ? "" : description.strip();
        authors = authors == null ? List.of() : List.copyOf(authors);
        website = website == null ? "" : website.strip();
        Guard.notNull(kind, "kind");
        Guard.notNull(state, "state");
        logo = logo == null ? Optional.empty() : logo;
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        serverVersion = serverVersion == null || serverVersion.strip().equals("*") ? "" : serverVersion.strip();
    }

    public ModInfo(
            String id,
            String name,
            String group,
            String version,
            String description,
            List<String> authors,
            String website,
            Kind kind,
            State state,
            boolean core,
            boolean enabledAfterRestart,
            Optional<String> logo) {
        this(
                id,
                name,
                group,
                version,
                description,
                authors,
                website,
                kind,
                state,
                core,
                enabledAfterRestart,
                logo,
                List.of(),
                "",
                true);
    }

    public ModInfo withEnabledAfterRestart(boolean enabled) {
        return new ModInfo(
                id,
                name,
                group,
                version,
                description,
                authors,
                website,
                kind,
                state,
                core,
                enabled,
                logo,
                dependencies,
                serverVersion,
                serverVersionOk);
    }

    public ModInfo withDependencies(List<ModDependency> changed) {
        return new ModInfo(
                id,
                name,
                group,
                version,
                description,
                authors,
                website,
                kind,
                state,
                core,
                enabledAfterRestart,
                logo,
                changed,
                serverVersion,
                serverVersionOk);
    }

    public List<ModDependency> required() {
        return dependencies.stream()
                .filter(dependency -> !dependency.optional())
                .toList();
    }

    public enum Kind {
        PLUGIN,
        ASSET_PACK,
        PLUGIN_WITH_ASSETS
    }

    public enum State {
        ENABLED,
        DISABLED,
        FAILED,
        NOT_LOADED
    }

    public boolean running() {
        return state == State.ENABLED;
    }

    public boolean changesAfterRestart() {
        return enabledAfterRestart != running() && state != State.FAILED;
    }

    public boolean hasAssets() {
        return kind != Kind.PLUGIN;
    }
}
