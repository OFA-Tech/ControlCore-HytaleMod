package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

public interface InputProbe {

    String KEY_TEST_TARGETS = "input";

    boolean toggle(PlayerId player);

    boolean isOn(PlayerId player);

    Result<Void> openKeyTest(PlayerId player, String target);
}
