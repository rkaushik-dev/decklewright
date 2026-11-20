package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.ProfileError;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.measure.Length;
import io.github.rkaushikdev.decklewright.measure.Tolerance;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A very small schema language for collection profiles.
 *
 * <p>A profile is written by a curator in a text file and it has to be read strictly: a misspelled
 * key that is silently ignored is how a collection ends up cropped to the wrong edge for six months.
 * Every field says what it is, every unknown key is an error, and every message names the path it
 * went wrong at.
 *
 * <p>This is not a general validator and does not want to be. It handles the dozen shapes profiles
 * actually use. What a field reads into: a {@link String} for text and choices, a {@link Double} for
 * any {@link Number}, a {@link Boolean}, a {@link Length}, a {@link Tolerance}, {@link Edges}, an
 * unmodifiable list for a list, and an unmodifiable map in field order for a group.
 */
public final class Schema {
    private Schema() {}

    /** A failure at the path, with the path in front of the message and in the details. */
    public static ProfileError fieldError(String path, String message) {
        return fieldError(path, message, Map.of());
    }

    /**
     * A failure at the path, with the path in front of the message and as the first of the details.
     *
     * @param details more context, after the path; a {@code path} entry here replaces the path's value
     */
    public static ProfileError fieldError(String path, String message, Map<String, ?> details) {
        return fieldError(path, message, details, null);
    }

    /** A failure at the path that a foreign error started. */
    static ProfileError fieldError(String path, String message, Map<String, ?> details, Throwable cause) {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("path", path);
        all.putAll(details);
        return new ProfileError(path + ": " + message, all, cause);
    }

    /** A field of the given reader and description, which may be left out and has no fallback. */
    public static Field field(Field.Reader read, String describe) {
        return field(read, describe, FieldOptions.defaults());
    }

    /** A field of the given reader, description and demands. */
    public static Field field(Field.Reader read, String describe, FieldOptions options) {
        return new Field(read, describe, options, Map.of());
    }

    /** A string with at least one character. */
    public static Field text() {
        return text(FieldOptions.defaults());
    }

    /** A string with at least one character. */
    public static Field text(FieldOptions options) {
        return field(
                (value, path) -> {
                    if (!(value instanceof String written) || written.isEmpty()) {
                        throw fieldError(path, "expected a non empty string");
                    }
                    return written;
                },
                "text",
                options);
    }

    /** Any finite number. */
    public static Field number() {
        return number(NumberOptions.defaults());
    }

    /** A finite number inside the bounds, read as a {@link Double} whatever kind of number it was. */
    public static Field number(NumberOptions options) {
        return field(
                (value, path) -> {
                    if (!(value instanceof Number written) || !Double.isFinite(written.doubleValue())) {
                        throw fieldError(path, "expected a number");
                    }
                    double number = written.doubleValue();
                    if (options.integer() && number != Math.rint(number)) {
                        throw fieldError(path, "expected a whole number");
                    }
                    if (options.min().isPresent() && number < options.min().getAsDouble()) {
                        throw fieldError(
                                path,
                                "must be at least "
                                        + NumberText.of(options.min().getAsDouble()));
                    }
                    if (options.max().isPresent() && number > options.max().getAsDouble()) {
                        throw fieldError(
                                path,
                                "must be at most " + NumberText.of(options.max().getAsDouble()));
                    }
                    return number;
                },
                "number",
                options.field());
    }

    /** True or false. */
    public static Field flag() {
        return flag(FieldOptions.defaults());
    }

    /** True or false. */
    public static Field flag(FieldOptions options) {
        return field(
                (value, path) -> {
                    if (!(value instanceof Boolean written)) {
                        throw fieldError(path, "expected true or false");
                    }
                    return written;
                },
                "true or false",
                options);
    }

    /** One of the listed strings. */
    public static Field choice(List<String> allowed) {
        return choice(allowed, FieldOptions.defaults());
    }

    /** One of the listed strings, which a refusal names in order. */
    public static Field choice(List<String> allowed, FieldOptions options) {
        List<String> choices = List.copyOf(allowed);
        String listed = String.join(", ", choices);
        return field(
                (value, path) -> {
                    if (!(value instanceof String written) || !choices.contains(written)) {
                        throw fieldError(path, "expected one of " + listed);
                    }
                    return written;
                },
                "one of " + listed,
                options);
    }

    /** A length in any of the shapes {@link Length#toLength(Object)} reads. */
    public static Field length() {
        return length(FieldOptions.defaults());
    }

    /** A length in any of the shapes {@link Length#toLength(Object)} reads. */
    public static Field length(FieldOptions options) {
        return field(
                (value, path) -> {
                    try {
                        return Length.toLength(value);
                    } catch (DeckleError cause) {
                        throw fieldError(path, "expected a length such as 3mm or 0.25in", causeOf(cause), cause);
                    }
                },
                "a length",
                options);
    }

    /** A tolerance in any of the shapes {@link Tolerance#toTolerance(Object)} reads. */
    public static Field tolerance() {
        return tolerance(FieldOptions.defaults());
    }

