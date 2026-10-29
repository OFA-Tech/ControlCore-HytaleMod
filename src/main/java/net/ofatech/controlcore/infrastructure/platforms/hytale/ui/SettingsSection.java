package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.serverconfig.WorldStatus;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

final class SettingsSection {

    enum Kind {
        KEYS,
        TEXT,
        INTEGER,
        DECIMAL,
        COLOR,
        LIST,
        TOGGLE,
        CHOICE,
        INFO
    }

    sealed interface Entry permits Heading, Row {}

    record Heading(String text) implements Entry {}

    record Row(Kind kind, SettingSnapshot snapshot, Optional<SettingSnapshot> color) implements Entry {

        Row(Kind kind, SettingSnapshot snapshot) {
            this(kind, snapshot, Optional.empty());
        }

        SettingId id() {
            return snapshot.id();
        }
    }

    private final SettingsEditor settings;
    private final SettingScope scope;
    private final Optional<PlayerId> player;
    private final String emptyText;
    private final boolean keys;
    private final Translator tr;
    private final boolean allKeys;

    SettingsSection(
            SettingsEditor settings, SettingScope scope, Optional<PlayerId> player, String emptyText, Translator tr) {
        this(settings, scope, player, emptyText, false, tr, false);
    }

    private SettingsSection(
            SettingsEditor settings,
            SettingScope scope,
            Optional<PlayerId> player,
            String emptyText,
            boolean keys,
            Translator tr,
            boolean allKeys) {
        this.settings = settings;
        this.scope = scope;
        this.player = scope == SettingScope.PLAYER ? player : Optional.empty();
        this.emptyText = emptyText;
        this.keys = keys;
        this.tr = tr;
        this.allKeys = allKeys;
    }

    static SettingsSection keymaps(
            SettingsEditor keymaps,
            SettingScope scope,
            Optional<PlayerId> player,
            String emptyText,
            Translator tr,
            boolean allKeys) {
        return new SettingsSection(keymaps, scope, player, emptyText, true, tr, allKeys);
    }

    String placeholder(SettingSnapshot snapshot) {
        return tr.translate(snapshot.definition().type().describeText());
    }

    String name(SettingSnapshot snapshot) {
        return tr.translate(snapshot.definition().nameText());
    }

    String category(SettingSnapshot snapshot) {
        return snapshot.definition().heading(tr);
    }

    String option(SettingSnapshot snapshot, String option) {
        return tr.translate(snapshot.definition().optionText(option));
    }

    String description(SettingSnapshot snapshot) {
        SettingDefinition<?> definition = snapshot.definition();
        String text = tr.translate(definition.descriptionText()).strip();
        if (keys) {
            String rest = KeyPicker.read(snapshot.value()).rest();
            if (!rest.isEmpty()) {
                text = text + (text.isBlank() ? "" : " · ") + tr.t("controlcore.panel.keypick.also", "keys", rest);
            }
        }
        String details = snapshot.access() == SettingAccess.READ_ONLY
                ? tr.t("controlcore.panel.row_read_only")
                : tr.t(
                        "controlcore.panel.row_default",
                        "value",
                        keys ? definition.formattedDefault() : shown(definition.formattedDefault(), snapshot));
        return text.isBlank() ? details : text + " · " + details;
    }

    SettingScope scope() {
        return scope;
    }

    List<Entry> entries() {
        List<SettingSnapshot> snapshots = settings.snapshot(scope, player);
        List<Entry> entries = new ArrayList<>();
        if (snapshots.isEmpty()) {
            entries.add(new Heading(emptyText));
            return entries;
        }
        Map<SettingId, SettingSnapshot> byId = new HashMap<>();
        snapshots.forEach(snapshot -> byId.put(snapshot.id(), snapshot));
        Set<SettingId> shownInline = new HashSet<>();
        snapshots.forEach(snapshot -> inlineColor(snapshot, byId).ifPresent(color -> shownInline.add(color.id())));
        String category = null;
        for (SettingSnapshot snapshot : snapshots) {
            if (shownInline.contains(snapshot.id())) {
                continue;
            }
            if (!snapshot.definition().heading().equals(category)) {
                category = snapshot.definition().heading();
                entries.add(new Heading(category(snapshot)));
            }
            entries.add(new Row(kind(snapshot), snapshot, inlineColor(snapshot, byId)));
        }
        return entries;
    }

    private static Optional<SettingSnapshot> inlineColor(
            SettingSnapshot snapshot, Map<SettingId, SettingSnapshot> byId) {
        if (kindOf(snapshot) != Kind.TEXT) {
            return Optional.empty();
        }
        return snapshot.definition().colorSetting().map(byId::get).filter(color -> kindOf(color) == Kind.COLOR);
    }

    static List<String> items(SettingSnapshot snapshot) {
        return snapshot.definition().type() instanceof SettingType.TextListType list
                ? list.parse(snapshot.value()).orElse(List.of())
                : List.of();
    }

    Result<SettingSnapshot> addItem(SettingSnapshot snapshot, String item) {
        List<String> items = new ArrayList<>(items(snapshot));
        items.add(item == null ? "" : item.strip());
        return save(snapshot.id().toString(), String.join(SettingType.TextListType.SEPARATOR + " ", items));
    }

    Result<SettingSnapshot> removeItem(SettingSnapshot snapshot, int index) {
        List<String> items = new ArrayList<>(items(snapshot));
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
        return save(snapshot.id().toString(), String.join(SettingType.TextListType.SEPARATOR + " ", items));
    }

