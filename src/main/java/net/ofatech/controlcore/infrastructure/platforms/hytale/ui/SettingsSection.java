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

/**
 * One list of settings in the control panel (server settings, or the viewer's preferences), without any Hytale
 * types: which rows to show and what each action does. {@link ControlPanelPage} draws it.
 */
final class SettingsSection {

    /** How a setting is edited. */
    enum Kind {
        /** Text field and Save button (text, and numbers without a usable range). */
        TEXT,
        /** Slider, text field and Save button: a whole number with a range. */
        INTEGER,
        /** Slider, text field and Save button: a decimal number with a range. */
        DECIMAL,
        /** Text field with a colour picker that fills it, and a Save button. */
        COLOR,
        /** Items with Remove buttons and a field to add one. */
        LIST,
        /** Check box, saved when clicked. */
        TOGGLE,
        /** Dropdown, saved when an option is picked. */
        CHOICE,
        /** Value shown as text; not editable. */
        INFO
    }

    /** One element of the list, in display order. */
    sealed interface Entry permits Heading, Row {}

    /** A category heading or an explanatory line. */
    record Heading(String text) implements Entry {}

    /**
     * An editable setting.
     *
     * @param color a colour setting shown in this (text) row, see {@code SettingDefinition.coloredBy}
     */
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

    /** @param emptyText shown when there is nothing to list, already in the viewer's language */
    SettingsSection(
            SettingsEditor settings, SettingScope scope, Optional<PlayerId> player, String emptyText, Translator tr) {
        this(settings, scope, player, emptyText, false, tr);
    }

    private SettingsSection(
            SettingsEditor settings,
            SettingScope scope,
            Optional<PlayerId> player,
            String emptyText,
            boolean keys,
            Translator tr) {
        this.settings = settings;
        this.scope = scope;
        this.player = scope == SettingScope.PLAYER ? player : Optional.empty();
        this.emptyText = emptyText;
        this.keys = keys;
        this.tr = tr;
    }

    /**
     * A section of key bindings: the rows are key actions, the values key combinations. {@code PLAYER}: the viewer's
     * own keys; {@code SERVER}: the server's keys for everyone.
     */
    static SettingsSection keymaps(
            SettingsEditor keymaps, SettingScope scope, Optional<PlayerId> player, String emptyText, Translator tr) {
        return new SettingsSection(keymaps, scope, player, emptyText, true, tr);
    }

    /** @return the hint shown in an empty text field */
    String placeholder(SettingSnapshot snapshot) {
        return keys
                ? tr.t("controlcore.panel.keys_hint")
                : tr.translate(snapshot.definition().type().describeText());
    }

    /** @return the row's name in the viewer's language */
    String name(SettingSnapshot snapshot) {
        return tr.translate(snapshot.definition().nameText());
    }

    /** @return the row's category in the viewer's language */
    String category(SettingSnapshot snapshot) {
        return snapshot.definition().heading(tr);
    }

    /** @return a choice's option in the viewer's language */
    String option(SettingSnapshot snapshot, String option) {
        return tr.translate(snapshot.definition().optionText(option));
    }

    /** @return the description line under a row */
    String description(SettingSnapshot snapshot) {
        SettingDefinition<?> definition = snapshot.definition();
        String text = tr.translate(definition.descriptionText()).strip();
        String details;
        if (keys) {
            details = tr.t(
                    scope == SettingScope.SERVER
                            ? "controlcore.panel.keys_details_mod"
                            : "controlcore.panel.keys_details_server",
                    "keys",
                    definition.formattedDefault(),
                    "id",
                    definition.id());
        } else if (snapshot.access() == SettingAccess.READ_ONLY) {
            details = tr.t("controlcore.panel.details_read_only", "id", definition.id());
        } else {
            details = tr.t(
                    "controlcore.panel.details",
                    "type",
                    tr.translate(definition.type().describeText()),
                    "default",
                    shown(definition.formattedDefault(), snapshot),
                    "id",
                    definition.id());
        }
        return text.isBlank() ? details : text + " (" + details + ")";
    }

    SettingScope scope() {
        return scope;
    }

