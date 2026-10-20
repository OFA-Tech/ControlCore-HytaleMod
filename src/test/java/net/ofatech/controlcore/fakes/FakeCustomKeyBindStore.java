package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;

public final class FakeCustomKeyBindStore implements CustomKeyBindStore {

    public final Map<String, CustomKeyBind> binds = new TreeMap<>();

    @Override
    public synchronized List<CustomKeyBind> all() {
        return List.copyOf(binds.values());
    }

    @Override
    public synchronized void put(CustomKeyBind bind) {
        binds.put(bind.name(), bind);
    }

    @Override
    public synchronized void remove(String name) {
        binds.remove(name);
    }
}
