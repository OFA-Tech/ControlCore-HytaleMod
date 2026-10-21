package net.ofatech.controlcore.domain.ports.game;

/**
 * Whether the game client reports keys that do nothing with an empty hand (or a plain block or item in hand). The
 * client only tells the server about a key if what the player holds has an interaction for it; ControlCore can add
 * a do-nothing interaction so those keys can be bound.
 */
public interface EmptyHandInput {

    /**
     * @param on whether the empty hand, plain blocks and plain items report every key the game can report (right
     *     click, abilities 1, 2 and 4, dodge); off restores the game's own interactions
     */
    void reportAllKeys(boolean on);
}
