package net.ofatech.controlcore.domain.model.automation;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.result.Result;

public interface AutomationView {

    record Entry(String id, String name, boolean enabled, String summary, String lastRun) {}

    record Form(
            String name,
            String trigger,
            String triggerValue,
            String condition,
            String conditionValue,
            String action,
            String actionValue,
            String action2,
            String action2Value,
            String cooldown) {}

    List<Entry> list();

    boolean globallyOn();

    Optional<Form> form(String id);

    Result<String> save(Optional<String> editing, Form form);

    Result<String> toggle(String id);

    Result<String> test(String id);

    Result<String> remove(String id);

    String hint(String kind, String value);
}
