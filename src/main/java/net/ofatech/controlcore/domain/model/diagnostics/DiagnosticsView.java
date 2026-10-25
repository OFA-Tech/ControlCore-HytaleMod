package net.ofatech.controlcore.domain.model.diagnostics;

import java.util.List;
import net.ofatech.controlcore.domain.result.Result;

public interface DiagnosticsView {

    record Line(String label, String value, boolean warning) {}

    record Section(String title, List<Line> lines) {}

    List<Section> sections();

    Result<String> writeReport();
}
