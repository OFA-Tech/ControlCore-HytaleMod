package net.ofatech.controlcore.domain.model.server;

import java.util.List;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.result.Result;

public interface RestartView {

    record Choice(String id, String label) {}

    boolean counting();

    String status();

    List<Choice> delays();

    Result<String> restart(String delay);

    Result<String> cancel();

    Subscription onChange(Runnable listener);
}
