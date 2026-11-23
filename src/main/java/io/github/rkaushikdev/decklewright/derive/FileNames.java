package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.Settings;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the files are called.
 *
 * <p>A name is the only part of a delivery that a person types, reads out over a phone and pastes
 * into a spreadsheet, so it is worth more care than its size suggests. The rules here are the ones a
 * state archive already lives with: nothing that a case insensitive filesystem will collide, nothing
 * that needs quoting in a shell, and nothing that a thirty year old catalogue import will choke on.
 *
 * <p>The pattern itself comes off the profile, because every collection was named by a different
 * person in a different decade and none of them will be renamed now.
 */
public final class FileNames {

    /** Names Windows will not let a file have, whatever the extension. */
    public static final List<String> RESERVED =
            List.of("con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "lpt1", "lpt2", "lpt3", "lpt4");

    /** How long a single name may be, before the extension. */
    public static final int MAX_STEM = 96;

    /** What a slug's separator is when the caller does not choose one. */
    private static final char SEPARATOR = '-';

    /** How much longer than a stem a whole name, extension included, may be before it is too long. */
    private static final int EXTENSION_ROOM = 8;

    /** Characters a name may keep. Every run of anything else becomes one separator. */
    private static final Pattern KEEP = Pattern.compile("[^a-zA-Z0-9._-]+");

    /** The combining diacritical marks a compatibility decomposition splits off a letter. */
    private static final Pattern ACCENTS = Pattern.compile("[\\u0300-\\u036f]");

    /** The separators a stem that had to be cut short may not end in. */
    private static final Pattern TRAILING_SEPARATORS = Pattern.compile("[-._]+\\z");

    /** A final extension: a dot and letters or digits, in either case, at the very end. */
    private static final Pattern EXTENSION = Pattern.compile("\\.[a-z0-9]+\\z", Pattern.CASE_INSENSITIVE);

    /** What a placeholder matches when a name is read back: the shortest run of name characters. */
    private static final String PLACEHOLDER_VALUE = "([a-zA-Z0-9._-]+?)";

    private FileNames() {}

    /**
     * Folds anything into something that will survive being a file name, with {@code -} between
     * the parts.
     *
     * @see #slug(String, char)
     */
    public static String slug(String value) {
        return slug(value, SEPARATOR);
    }

    /**
     * Folds anything into something that will survive being a file name.
     *
     * <p>Accents come off after a compatibility decomposition, and every run of characters a name may
     * not keep becomes one separator. The separator run is collapsed rather than kept, because an
     * accession typed with two spaces and one typed with one are the same accession, and two files
     * whose names differ only in that are a problem nobody will diagnose. The result never starts or
     * ends with the separator, and may be empty.
     *
     * @param separator what goes between the parts, taken literally
     */
    public static String slug(String value, char separator) {
        String text = String.valueOf(separator);
        String replacement = Matcher.quoteReplacement(text);
        String one = "(?:" + Pattern.quote(text) + ")";

        String unaccented = ACCENTS.matcher(Normalizer.normalize(value, Normalizer.Form.NFKD))
                .replaceAll("");
        String folded = KEEP.matcher(unaccented).replaceAll(replacement);
        String trimmed =
                Pattern.compile("^" + one + "+|" + one + "+\\z").matcher(folded).replaceAll("");
        return Pattern.compile(one + "{2,}").matcher(trimmed).replaceAll(replacement);
    }

    /**
     * A stem a filesystem will accept, at most {@link #MAX_STEM} characters long.
     *
     * @see #safeStem(String, int)
     */
    public static String safeStem(String value) {
        return safeStem(value, MAX_STEM);
    }

    /**
     * A stem a filesystem will accept: the slug, cut to the maximum length without a separator left
     * at the end of the cut. Reserved names get a trailing underscore rather than being refused,
     * because an accession really can be called {@code aux} and refusing it helps nobody.
     *
     * @param max the longest the stem may be
     * @throws BadArgumentError when nothing of the value survives the slug
     */
    public static String safeStem(String value, int max) {
        String base = slug(value);
        if (base.isEmpty()) {
            throw new BadArgumentError("there is nothing left of that name", Map.of("value", value));
        }
        String cut = base.length() > max
                ? TRAILING_SEPARATORS.matcher(base.substring(0, max)).replaceFirst("")
                : base;
        return RESERVED.contains(cut.toLowerCase(Locale.ROOT)) ? cut + "_" : cut;
    }

    /**
     * The name one derivative gets, with no placeholders beyond the accession and the derivative.
     *
     * @see #nameFor(Profile, String, String, String, Map)
     */
    public static String nameFor(Profile profile, String accession, String derivative, String extension) {
        return nameFor(profile, accession, derivative, extension, Map.of());
    }

    /**
     * The name one derivative gets: the profile's naming pattern filled with the accession and the
     * derivative name, each made a safe stem first, and the whole made a safe stem again. An
     * accession written as a map call number also fills {@code class}, {@code cutter} and {@code
     * year}, as {@link CallNumber#placeholders()} reads them, so a pattern such as {@code
     * {class}-{year}_{derivative}} files a sheet the way the map room shelves it.
     *
     * <p>A placeholder nobody filled is left in the name, so a typo in a pattern shows up in the file
     * name instead of quietly giving two files the same one.
     *
     * @param profile the resolved profile, whose naming pattern is used
     * @param extension appended as given, including the leading dot
     * @param extra more placeholders, a number written as reports write one; these win over the
     *     accession and the derivative when they share a name
     * @throws BadArgumentError when the accession or the derivative is empty, or nothing of a value
     *     survives being made a stem
     */
    public static String nameFor(
            Profile profile, String accession, String derivative, String extension, Map<String, ?> extra) {
        Guards.requireNonEmptyString(accession, "accession");
        Guards.requireNonEmptyString(derivative, "derivative");

        return safeStem(Settings.fillName(profile, placeholdersFor(accession, derivative, extra))) + extension;
    }

    /**
     * Every value a naming pattern can be filled with for one derivative: the call number's parts
     * when the accession is one, then the accession and the derivative made safe stems, then the
     * extra values, each later one winning over an earlier one of the same name.
     *
     * @return in a new map the caller may keep, in that order
     */
    public static Map<String, Object> placeholdersFor(String accession, String derivative, Map<String, ?> extra) {
        Map<String, Object> values = new LinkedHashMap<>();
        CallNumber.read(accession).ifPresent(call -> values.putAll(call.placeholders()));
        values.put("accession", safeStem(accession));
        values.put("derivative", safeStem(derivative));
        values.putAll(extra);
        return values;
    }

    /**
     * Every name a set of encoded derivatives will be written under.
     *
     * @see #namesFor(Profile, String, List, Map)
     */
    public static Map<String, String> namesFor(Profile profile, String accession, List<? extends Nameable> encoded) {
        return namesFor(profile, accession, encoded, Map.of());
    }

    /**
     * Every name a set of encoded derivatives will be written under, each with the extension of the
     * format it was written in.
     *
     * @param extra more placeholders, as for {@link #nameFor(Profile, String, String, String, Map)}
     * @return derivative name to file name, read only, in the order of the set
     */
    public static Map<String, String> namesFor(
            Profile profile, String accession, List<? extends Nameable> encoded, Map<String, ?> extra) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Nameable item : encoded) {
            out.put(item.name(), nameFor(profile, accession, item.name(), item.extension(), extra));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * The names that differ only in case, which is one file on the filesystems half of this material
     * will end up on.
     *
     * @return each group that would collide, in the order its first name appears, with the names in
     *     the group sorted by their UTF-16 code units
     */
    public static List<List<String>> collisions(List<String> names) {
        Map<String, List<String>> byFold = new LinkedHashMap<>();
        for (String name : names) {
            byFold.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>())
                    .add(name);
        }
        return byFold.values().stream()
                .filter(group -> group.size() > 1)
                .map(group -> group.stream().sorted().toList())
                .toList();
    }

