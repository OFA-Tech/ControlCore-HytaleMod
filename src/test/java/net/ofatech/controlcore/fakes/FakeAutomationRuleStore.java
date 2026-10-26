package net.ofatech.controlcore.fakes;

import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.domain.model.automation.Rule;
import net.ofatech.controlcore.domain.ports.config.AutomationRuleStore;

public final class FakeAutomationRuleStore implements AutomationRuleStore {

    public List<Rule> rules = new ArrayList<>();

    @Override
    public List<Rule> all() {
        return List.copyOf(rules);
    }

    @Override
    public void saveAll(List<Rule> updated) {
        rules = new ArrayList<>(updated);
    }
}
