package net.ofatech.controlcore.domain.ports.config;

import java.util.List;
import net.ofatech.controlcore.domain.model.automation.Rule;

public interface AutomationRuleStore {

    List<Rule> all();

    boolean saveAll(List<Rule> rules);
}
