package io.github.rkaushikdev.decklewright.measure;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import io.github.rkaushikdev.decklewright.util.Whitespace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converting, reading and writing physical lengths, and the nominal sheet sizes the lab quotes.
 *
 * <p>Every conversion goes through the inch, because sampling frequency is quoted per inch. Pixels
 * are not a {@link Unit}: a pixel has no length until a {@link Sampling} says what it was taken at.
 */
public final class Units {
    /** Every unit this tree understands, in the order a report lists them. */
    public static final List<Unit> UNITS = List.of(Unit.values());

    /**
     * The nominal sizes the lab quotes on a work order, by name, in the order they are matched.
     * Anything outside this table is entered as an explicit measurement instead.
     */
    public static final Map<String, PhysicalSize> SHEET_SIZES = sheetSizes();

    /** A figure, a run of optional blank space, then a unit, with optional blank space around both. */
    private static final Pattern LENGTH = Pattern.compile(Whitespace.CLASS + "*([+-]?(?:\\d+\\.?\\d*|\\.\\d+))"
            + Whitespace.CLASS + "*([a-zA-Z]+)" + Whitespace.CLASS + "*");

    private Units() {}

    /** True when the value is a unit or the label of one, such as {@code mm}. Labels are case sensitive. */
    public static boolean isUnit(Object value) {
        return value instanceof Unit || Unit.isLabel(value);
    }

    /**
     * The value in inches.
     *
     * @throws BadArgumentError when the value is not finite
     */
    public static double toInches(double value, Unit unit) {
        Guards.requireFinite(value, "value");
        return value * unit.inchesPer();
    }

    /**
     * A figure in inches, in the given unit.
     *
     * @throws BadArgumentError when the figure is not finite
     */
    public static double fromInches(double inches, Unit unit) {
        Guards.requireFinite(inches, "inches");
        return inches / unit.inchesPer();
    }

    /**
     * The value converted from one unit to another, by way of the inch. A value already in the
     * wanted unit comes back exactly as it was.
     *
     * @throws BadArgumentError when the value is not finite
     */
    public static double convert(double value, Unit from, Unit to) {
        if (from == to) {
            return Guards.requireFinite(value, "value");
        }
        return fromInches(toInches(value, from), to);
    }

    /**
     * Reads what an operator types into a profile, which is a number then a unit with optional
     * space, such as {@code 12.5mm}, {@code .5 in} or {@code 17IN}. A bare number is an error, not an
     * assumed millimetre.
     *
     * @throws BadArgumentError when the text is empty, is not a number and a unit, or names a unit
     *     this tree does not know
     */
    public static Length parseLength(String text) {
        Guards.requireNonEmptyString(text, "text");
        Matcher match = LENGTH.matcher(text);
        if (!match.matches()) {
            throw new BadArgumentError("cannot read " + text + " as a length", Map.of("text", text));
        }
        double value = Double.parseDouble(match.group(1));
        Unit unit = Unit.parse(match.group(2).toLowerCase(Locale.ROOT));
        return new Length(value, unit);
    }

    /** The value rounded and written to the report precision of its unit, such as {@code 431.80 mm}. */
    public static String formatLength(double value, Unit unit) {
        return formatLength(value, unit, unit.reportPlaces());
    }

    /**
     * The value rounded half even to the given places and written with exactly that many, such as
     * {@code 432 mm} for no places. A negative count rounds to tens, hundreds and so on, and writes
     * no places.
     */
    public static String formatLength(double value, Unit unit, int places) {
        return NumberText.fixed(Num.roundTo(value, places), Math.max(0, places)) + " " + unit.label();
    }

    /** The nominal size of the given name, in inches. */
    public static PhysicalSize sheetSize(String name) {
        return sheetSize(name, Unit.INCH);
    }

    /**
     * The nominal size of the given name, in the given unit.
     *
     * @throws BadArgumentError when the name is empty, or is not in {@link #SHEET_SIZES}, in which
     *     case the details list every name that is as {@code known}
     */
    public static PhysicalSize sheetSize(String name, Unit unit) {
        Guards.requireNonEmptyString(name, "name");
        PhysicalSize entry = SHEET_SIZES.get(name);
        if (entry == null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("name", name);
            details.put("known", new ArrayList<>(SHEET_SIZES.keySet()));
            throw new BadArgumentError("no nominal size called " + name, details);
        }
        Unit from = entry.unit();
        return new PhysicalSize(convert(entry.width(), from, unit), convert(entry.height(), from, unit), unit);
    }

    private static Map<String, PhysicalSize> sheetSizes() {
        Map<String, PhysicalSize> table = new LinkedHashMap<>();
        table.put("quad75", new PhysicalSize(22, 27, Unit.INCH));
        table.put("quad15", new PhysicalSize(21, 27, Unit.INCH));
        table.put("atlasPlate", new PhysicalSize(17, 14, Unit.INCH));
        table.put("platBook", new PhysicalSize(14, 17, Unit.INCH));
        table.put("archD", new PhysicalSize(24, 36, Unit.INCH));
        table.put("archE", new PhysicalSize(36, 48, Unit.INCH));
        return Collections.unmodifiableMap(table);
    }
}
