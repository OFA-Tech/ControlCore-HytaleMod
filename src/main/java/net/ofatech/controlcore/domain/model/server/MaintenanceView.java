package net.ofatech.controlcore.domain.model.server;

import java.util.List;
import net.ofatech.controlcore.domain.result.Result;

public interface MaintenanceView {

    record Choice(String id, String label) {}

    boolean on();

    String status();

    String message();

    String motd();

    List<Choice> offAfterChoices();

    Result<String> turnOn(String message, String motd, String offAfter);

    Result<String> turnOff();
}
