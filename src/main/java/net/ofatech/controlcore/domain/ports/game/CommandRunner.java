package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

/** Runs chat commands on behalf of players. */
public interface CommandRunner {

    /**
     * Runs {@code command} as if the player typed it, with their permissions. Returns at once; the command runs
     * in the background and failures are logged.
     *
     * @param command the command without the leading {@code /}
     */
    void runAs(PlayerId player, String command);

    /** Runs {@code work} in the background (on a virtual thread); failures are logged. */
    void runInBackground(String description, Runnable work);
}
