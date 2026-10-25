package net.ofatech.controlcore.api;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * An action a mod offers to automation rules, for example {@code homes:reset-homes}. Admins choose it in the panel's
 * Automation tab as "Mod action" with the value {@code homes:reset-homes} (optionally followed by a space and a
 * value passed to the action).
 *
 * <pre>{@code
 * api.actions().register(AutomationAction.of("homes", "reset-homes")
 *         .displayName("Reset everyone's homes")
 *         .run(context -> homes.resetAll()));
 * }</pre>
 *
 * @since API 2.1.0
 */
public final class AutomationAction {

    private static final Pattern PART = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private final String modId;
    private final String key;
    private String displayName;
    private Consumer<ActionContext> work;

    private AutomationAction(String modId, String key) {
        this.modId = checked(modId, "modId");
        this.key = checked(key, "key");
        this.displayName = this.modId + ":" + this.key;
    }

    /**
     * Starts an action.
     *
     * @param modId your mod's id, lower case, for example {@code homes}
     * @param key the action's key, lower case, for example {@code reset-homes}
     * @return the action, to finish with {@link #run(Consumer)}
     * @throws IllegalArgumentException if a part is blank or has characters other than {@code a-z 0-9 _ -}
     */
    public static AutomationAction of(String modId, String key) {
        return new AutomationAction(modId, key);
    }

    /**
     * Sets the name admins see.
     *
     * @param name the name, for example "Reset everyone's homes"
     * @return this action
     */
    public AutomationAction displayName(String name) {
        if (name != null && !name.isBlank()) {
            this.displayName = name.strip();
        }
        return this;
    }

    /**
     * Sets what the action does.
     *
     * @param work the code to run; it runs on a background thread
     * @return this action
     */
    public AutomationAction run(Consumer<ActionContext> work) {
        this.work = Objects.requireNonNull(work, "work");
        return this;
    }

    /**
     * The id rules use, {@code modId:key}.
     *
     * @return the action's id
     */
    public String id() {
        return modId + ":" + key;
    }

    /**
     * The mod's id.
     *
     * @return the mod's id
     */
    public String modId() {
        return modId;
    }

    /**
     * The name admins see.
     *
     * @return the display name
     */
    public String displayName() {
        return displayName;
    }

    /**
     * The code to run.
     *
     * @return the work, or {@code null} if {@link #run(Consumer)} was not called
     */
    public Consumer<ActionContext> work() {
        return work;
    }

    static String checked(String part, String what) {
        String value = part == null ? "" : part.strip().toLowerCase(java.util.Locale.ROOT);
        if (!PART.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be lower case letters, digits, _ or -: " + part);
        }
        return value;
    }
}
