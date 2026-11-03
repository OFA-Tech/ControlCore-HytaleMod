package net.ofatech.controlcore.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Automation rules: "when this happens, if that holds, do these things", as in the control panel's Automation tab
 * and {@code /cc-rules}. Rules are written as a {@link Form}, checked the same way as in the panel. Mods add their
 * own triggers and actions with {@link ActionsApi}. Get it with {@link ControlCoreApi#rules()}.
 *
 * <p>The API acts with full rights, like the console. {@link #actingFor(PlayerId)} gives the same API with a
 * player's rights: only admins may manage rules.
 *
 * @since API 2.4.0
 */
public interface RulesApi {

    /**
     * Returns this API acting for a player. Only admins may use it: for anyone else {@link #list()} is empty,
     * {@link #get(String)} is empty and every change fails with {@code Automation.Forbidden}. A tested rule runs
     * for the player.
     *
     * @param player the player whose rights apply
     * @return the rules API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    RulesApi actingFor(PlayerId player);

    /**
     * Lists the rules.
     *
     * @return every rule in the order they were added; empty if this API may not manage rules
     */
    List<Entry> list();

    /**
     * Reads a rule as a form, ready to change and {@link #save(Optional, Form) save}.
     *
     * @param id the rule's id
     * @return the rule's form, or empty if there is no such rule or this API may not manage rules
     */
    Optional<Form> get(String id);

    /**
     * Adds a rule, or changes one.
     *
     * @param editing the id of the rule to change; empty to add a new one
     * @param form the rule
     * @return the rule's id, or an error such as {@code Automation.InvalidTrigger}, {@code Automation.NoAction},
     *     {@code Automation.Unknown} or {@code Automation.Forbidden}
     */
    Result<String> save(Optional<String> editing, Form form);

    /**
     * Turns a rule on or off.
     *
     * @param id the rule's id
     * @return a message saying whether it is now on, or an error such as {@code Automation.Unknown} or
     *     {@code Automation.Forbidden}
     */
    Result<String> toggle(String id);

    /**
     * Runs a rule's actions once now, ignoring its trigger, conditions and cooldown.
     *
     * @param id the rule's id
     * @return a message with what ran, or an error such as {@code Automation.Unknown} or
     *     {@code Automation.Forbidden}
     */
    Result<String> test(String id);

    /**
     * Deletes a rule.
     *
     * @param id the rule's id
     * @return a confirmation message, or an error such as {@code Automation.Unknown} or
     *     {@code Automation.Forbidden}
     */
    Result<String> remove(String id);

    /**
     * Lists the trigger ids a form may use, for example {@code join} or {@code daily-at}.
     *
     * @return the built-in trigger ids
     */
    List<String> triggers();

    /**
     * Lists the condition ids a form may use, for example {@code has-permission}; {@code none} means no condition.
     *
     * @return the condition ids, {@code none} first
     */
    List<String> conditions();

    /**
     * Lists the action ids a form may use, for example {@code message-player} or {@code command-server}.
     *
     * @return the built-in action ids
     */
    List<String> actions();

    /**
     * Lists the triggers mods added, for the trigger {@code mod-trigger}.
     *
     * @return trigger id to display name
     */
    Map<String, String> modTriggers();

    /**
     * Lists the actions mods added, for the action {@code mod-action}.
     *
     * @return action id to display name
     */
    Map<String, String> modActions();

    /**
     * A rule as listed.
     *
     * @param id the rule's id
     * @param name the rule's name
     * @param enabled whether it is on
     * @param summary what it does, in the reader's language
     * @param lastRun when it last ran, in the reader's language
     */
    record Entry(String id, String name, boolean enabled, String summary, String lastRun) {}

    /**
     * One action of a rule.
     *
     * @param kind the action id, see {@link #actions()}
     * @param value the action's text, for example the message or command; {@code {player}} is filled in
     */
    record Step(String kind, String value) {}

    /**
     * A rule as written in the panel's form.
     *
     * @param name the rule's name
     * @param trigger the trigger id, see {@link #triggers()}
     * @param triggerValue the trigger's value, for example {@code 08:00} for {@code daily-at}; empty if none
     * @param condition the condition id, see {@link #conditions()}; {@code none} for no condition
     * @param conditionValue the condition's value, for example a permission node; empty if none
     * @param actions what the rule does, in order; at least one
     * @param cooldown the seconds before it may run again for the same player, as text; empty for the trigger's
     *     default
     */
    record Form(
            String name,
            String trigger,
            String triggerValue,
            String condition,
            String conditionValue,
            List<Step> actions,
            String cooldown) {}
}