    /** A tolerance in any of the shapes {@link Tolerance#toTolerance(Object)} reads. */
    public static Field tolerance(FieldOptions options) {
        return field(
                (value, path) -> {
                    try {
                        return Tolerance.toTolerance(value);
                    } catch (DeckleError cause) {
                        throw fieldError(path, "expected a tolerance such as 0.5 or +2/-1", causeOf(cause), cause);
                    }
                },
                "a tolerance",
                options);
    }

    /** Four lengths, one per edge. */
    public static Field edges() {
        return edges(FieldOptions.defaults());
    }

    /**
     * Four lengths, one per edge, written as one length for every edge or as a bag naming the edges.
     *
     * <p>An edge a bag leaves out is zero millimetres. A value that is neither shape, or a bag naming
     * something that is not an edge, is refused with the path; a length that will not read surfaces
     * with the length's own {@link io.github.rkaushikdev.decklewright.BadArgumentError} instead,
     * whose message says what is wrong with the length itself.
     */
    public static Field edges(FieldOptions options) {
        return field(
                (value, path) -> {
                    if (value instanceof String
                            || value instanceof Length
                            || value instanceof Map<?, ?> pair && pair.containsKey("value")) {
                        return Edges.uniform(Length.toLength(value));
                    }
                    if (!(value instanceof Map<?, ?> bag)) {
                        throw fieldError(path, "expected a length or a bag of four");
                    }
                    for (Object key : bag.keySet()) {
                        if (!Edges.SIDES.contains(key)) {
                            throw fieldError(path + "." + key, "is not an edge");
                        }
                    }
                    return new Edges(edge(bag, "top"), edge(bag, "right"), edge(bag, "bottom"), edge(bag, "left"));
                },
                "a length or one per edge",
                options);
    }

    /** A list whose members each read as the member field. */
    public static Field list(Field member) {
        return list(member, FieldOptions.defaults());
    }

    /** A list whose members each read as the member field, each failure pathed by its index. */
    public static Field list(Field member, FieldOptions options) {
        return field(
                (value, path) -> {
                    if (!(value instanceof List<?> items)) {
                        throw fieldError(path, "expected a list");
                    }
                    List<Object> out = new ArrayList<>(items.size());
                    for (int i = 0; i < items.size(); i += 1) {
                        out.add(member.read(items.get(i), path + "[" + i + "]"));
                    }
                    return Collections.unmodifiableList(out);
                },
                "a list of " + member.describe(),
                options);
    }

    /** A group of named fields. */
    public static Field group(Map<String, Field> fields) {
        return group(fields, FieldOptions.defaults());
    }

    /**
     * A group of named fields, read by {@link #readGroup(Map, Object, String)}. The group keeps its
     * fields so a caller can walk the tree and list every setting a profile has.
     */
    public static Field group(Map<String, Field> fields, FieldOptions options) {
        Map<String, Field> own = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        return new Field((value, path) -> readGroup(own, value, path), "a group", options, own);
    }

    /**
     * Reads a whole document against the fields.
     *
     * @see #readGroup(Map, Object, String)
     */
    public static Map<String, Object> readGroup(Map<String, Field> fields, Object value) {
        return readGroup(fields, value, "");
    }

    /**
     * Reads a group of settings against the fields.
     *
     * <p>A key the fields do not name is refused, with every name they do know in the details as
     * {@code known}. A field that is not written is refused when it is required, read from its
     * fallback when it has one, and left out otherwise.
     *
     * @param path where the group sits, such as {@code crop}; empty for the document itself
     * @return the typed values in field order, in a map nobody can change
     * @throws io.github.rkaushikdev.decklewright.ProfileError naming the path of the first thing wrong
     */
    public static Map<String, Object> readGroup(Map<String, Field> fields, Object value, String path) {
        if (!(value instanceof Map<?, ?> bag)) {
            throw fieldError(path.isEmpty() ? "(root)" : path, "expected a group of settings");
        }

        for (Object key : bag.keySet()) {
            if (!fields.containsKey(key)) {
                throw fieldError(
                        join(path, String.valueOf(key)),
                        "is not a setting this profile understands",
                        Map.of("known", List.copyOf(fields.keySet())));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            String key = entry.getKey();
            Field spec = entry.getValue();
            String here = join(path, key);
            if (!PlainData.has(bag, key)) {
                if (spec.required()) {
                    throw fieldError(here, "is required");
                }
                if (spec.fallback().isPresent()) {
                    out.put(key, spec.read(spec.fallback().get(), here));
                }
                continue;
            }
            out.put(key, spec.read(bag.get(key), here));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Insets holding the figures of the four lengths as they were written, with no conversion: a bag
     * of {@code 1mm} and {@code 2mm} becomes insets of 1 and 2.
     */
    public static Insets edgesToInsets(Edges edges) {
        return new Insets(
                edges.top().value(),
                edges.right().value(),
                edges.bottom().value(),
                edges.left().value());
    }

    private static String join(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private static Length edge(Map<?, ?> bag, String side) {
        return bag.containsKey(side) ? Length.toLength(bag.get(side)) : Length.zero();
    }

    /** The cause as a message quotes an error: its name, then its message. */
    private static Map<String, Object> causeOf(DeckleError cause) {
        return Map.of("cause", cause.name() + ": " + cause.getMessage());
    }
}
