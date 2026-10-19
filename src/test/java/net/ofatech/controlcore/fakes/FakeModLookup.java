package net.ofatech.controlcore.fakes;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.ports.mods.ModLookup;

public final class FakeModLookup implements ModLookup {

    public final Set<String> loaded = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isLoaded(String modId) {
        return loaded.contains(modId);
    }
}
