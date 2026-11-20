package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.ProfileError;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One setting a profile may carry: how to read it, what it is called in a message, and what happens
 * when it is not written.
 *
 * <p>Fields are made by the factories in {@link Schema}. A group keeps its own fields, so a caller
 * can walk the tree and list every setting a profile has.
 */
public final class Field {

    /**
     * Turns what a curator wrote into a typed value, or refuses it.
     *
     * <p>A reader refuses a value by throwing, as a rule a {@link ProfileError} that names the path
     * it went wrong at.
     */
    @FunctionalInterface
    public interface Reader {
        /**
         * The typed value of what was written.
         *
         * @param value the written data, which may be anything at all until it is checked
         * @param path where in the profile it was written, such as {@code crop.bleed}
         */
        Object read(Object value, String path);
    }

    private final Reader reader;
    private final String describe;
    private final FieldOptions options;
    private final Map<String, Field> fields;

    Field(Reader reader, String describe, FieldOptions options, Map<String, Field> fields) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.describe = Objects.requireNonNull(describe, "describe");
        this.options = Objects.requireNonNull(options, "options");
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /**
     * The typed value of what was written at the path.
     *
     * @throws ProfileError naming the path when the value will not read, except where a factory in
     *     {@link Schema} says otherwise
     */
    public Object read(Object value, String path) {
        return reader.read(value, path);
    }

    /** What the field holds, in a few words, such as {@code a length} or {@code one of png, pnm}. */
    public String describe() {
        return describe;
    }

    /** True when a group missing this field is refused. */
    public boolean required() {
        return options.required();
    }

    /** The written data a missing field reads as; empty when a missing field is left out. */
    public Optional<Object> fallback() {
        return options.fallback();
    }

    /** The fields of a group, in reading order; empty for a field that is not a group. */
    public Map<String, Field> fields() {
        return fields;
    }

    /** The {@linkplain #describe() description}. */
    @Override
    public String toString() {
        return describe;
    }
}
