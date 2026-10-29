package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

@FunctionalInterface
public interface KeyDetections {

    KeyDetections NONE = player -> KeyDetection.NONE;

    KeyDetection viewedBy(PlayerId player);

    default void cancel(PlayerId player) {}
}
