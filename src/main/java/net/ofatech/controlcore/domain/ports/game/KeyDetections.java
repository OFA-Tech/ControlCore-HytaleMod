package net.ofatech.controlcore.domain.ports.game;

import java.util.function.BooleanSupplier;
import net.ofatech.controlcore.domain.model.PlayerId;

@FunctionalInterface
public interface KeyDetections {

    KeyDetections NONE = (player, rightsUnchanged) -> KeyDetection.NONE;

    KeyDetection viewedBy(PlayerId player, BooleanSupplier rightsUnchanged);
}
