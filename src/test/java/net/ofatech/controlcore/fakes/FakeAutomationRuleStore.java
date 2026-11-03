package net.ofatech.controlcore.fakes;

import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.domain.model.automation.Rule;
import net.ofatech.controlcore.domain.ports.config.AutomationRuleStore;

public final class FakeAutomationRuleStore implements AutomationRuleStore {

    public List<Rule> rules = new ArrayList<>();
    public boolean failWrites;

    @Override
    public List<Rule> all() {
        return List.copyOf(rules);
    }

    @Override
    public boolean saveAll(List<Rule> updated) {
        if (failWrites) {
            return false;
        }
        rules = new ArrayList<>(updated);
        return true;
    }
}
