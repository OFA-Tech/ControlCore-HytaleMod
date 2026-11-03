package net.ofatech.controlcore.domain.model.mods;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public interface ModsView {

    List<Entry> list();

    boolean canManage();

    Result<Entry> setEnabled(String modId, boolean enabled);

    Result<List<Entry>> plan(String modId, boolean enabled);

    List<Entry> alsoChanged(String modId, boolean enabled);

    List<ModCatalog.ConfigFile> configFiles(String modId);

    default Result<Entry> find(String input) {
        String wanted = input == null ? "" : input.strip();
        return list().stream()
                .filter(entry -> entry.mod().id().equalsIgnoreCase(wanted)
                        || entry.mod().name().equalsIgnoreCase(wanted)
                        || entry.namespace().filter(wanted::equalsIgnoreCase).isPresent())
                .findFirst()
                .map(Result::success)
                .orElseGet(() -> Result.failure(Error.notFound(
                        "Mods.Unknown",
                        Text.key("controlcore.errors.mods.unknown", "There is no mod {id}.")
                                .with("id", wanted))));
    }

    record Entry(
            ModInfo mod,
            Optional<String> namespace,
            boolean hasServerSettings,
            boolean hasPreferences,
            boolean hasKeys,
            boolean manageable,
            List<String> neededBy) {

        public Entry {
            neededBy = neededBy == null ? List.of() : List.copyOf(neededBy);
        }

        public boolean hasProblems() {
            return !mod.serverVersionOk() || mod.dependencies().stream().anyMatch(ModDependency::problem);
        }
    }
}
