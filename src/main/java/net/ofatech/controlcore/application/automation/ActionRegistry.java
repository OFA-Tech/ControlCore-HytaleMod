package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.ofatech.controlcore.api.ActionsApi;
import net.ofatech.controlcore.api.AutomationAction;
import net.ofatech.controlcore.domain.model.PlayerId;

@AutoService(ActionsApi.class)
public final class ActionRegistry implements ActionsApi {

    public record Fired(String trigger, Optional<PlayerId> player, Map<String, String> variables) {}

    private final Map<String, AutomationAction> actions = new ConcurrentHashMap<>();
    private final Map<String, String> triggers = new ConcurrentHashMap<>();
    private volatile BiConsumer<String, Fired> listener = (id, fired) -> {};

    @Override
    public void register(AutomationAction action) {
        if (action == null || action.work() == null) {
            throw new IllegalArgumentException("An automation action needs code to run (call run(...))");
        }
        actions.put(action.id(), action);
    }

    @Override
    public void unregister(String id) {
        if (id != null) {
            actions.remove(id.strip().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public Collection<AutomationAction> actions() {
        return List.copyOf(actions.values());
    }

    @Override
    public void registerTrigger(String modId, String key, String displayName) {
        String id = AutomationAction.of(modId, key).id();
        triggers.put(id, displayName == null || displayName.isBlank() ? id : displayName.strip());
    }

    @Override
    public void fire(String modId, String key, Optional<PlayerId> player, Map<String, String> variables) {
        String id = AutomationAction.of(modId, key).id();
        listener.accept(id, new Fired(id, player == null ? Optional.empty() : player, Map.copyOf(variables)));
    }

    @Override
    public Map<String, String> triggers() {
        return new TreeMap<>(triggers);
    }

    public Optional<AutomationAction> action(String id) {
        return Optional.ofNullable(actions.get(id.strip().toLowerCase(Locale.ROOT)));
    }

    void listen(BiConsumer<String, Fired> onFire) {
        this.listener = onFire;
    }
}
