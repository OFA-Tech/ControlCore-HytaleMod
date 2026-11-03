package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;

public final class FakeCustomKeyBindStore implements CustomKeyBindStore {

    public final Map<String, CustomKeyBind> binds = new TreeMap<>();
    public boolean failWrites;

    @Override
    public synchronized List<CustomKeyBind> all() {
        return List.copyOf(binds.values());
    }

    @Override
    public synchronized boolean put(CustomKeyBind bind) {
        if (failWrites) {
            return false;
        }
        binds.put(bind.name(), bind);
        return true;
    }

    @Override
    public synchronized boolean remove(String name) {
        if (failWrites) {
            return false;
        }
        binds.remove(name);
        return true;
    }
}
