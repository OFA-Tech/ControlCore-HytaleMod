package net.ofatech.controlcore.domain.ports.config;

import java.util.List;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;

public interface CustomKeyBindStore {

    List<CustomKeyBind> all();

    boolean put(CustomKeyBind bind);

    boolean remove(String name);
}
