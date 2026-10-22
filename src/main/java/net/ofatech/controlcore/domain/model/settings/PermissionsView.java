package net.ofatech.controlcore.domain.model.settings;

import java.util.List;
import net.ofatech.controlcore.domain.result.Result;

public interface PermissionsView {

    List<Entry> list();

    Result<String> save(String target, String view, String edit);

    Result<String> reset(String target);

    enum Level {
        MOD,
        CATEGORY,
        SETTING
    }

    record Entry(
            Level level,
            String target,
            String heading,
            String name,
            String description,
            String view,
            String edit,
            boolean adminRule,
            SettingDefinition<?> definition) {}
}
