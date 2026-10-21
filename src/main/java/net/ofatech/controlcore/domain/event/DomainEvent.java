package net.ofatech.controlcore.domain.event;

/**
 * Marker for events published on ControlCore's event bus, both translated game events and its own, such as
 * {@link SettingChanged}. Listen with
 * {@link net.ofatech.controlcore.api.ControlCoreEvents#subscribe(Class, java.util.function.Consumer)}.
 */
public interface DomainEvent {}
