package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Shows a player what their client tells the server when they press keys and buttons, to find out which inputs
 * can be used as key triggers. The server never sees raw keys, only the actions the client derives from them.
 */
public interface InputProbe {

    /** Targets accepted by {@link #openKeyTest}. */
    String KEY_TEST_TARGETS = "input, root, page";

    /** @return whether the probe is now on for the player */
    boolean toggle(PlayerId player);

    /**
     * Opens a test screen that listens for key presses on {@code target} (one of {@link #KEY_TEST_TARGETS}) and
     * shows the raw data the client sends.
     */
    Result<Void> openKeyTest(PlayerId player, String target);
}
