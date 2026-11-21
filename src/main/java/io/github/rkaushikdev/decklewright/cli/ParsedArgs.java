package io.github.rkaushikdev.decklewright.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A command line once it has been read against a command's options.
 *
 * <p>The options map holds a {@link String}, a {@link Double} or a {@link Boolean} for each option
 * that was given or has a fallback, and a list of them for an option that repeats. An option that
 * was neither given nor has a fallback is not in the map at all. The typed accessors read it the way
 * a command wants to.
 *
 * @param options what each option came to, in the order first given, then the fallbacks; read only
 * @param positional everything that was not an option, in order
 * @param rest everything after a lone {@code --}, untouched
 */
public record ParsedArgs(Map<String, Object> options, List<String> positional, List<String> rest) {

    /** A reading of the given parts; the map and the lists are copied. */
    public ParsedArgs {
        options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
        positional = List.copyOf(positional);
        rest = List.copyOf(rest);
    }

    /** True when the option was given or has a fallback. */
    public boolean has(String name) {
        return options.containsKey(name);
    }

    /** The word an option came to; empty when it is not there or does not hold a word. */
    public Optional<String> string(String name) {
        return options.get(name) instanceof String text ? Optional.of(text) : Optional.empty();
    }

    /** The number an option came to; empty when it is not there or does not hold a number. */
    public OptionalDouble number(String name) {
        return options.get(name) instanceof Double number ? OptionalDouble.of(number) : OptionalDouble.empty();
    }

    /** True when a flag was given and not written {@code --name=false}. */
    public boolean flag(String name) {
        return Boolean.TRUE.equals(options.get(name));
    }

    /** Every word a repeated option was given, in order; empty when it was never used. */
    public List<String> strings(String name) {
        return options.get(name) instanceof List<?> values
                ? values.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList()
                : List.of();
    }

    /**
     * The positional at an index when there is one and it is not empty, which is how a command tells
     * that it was not told which file or workspace to work on.
     */
    public Optional<String> positionalAt(int index) {
        return index < positional.size()
                ? Optional.of(positional.get(index)).filter(word -> !word.isEmpty())
                : Optional.empty();
    }

    /** This reading with one option set to another value, everything else the same. */
    public ParsedArgs with(String name, Object value) {
        Map<String, Object> changed = new LinkedHashMap<>(options);
        changed.put(name, value);
        return new ParsedArgs(changed, positional, rest);
    }
}
