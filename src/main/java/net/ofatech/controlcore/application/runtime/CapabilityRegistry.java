package net.ofatech.controlcore.application.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.ports.platform.Capabilities;

final class CapabilityRegistry implements Capabilities {

    private final Map<Class<?>, Object> capabilities = new ConcurrentHashMap<>();

    @Override
    public <T> Optional<T> find(Class<T> capability) {
        return Optional.ofNullable(capabilities.get(capability)).map(capability::cast);
    }

    boolean offer(Class<?> capability, Object implementation) {
        return capabilities.putIfAbsent(capability, implementation) == null;
    }

    void clear() {
        capabilities.clear();
    }
}
