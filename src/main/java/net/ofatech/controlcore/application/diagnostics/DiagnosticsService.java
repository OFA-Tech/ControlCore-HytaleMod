package net.ofatech.controlcore.application.diagnostics;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.ofatech.controlcore.application.mods.ModsEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.diagnostics.DiagnosticsView;
import net.ofatech.controlcore.domain.model.diagnostics.DiagnosticsView.Line;
import net.ofatech.controlcore.domain.model.diagnostics.DiagnosticsView.Section;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.config.ReportWriter;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.ports.system.RecentProblems;
import net.ofatech.controlcore.domain.ports.system.ServerHealth;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class DiagnosticsService {

    static final int PROBLEMS_SHOWN = 10;
    static final double MEMORY_WARNING = 0.85;

    private static final String CONTROLCORE_ID = "OFATech:ControlCore";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?\\b");
    private static final Pattern IPV6 = Pattern.compile("\\[?\\b([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\\b]?(:\\d+)?");
    private static final Pattern SECRET =
            Pattern.compile("(?i)(password|passwd|token|secret|key|dsn)(\\s*[=:]\\s*)(\\S+)");
    private static final Pattern LONG_TOKEN = Pattern.compile("[A-Za-z0-9_\\-+/=.]{40,}");

    private final ServerHealth health;
    private final RecentProblems problems;
    private final ReportWriter reports;
    private final ModsEditor mods;
    private final SettingPermissionPolicy policy;
    private final Texts texts;
    private final Clock clock;
    private final Log log;

    public DiagnosticsService(
            ServerHealth health,
            RecentProblems problems,
            ReportWriter reports,
            ModsEditor mods,
            SettingPermissionPolicy policy,
            Texts texts,
            Clock clock,
            Log log) {
        this.health = health;
        this.problems = problems;
        this.reports = reports;
        this.mods = mods;
        this.policy = policy;
        this.texts = texts;
        this.clock = clock;
        this.log = log;
    }

    public boolean maySee(Optional<PlayerId> viewer) {
        return viewer.isEmpty() || policy.isAdmin(viewer.get());
    }

    public DiagnosticsView viewedBy(PlayerId viewer) {
        Translator tr = texts.forPlayer(Optional.of(viewer));
        Optional<PlayerId> who = Optional.of(viewer);
        return new DiagnosticsView() {
            @Override
            public List<Section> sections() {
                return DiagnosticsService.this.sections(tr);
            }

            @Override
            public Result<String> writeReport() {
                return DiagnosticsService.this.writeReport(who, tr);
            }
        };
    }

    public List<Section> sections(Translator tr) {
        return List.of(server(tr), worlds(tr), mods(tr), problems(tr));
    }

    public Result<String> writeReport(Optional<PlayerId> who, Translator tr) {
        if (!maySee(who)) {
            return Result.failure(Error.forbidden(
                    "Diagnostics.Forbidden",
                    Text.key("controlcore.errors.diagnostics.forbidden", "Only admins can see diagnostics.")));
        }
        Translator english = texts.forServer();
        Instant now = clock.now();
        StringBuilder text = new StringBuilder();
        text.append("ControlCore diagnostics report\n");
        text.append("Written ")
                .append(WHEN.format(now.atZone(ZoneId.systemDefault())))
                .append('\n');
        text.append("Passwords, tokens and IP addresses are removed.\n");
        for (Section section : sections(english)) {
            text.append('\n').append("== ").append(section.title()).append(" ==\n");
            for (Line line : section.lines()) {
                text.append(line.warning() ? "! " : "  ")
                        .append(line.label())
                        .append(": ")
                        .append(line.value())
                        .append('\n');
            }
        }
        String fileName = "report-" + FILE.format(now.atZone(ZoneId.systemDefault())) + ".txt";
        return reports.write(fileName, sanitize(text.toString())).map(path -> {
            log.info("Wrote diagnostics report " + path);
            return tr.t("controlcore.diagnostics.report_written", "path", path);
        });
    }

    static String sanitize(String text) {
        String clean = SECRET.matcher(text).replaceAll("$1$2[hidden]");
        clean = IPV4.matcher(clean).replaceAll("[ip]");
        clean = IPV6.matcher(clean)
                .replaceAll(match -> match.group().contains("::")
                                || match.group().chars().filter(c -> c == ':').count() >= 4
                        ? "[ip]"
                        : match.group());
        return LONG_TOKEN.matcher(clean).replaceAll("[hidden]");
    }

    private Section server(Translator tr) {
        List<Line> lines = new ArrayList<>();
        lines.add(line(tr, "hytale", health.gameVersion(), false));
        lines.add(line(tr, "controlcore", controlCoreVersion(), false));
        ServerHealth.Machine machine = health.machine();
        lines.add(line(tr, "java", machine.java(), false));
        lines.add(line(
                tr,
                "uptime",
                health.bootedAt()
                        .map(boot -> duration(Duration.between(boot, clock.now()), tr))
                        .orElse("?"),
                false));
        lines.add(line(
                tr,
                "players",
                tr.t(
                        "controlcore.diagnostics.value.players",
                        "online",
                        health.playersOnline(),
                        "max",
                        health.maxPlayers()),
                false));
        long used = machine.usedMb();
        long max = machine.maxMb();
        double share = max > 0 ? (double) used / max : 0;
        lines.add(line(
                tr,
                "memory",
                tr.t(
                        "controlcore.diagnostics.value.memory",
                        "used",
                        used,
                        "max",
                        max,
                        "percent",
                        Math.round(share * 100)),
                share >= MEMORY_WARNING));
        String cpu = machine.processCpu() >= 0 && machine.machineCpu() >= 0
                ? tr.t(
                        "controlcore.diagnostics.value.cpu",
                        "server",
                        Math.round(machine.processCpu() * 100),
                        "machine",
                        Math.round(machine.machineCpu() * 100),
                        "cores",
                        machine.cores())
                : tr.t("controlcore.diagnostics.value.cores", "count", machine.cores());
        lines.add(line(tr, "cpu", cpu, false));
        return new Section(tr.t("controlcore.diagnostics.section.server"), lines);
    }

    private Section worlds(Translator tr) {
        List<Line> lines = new ArrayList<>();
        for (ServerHealth.WorldHealth world : health.worlds()) {
            double budget = 1000.0 / Math.max(1, world.targetTps());
            boolean behind = world.tickMillis() > budget;
            String state = !world.ticking()
                    ? tr.t("controlcore.diagnostics.value.not_ticking")
                    : world.paused() ? tr.t("controlcore.diagnostics.value.paused") : "";
            String tick = world.tickMillis() < 0
                    ? ""
                    : tr.t(
                            "controlcore.diagnostics.value.tick",
                            "tick",
                            String.format(Locale.ROOT, "%.1f", world.tickMillis()),
                            "budget",
                            String.format(Locale.ROOT, "%.1f", budget));
            String value = tr.t(
                    "controlcore.diagnostics.value.world",
                    "players",
                    world.players(),
                    "chunks",
                    world.chunks(),
                    "tps",
                    world.targetTps());
            if (!tick.isEmpty()) {
                value += " · " + tick;
            }
            if (behind && world.ticking()) {
                value += " · "
                        + tr.t(
                                "controlcore.diagnostics.value.behind",
                                "tps",
                                Math.max(1, Math.round(1000.0 / world.tickMillis())));
            }
            if (!state.isEmpty()) {
                value += " · " + state;
            }
            lines.add(new Line(world.name(), value, (behind && world.ticking()) || !world.ticking()));
        }
        if (lines.isEmpty()) {
            lines.add(new Line(
                    tr.t("controlcore.diagnostics.label.worlds"), tr.t("controlcore.diagnostics.value.none"), false));
        }
        return new Section(tr.t("controlcore.diagnostics.section.worlds"), lines);
    }

    private Section mods(Translator tr) {
        List<Line> lines = new ArrayList<>();
        List<ModsView.Entry> entries = mods.viewedBy(Optional.empty()).list();
        long running = entries.stream().filter(entry -> entry.mod().running()).count();
        long own = entries.stream().filter(entry -> !entry.mod().core()).count();
        lines.add(line(
                tr,
                "mods",
                tr.t("controlcore.diagnostics.value.mods", "running", running, "total", entries.size(), "own", own),
                false));
        boolean failed = false;
        for (ModsView.Entry entry : entries) {
            if (entry.mod().state() == ModInfo.State.FAILED) {
                failed = true;
                lines.add(new Line(entry.mod().name(), tr.t("controlcore.diagnostics.value.failed"), true));
            }
        }
        if (!failed) {
            lines.add(line(tr, "failed", tr.t("controlcore.diagnostics.value.no_failed"), false));
        }
        return new Section(tr.t("controlcore.diagnostics.section.mods"), lines);
    }

    private Section problems(Translator tr) {
        List<Line> lines = new ArrayList<>();
        List<RecentProblems.Problem> recent = problems.recent();
        for (RecentProblems.Problem problem : recent.subList(0, Math.min(PROBLEMS_SHOWN, recent.size()))) {
            lines.add(new Line(
                    WHEN.format(problem.at().atZone(ZoneId.systemDefault()))
                            + " "
                            + tr.t(
                                    problem.error()
                                            ? "controlcore.diagnostics.value.error"
                                            : "controlcore.diagnostics.value.warning"),
                    sanitize(problem.message()),
                    problem.error()));
        }
        if (lines.isEmpty()) {
            lines.add(line(tr, "problems", tr.t("controlcore.diagnostics.value.no_problems"), false));
        }
        return new Section(tr.t("controlcore.diagnostics.section.problems"), lines);
    }

    private String controlCoreVersion() {
        return mods.viewedBy(Optional.empty()).list().stream()
                .map(ModsView.Entry::mod)
                .filter(mod -> CONTROLCORE_ID.equalsIgnoreCase(mod.id()))
                .map(ModInfo::version)
                .findFirst()
                .orElse("?");
    }

    private static Line line(Translator tr, String key, String value, boolean warning) {
        return new Line(tr.t("controlcore.diagnostics.label." + key), value, warning);
    }

    private static String duration(Duration duration, Translator tr) {
        long minutes = Math.max(0, duration.toMinutes());
        return tr.t(
                "controlcore.diagnostics.value.uptime",
                "days",
                minutes / (60 * 24),
                "hours",
                (minutes / 60) % 24,
                "minutes",
                minutes % 60);
    }
}
