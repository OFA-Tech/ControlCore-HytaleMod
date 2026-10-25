package net.ofatech.controlcore.domain.model.keys;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.result.Result;

public interface CustomKeyBindEditor {

    Result<String> add(String name, String keys, String command, boolean runAsServer);

    Optional<CustomKeyBind> find(String name);

    Result<String> remove(String name);

    GameActionMode gameAction(SettingId action);

    Result<GameActionMode> setGameAction(SettingId action, GameActionMode mode);
}
