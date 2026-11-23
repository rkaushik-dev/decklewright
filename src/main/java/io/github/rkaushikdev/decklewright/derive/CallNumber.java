package io.github.rkaushikdev.decklewright.derive;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Library of Congress class G call number, as the map room writes one into an accession.
 *
 * <p>Atlases and sheet maps are shelved under class G from G3160 to G9999, and the accession the
 * lab gives a capture is the call number with its spaces made separators: {@code g4181-1907} is
 * G4181 for 1907, and {@code g3701.s5-1864} carries the subject Cutter S5 as well. Reading it back
 * lets a profile name files by the parts a curator files them under.
 *
 * @param classNumber the class number with its letter, lower cased, such as {@code g4181}
 * @param cutter the area or subject Cutter, lower cased, such as {@code s5}; empty when there is none
 * @param year the situation date, a year from 1500 to 2099; empty when the accession gives none
 */
public record CallNumber(String classNumber, Optional<String> cutter, OptionalInt year) {

    /** The first and last class numbers of the G schedule that hold maps and atlases. */
    private static final int FIRST_MAP_CLASS = 3160;

    private static final int LAST_MAP_CLASS = 9999;

    /** G and four digits, then a Cutter and a year, each after a separator and each optional. */
    private static final Pattern WRITTEN = Pattern.compile(
            "g(\\d{4})(?:[._ -]([a-z][0-9]+))?(?:[._ -]((?:1[5-9]|20)\\d\\d))?(?:[._ -].*)?", Pattern.CASE_INSENSITIVE);

    /** A call number of the given parts. */
    public CallNumber {
        Objects.requireNonNull(classNumber, "classNumber");
        Objects.requireNonNull(cutter, "cutter");
        Objects.requireNonNull(year, "year");
    }

    /**
     * The call number an accession is written as.
     *
     * @return empty when the accession does not open with a map class number, such as one the lab
     *     made up for a loose sheet with no call number yet
     */
    public static Optional<CallNumber> read(String accession) {
        Matcher match = WRITTEN.matcher(accession.strip());
        if (!match.matches()) {
            return Optional.empty();
        }
        int number = Integer.parseInt(match.group(1));
        if (number < FIRST_MAP_CLASS || number > LAST_MAP_CLASS) {
            return Optional.empty();
        }
        return Optional.of(new CallNumber(
                "g" + match.group(1),
                Optional.ofNullable(match.group(2)).map(text -> text.toLowerCase(Locale.ROOT)),
                match.group(3) == null ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(match.group(3)))));
    }

    /**
     * What a naming pattern can take from the call number: {@code class} always, {@code cutter}
     * and {@code year} when the accession gave them, in that order.
     */
    public Map<String, Object> placeholders() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("class", classNumber);
        cutter.ifPresent(value -> out.put("cutter", value));
        year.ifPresent(value -> out.put("year", String.valueOf(value)));
        return out;
    }
}
