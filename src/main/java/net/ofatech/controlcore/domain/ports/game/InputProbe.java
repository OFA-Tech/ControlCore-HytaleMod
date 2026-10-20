package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

/**
 * Shows a player what their client tells the server when they press keys and buttons, to find out which inputs
 * can be used as key triggers. The server never sees raw keys, only the actions the client derives from them.
 */
public interface InputProbe {

    /** @return whether the probe is now on for the player */
    boolean toggle(PlayerId player);
}