    Result<SettingSnapshot> save(String idText, String value) {
        return id(idText).flatMap(id -> settings.write(id, player, value == null ? "" : value));
    }

    Result<SettingSnapshot> check(String idText, Boolean checked) {
        if (checked == null) {
            return Result.failure(Error.validation(
                    "ControlPanel.MissingValue",
                    Text.key("controlcore.errors.controlpanel.missing_value", "The check box sent no value.")));
        }
        return save(idText, checked.toString());
    }

    Result<SettingSnapshot> reset(String idText) {
        return id(idText).flatMap(id -> settings.reset(id, player));
    }

    Result<SettingSnapshot> current(SettingId id) {
        return settings.read(id, player);
    }

    Subscription listen(Consumer<? super SettingChanged> listener) {
        return settings.onAnyChange(listener);
    }

    boolean concerns(SettingChanged change) {
        boolean serverKeysAreThePlayersDefaults =
                keys && scope == SettingScope.PLAYER && change.scope() == SettingScope.SERVER;
        if (serverKeysAreThePlayersDefaults) {
            return true;
        }
        return change.scope() == scope
                && (scope == SettingScope.SERVER || change.player().equals(player));
    }

    Kind kind(SettingSnapshot snapshot) {
        Kind kind = kindOf(snapshot);
        return keys && kind == Kind.TEXT ? Kind.KEYS : kind;
    }

    static Kind kindOf(SettingSnapshot snapshot) {
        if (snapshot.access() == SettingAccess.READ_ONLY) {
            return Kind.INFO;
        }
        return switch (snapshot.definition().type()) {
            case SettingType.BooleanType _ -> Kind.TOGGLE;
            case SettingType.ChoiceType _ -> Kind.CHOICE;
            case SettingType.ColorType _ -> Kind.COLOR;
            case SettingType.TextListType _ -> Kind.LIST;
            case SettingType.IntegerType number when Slider.fits(number.min(), number.max()) -> Kind.INTEGER;
            case SettingType.DecimalType number when Slider.fits(number.min(), number.max()) -> Kind.DECIMAL;
            default -> Kind.TEXT;
        };
    }

    record Slider(double min, double max, double step) {

        static final double MAX_SPAN = 1_000_000;

        static boolean fits(double min, double max) {
            return Double.isFinite(min) && Double.isFinite(max) && max > min && max - min <= MAX_SPAN;
        }

        static Slider of(SettingSnapshot snapshot) {
            return switch (snapshot.definition().type()) {
                case SettingType.IntegerType number -> new Slider(number.min(), number.max(), 1);
                case SettingType.DecimalType number ->
                    new Slider(number.min(), number.max(), niceStep((number.max() - number.min()) / 100));
                default -> throw new IllegalArgumentException("Not a number setting: " + snapshot.id());
            };
        }

        double position(String value) {
            try {
                return Math.clamp(Double.parseDouble(value.strip()), min, max);
            } catch (NumberFormatException e) {
                return min;
            }
        }

        String text(double position, boolean whole) {
            double value = Math.clamp(position, min, max);
            if (whole) {
                return Long.toString(Math.round(value));
            }
            double stepped = Math.round(value / step) * step;
            return java.math.BigDecimal.valueOf(Math.clamp(stepped, min, max))
                    .setScale(decimals(step), java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        }

        static double niceStep(double raw) {
            if (!(raw > 0)) {
                return 0.01;
            }
            double power = Math.pow(10, Math.floor(Math.log10(raw)));
            double scaled = raw / power;
            double nice = scaled <= 1 ? 1 : scaled <= 2 ? 2 : scaled <= 5 ? 5 : 10;
            return nice * power;
        }

        private static int decimals(double step) {
            return Math.max(
                    0, java.math.BigDecimal.valueOf(step).stripTrailingZeros().scale());
        }
    }

    String saved(SettingSnapshot snapshot) {
        String key = snapshot.overridden() ? "controlcore.panel.saved" : "controlcore.panel.saved_default";
        if (snapshot.access() == SettingAccess.APPLIES_AFTER_RESTART) {
            key += "_restart";
        }
        return tr.t(key, "name", name(snapshot), "value", shown(snapshot.value(), snapshot));
    }

    String shownValue(SettingSnapshot snapshot) {
        return WorldStatus.decode(snapshot.value())
                .map(status -> status.describe(tr))
                .orElseGet(() -> shown(snapshot.value(), snapshot));
    }

    String changedElsewhere(SettingSnapshot snapshot) {
        return tr.t(
                snapshot.overridden()
                        ? "controlcore.panel.changed_elsewhere"
                        : "controlcore.panel.changed_elsewhere_default",
                "name",
                name(snapshot),
                "value",
                shown(snapshot.value(), snapshot));
    }

    Translator translator() {
        return tr;
    }

    boolean allKeys() {
        return allKeys;
    }

    String error(Error error) {
        return tr.error(error);
    }

    static List<String> options(SettingSnapshot snapshot) {
        return snapshot.definition().type() instanceof SettingType.ChoiceType choice ? choice.options() : List.of();
    }

    private String shown(String value, SettingSnapshot snapshot) {
        if (value.isEmpty()) {
            return tr.t("controlcore.settings.empty");
        }
        return snapshot.definition().type() instanceof SettingType.ChoiceType ? option(snapshot, value) : value;
    }

    private static Result<SettingId> id(String text) {
        return SettingId.parse(text);
    }
}
