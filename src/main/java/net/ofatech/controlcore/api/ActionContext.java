package net.ofatech.controlcore.api;

import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;

/**
 * What an automation rule hands to a mod's action when it runs it.
 *
 * @param rule the name of the rule running the action
 * @param player the player who caused the trigger, if any
 * @param value the text the admin typed after the action's id, with placeholders filled in; may be empty
 * @param variables the trigger's placeholders, for example {@code player}, {@code world} and {@code online}
 * @since API 2.1.0
 */
public record ActionContext(String rule, Optional<PlayerId> player, String value, Map<String, String> variables) {}
