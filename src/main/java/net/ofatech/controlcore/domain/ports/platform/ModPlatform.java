package net.ofatech.controlcore.domain.ports.platform;

public interface ModPlatform {

    String modId();

    void bind(CapabilityBinder binder);
}
