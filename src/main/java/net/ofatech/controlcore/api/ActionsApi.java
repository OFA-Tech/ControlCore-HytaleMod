package net.ofatech.controlcore.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;

/**
 * Lets mods plug into automation rules: offer actions rules can run, and announce triggers rules can react to.
 *
 * <pre>{@code
 * api.actions().registerTrigger("arena", "match-won", "An arena match is won");
 * api.actions().fire("arena", "match-won", Optional.of(winner), Map.of("arena", "Lava pit"));
 * }</pre>
 *
 * @since API 2.1.0
 */
public interface ActionsApi {

    /**
     * Offers an action to automation rules. Registering the same id again replaces it.
     *
     * @param action the action
     * @throws IllegalArgumentException if the action has no code to run
     */
    void register(AutomationAction action);

    /**
     * Removes an action you registered.
     *
     * @param id the action's id, {@code modId:key}
     */
    void unregister(String id);

    /**
     * The actions mods registered.
     *
     * @return every registered action
     */
    Collection<AutomationAction> actions();

    /**
     * Announces a trigger rules can choose ("Mod trigger" with the value {@code modId:key}).
     *
     * @param modId your mod's id
     * @param key the trigger's key
     * @param displayName the name admins see
     * @throws IllegalArgumentException if a part is blank or has characters other than {@code a-z 0-9 _ -}
     */
    void registerTrigger(String modId, String key, String displayName);

    /**
     * Runs every enabled rule whose trigger is this mod trigger.
     *
     * @param modId your mod's id
     * @param key the trigger's key
     * @param player the player who caused it, if any; fills {@code {player}} and player conditions
     * @param variables extra placeholders, for example {@code arena} for {@code {arena}}
     */
    void fire(String modId, String key, Optional<PlayerId> player, Map<String, String> variables);

    /**
     * The triggers mods announced, as {@code modId:key} to display name.
     *
     * @return every registered trigger
     */
    Map<String, String> triggers();
}