    /**
     * Everything wrong with a set of names, before anything is written: names that collide, names
     * too long for a stem and an extension, and names with no extension.
     *
     * @param names derivative name to file name
     * @return what is wrong, collisions first; empty when the set is clean
     */
    public static List<String> objectionsToNames(Map<String, String> names) {
        if (names.isEmpty()) {
            return List.of("nothing was named");
        }
        List<String> out = new ArrayList<>();
        for (List<String> group : collisions(List.copyOf(names.values()))) {
            out.add(String.join(" and ", group) + " differ only in case");
        }
        names.forEach((derivative, name) -> {
            if (name.length() > MAX_STEM + EXTENSION_ROOM) {
                out.add("the name for " + derivative + " is too long");
            }
            if (!EXTENSION.matcher(name).find()) {
                out.add("the name for " + derivative + " has no extension");
            }
        });
        return List.copyOf(out);
    }

    /**
     * Pulls the placeholder values back out of a name written under the profile's pattern. This is
     * what an intake run uses when a folder arrives already named and nobody kept the spreadsheet.
     *
     * <p>The final extension, if any, is ignored. Each placeholder takes the shortest run of name
     * characters that lets the rest of the pattern match, so an accession with a separator in it
     * splits at its first one.
     *
     * @return placeholder name to value, read only, in the order the pattern first names them; empty
     *     when the name was not written under this pattern
     */
    public static Optional<Map<String, String>> parseName(String name, Profile profile) {
        String stem = EXTENSION.matcher(name).replaceFirst("");
        List<String> keys = new ArrayList<>();
        StringBuilder expression = new StringBuilder();
        Matcher placeholders = Settings.PLACEHOLDER.matcher(profile.naming().pattern());
        int literalStart = 0;
        while (placeholders.find()) {
            appendLiteral(expression, profile.naming().pattern().substring(literalStart, placeholders.start()));
            expression.append(PLACEHOLDER_VALUE);
            keys.add(placeholders.group(1));
            literalStart = placeholders.end();
        }
        appendLiteral(expression, profile.naming().pattern().substring(literalStart));

        Matcher match = Pattern.compile(expression.toString()).matcher(stem);
        if (!match.matches()) {
            return Optional.empty();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i += 1) {
            out.put(keys.get(i), match.group(i + 1));
        }
        return Optional.of(Collections.unmodifiableMap(out));
    }

    private static void appendLiteral(StringBuilder expression, String literal) {
        if (!literal.isEmpty()) {
            expression.append(Pattern.quote(literal));
        }
    }
}