    /** @return headings and rows, grouped by category */
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
            entries.add(new Row(kindOf(snapshot), snapshot, inlineColor(snapshot, byId)));
        }
        return entries;
    }

    /** @return the editable colour setting shown in {@code snapshot}'s (editable text) row, if it has one */
    private static Optional<SettingSnapshot> inlineColor(
            SettingSnapshot snapshot, Map<SettingId, SettingSnapshot> byId) {
        if (kindOf(snapshot) != Kind.TEXT) {
            return Optional.empty();
        }
        return snapshot.definition().colorSetting().map(byId::get).filter(color -> kindOf(color) == Kind.COLOR);
    }

    /** @return the items of a list setting's value */
    static List<String> items(SettingSnapshot snapshot) {
        return snapshot.definition().type() instanceof SettingType.TextListType list
                ? list.parse(snapshot.value()).orElse(List.of())
                : List.of();
    }

    /** Adds {@code item} to a list setting. */
    Result<SettingSnapshot> addItem(SettingSnapshot snapshot, String item) {
        List<String> items = new ArrayList<>(items(snapshot));
        items.add(item == null ? "" : item.strip());
        return save(snapshot.id().toString(), String.join(SettingType.TextListType.SEPARATOR + " ", items));
    }

    /** Removes the item at {@code index} from a list setting. */
    Result<SettingSnapshot> removeItem(SettingSnapshot snapshot, int index) {
        List<String> items = new ArrayList<>(items(snapshot));
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
        return save(snapshot.id().toString(), String.join(SettingType.TextListType.SEPARATOR + " ", items));
    }

    /** Saves text typed into a field or picked from a dropdown. */
    Result<SettingSnapshot> save(String idText, String value) {
        return id(idText).flatMap(id -> settings.write(id, player, value == null ? "" : value));
    }

    /** Saves the state of a check box. */
    Result<SettingSnapshot> check(String idText, Boolean checked) {
        if (checked == null) {
            return Result.failure(Error.validation(
                    "ControlPanel.MissingValue",
                    Text.key("controlcore.errors.controlpanel.missing_value", "The check box sent no value.")));
        }
        return save(idText, checked.toString());
    }

    /** Returns a setting to its default. */
    Result<SettingSnapshot> reset(String idText) {
        return id(idText).flatMap(id -> settings.reset(id, player));
    }

    /** @return the setting's current value as this section shows it */
    Result<SettingSnapshot> current(SettingId id) {
        return settings.read(id, player);
    }

    /** Listens to changes of the settings this section edits (and others; filter with {@link #concerns}). */
    Subscription listen(Consumer<? super SettingChanged> listener) {
        return settings.onAnyChange(listener);
    }

    /** @return whether a change affects a row of this section (for preferences: the viewer's own) */
    boolean concerns(SettingChanged change) {
        if (keys && scope == SettingScope.PLAYER && change.scope() == SettingScope.SERVER) {
            return true; // the server's keys apply to players who did not choose their own
        }
        return change.scope() == scope
                && (scope == SettingScope.SERVER || change.player().equals(player));
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

    /**
     * A number setting's slider: its range and step. The text field next to it takes any valid value; the
     * slider covers the same range in steps.
     */
    record Slider(double min, double max, double step) {

        /** Ranges wider than this get only a text field: a slider could not reach single values. */
        static final double MAX_SPAN = 1_000_000;

        static boolean fits(double min, double max) {
            return Double.isFinite(min) && Double.isFinite(max) && max > min && max - min <= MAX_SPAN;
        }

        /** @return the slider of a {@link Kind#INTEGER} or {@link Kind#DECIMAL} row */
        static Slider of(SettingSnapshot snapshot) {
            return switch (snapshot.definition().type()) {
                case SettingType.IntegerType number -> new Slider(number.min(), number.max(), 1);
                case SettingType.DecimalType number ->
                    new Slider(number.min(), number.max(), niceStep((number.max() - number.min()) / 100));
                default -> throw new IllegalArgumentException("Not a number setting: " + snapshot.id());
            };
        }

        /** @return {@code value} as the slider's value, kept inside the range */
        double position(String value) {
            try {
                return Math.clamp(Double.parseDouble(value.strip()), min, max);
            } catch (NumberFormatException e) {
                return min;
            }
        }

        /** @return the text a slider position saves: whole numbers for integers, else rounded to the step */
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

        /** @return 1, 2 or 5 times a power of ten, at least {@code raw} */
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

    /** @return the status line after a successful change */
    String saved(SettingSnapshot snapshot) {
        String key = snapshot.overridden() ? "controlcore.panel.saved" : "controlcore.panel.saved_default";
        if (snapshot.access() == SettingAccess.APPLIES_AFTER_RESTART) {
            key += "_restart";
        }
        return tr.t(key, "name", name(snapshot), "value", shown(snapshot.value(), snapshot));
    }

    /** @return the value shown in a read-only row */
    String shownValue(SettingSnapshot snapshot) {
        return shown(snapshot.value(), snapshot);
    }

    /** @return the status line after a change made outside this panel */
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

    /** @return {@code error} in the viewer's language */
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
