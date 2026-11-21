package io.github.rkaushikdev.decklewright.cli;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reading a command line.
 *
 * <p>This is a small parser rather than a general one because what a lab actually types is short:
 * a command, a path, and two or three options. The rules are the ones people already expect, and
 * the ones they expect and do not get are the ones that waste an afternoon.
 *
 * <p>What it handles: long options with a value after a space or after an equals sign, short
 * options bundled or not, a lone double dash ending the options, and everything else in order as
 * positionals. What it refuses: an option the command does not declare, and an option that wants a
 * value and did not get one. Both refusals name the option, because a typo in a flag is the most
 * common thing that goes wrong here and the least interesting to diagnose.
 */
public final class Args {

    private static final String END_OF_OPTIONS = "--";

    private Args() {}

    /**
     * An option table in the order given, read only. An entry repeating an earlier name replaces it
     * in place.
     */
    @SafeVarargs
    public static Map<String, OptionSpec> spec(Map.Entry<String, OptionSpec>... entries) {
        return spec(Map.of(), entries);
    }

    /**
     * An option table holding every option of a shared table and then the given ones, read only.
     * An entry repeating a name of the shared table replaces it in place.
     */
    @SafeVarargs
    public static Map<String, OptionSpec> spec(
            Map<String, OptionSpec> shared, Map.Entry<String, OptionSpec>... entries) {
        Map<String, OptionSpec> out = new LinkedHashMap<>(shared);
        for (Map.Entry<String, OptionSpec> entry : entries) {
            out.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * A command line against no options at all, so anything that looks like one is refused.
     *
     * @see #parseArgs(List, Map)
     */
    public static ParsedArgs parseArgs(List<String> argv) {
        return parseArgs(argv, Map.of());
    }

    /**
     * Read a command line against a command's options.
     *
     * <p>A long option takes its value after an equals sign or as the next word, unless the next
     * word is itself a long option other than one starting {@code --no-}; a word starting with one
     * dash is taken as a value, so a negative number reads. A flag is true, or false when written
     * {@code --name=false}. Short letters bundle, and the first letter that takes a value takes the
     * rest of the bundle, or the next word when it is the last. Once everything is read, an option
     * that was not given takes its fallback, and a repeated option nobody used is an empty list.
     *
     * @param argv the words after the command
     * @param spec the command's options, by long name
     * @return the options, the positionals in order, and everything after a lone {@code --}
     * @throws BadArgumentError when two options want the same letter, an option is not declared, an
     *     option that wants a value has none, or a number option's value is not a finite number
     */
    public static ParsedArgs parseArgs(List<String> argv, Map<String, OptionSpec> spec) {
        Objects.requireNonNull(argv, "argv");
        Map<Character, String> shorts = shortNames(spec);
        Reading reading = new Reading(spec);
        List<String> positional = new ArrayList<>();
        List<String> rest = new ArrayList<>();

        for (int i = 0; i < argv.size(); i += 1) {
            String token = argv.get(i);

            if (token.equals(END_OF_OPTIONS)) {
                rest.addAll(argv.subList(i + 1, argv.size()));
                break;
            }

            if (isLong(token)) {
                String body = token.substring(2);
                int at = body.indexOf('=');
                String name = at < 0 ? body : body.substring(0, at);
                OptionSpec one = reading.find(name);

                if (!one.type().takesValue()) {
                    reading.put(name, at < 0 || !body.substring(at + 1).equals("false"));
                    continue;
                }
                if (at >= 0) {
                    reading.put(name, coerce(one, name, body.substring(at + 1)));
                    continue;
                }
                if (i + 1 >= argv.size()
                        || (isLong(argv.get(i + 1)) && !argv.get(i + 1).startsWith("--no-"))) {
                    throw wantsAValue("--" + name);
                }
                reading.put(name, coerce(one, name, argv.get(i + 1)));
                i += 1;
                continue;
            }

            if (isShort(token)) {
                String letters = token.substring(1);
                for (int at = 0; at < letters.length(); at += 1) {
                    char letter = letters.charAt(at);
                    String name = shorts.get(letter);
                    if (name == null) {
                        throw noSuchOption("-" + letter);
                    }
                    OptionSpec one = spec.get(name);
                    if (!one.type().takesValue()) {
                        reading.put(name, true);
                        continue;
                    }
                    String tail = letters.substring(at + 1);
                    if (!tail.isEmpty()) {
                        reading.put(name, coerce(one, name, tail));
                        break;
                    }
                    if (i + 1 >= argv.size()) {
                        throw wantsAValue("-" + letter);
                    }
                    reading.put(name, coerce(one, name, argv.get(i + 1)));
                    i += 1;
                    break;
                }
                continue;
            }

            positional.add(token);
        }

        return new ParsedArgs(reading.finished(), positional, rest);
    }

    /**
     * The help for one command's options, aligned so a column of them reads, such as {@code -p,
     * --profile <string>  which collection profile}, with the fallback in brackets after the
     * description when there is one.
     */
    public static List<String> describeOptions(Map<String, OptionSpec> spec) {
        if (spec.isEmpty()) {
            return List.of();
        }
        List<String> heads = new ArrayList<>();
        for (Map.Entry<String, OptionSpec> entry : spec.entrySet()) {
            OptionSpec one = entry.getValue();
            String letter = one.letter().map(c -> "-" + c + ", ").orElse("    ");
            String value = one.type().takesValue() ? " <" + one.type().label() + ">" : "";
            heads.add(letter + "--" + entry.getKey() + value);
        }
        int width = heads.stream().mapToInt(String::length).max().orElse(0);

        List<String> lines = new ArrayList<>();
        int at = 0;
        for (OptionSpec one : spec.values()) {
            String fallback =
                    one.fallback().map(value -> " (" + text(value) + ")").orElse("");
            lines.add("  " + padEnd(heads.get(at), width) + "  " + one.describe() + fallback);
            at += 1;
        }
        return List.copyOf(lines);
    }

    /** The ones a command needs and did not get, in the order asked about. */
    public static List<String> missingOptions(Map<String, ?> options, List<String> names) {
        return names.stream().filter(name -> !options.containsKey(name)).toList();
    }

    private static boolean isLong(String token) {
        return token.startsWith("--") && token.length() > 2;
    }

    private static boolean isShort(String token) {
        return token.startsWith("-") && token.length() > 1 && !token.startsWith("--");
    }

    private static Map<Character, String> shortNames(Map<String, OptionSpec> spec) {
        Map<Character, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, OptionSpec> entry : spec.entrySet()) {
            if (entry.getValue().letter().isEmpty()) {
                continue;
            }
            char letter = entry.getValue().letter().get();
            String taken = out.get(letter);
            if (taken != null) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("letter", String.valueOf(letter));
                details.put("options", taken + " and " + entry.getKey());
                throw new BadArgumentError("two options want the same letter", details);
            }
            out.put(letter, entry.getKey());
        }
        return out;
    }

    private static Object coerce(OptionSpec spec, String name, String raw) {
        if (spec.type() != OptionType.NUMBER) {
            return raw;
        }
        double value = NumberText.parse(raw);
        if (!Double.isFinite(value)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("option", name);
            details.put("given", raw);
            throw new BadArgumentError("that option wants a number", details);
        }
        return value;
    }

    private static BadArgumentError noSuchOption(String option) {
        return new BadArgumentError("there is no such option", Map.of("option", option));
    }

    private static BadArgumentError wantsAValue(String option) {
        return new BadArgumentError("that option wants a value", Map.of("option", option));
    }

    private static String padEnd(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    private static String text(Object value) {
        return value instanceof Number number ? NumberText.of(number) : String.valueOf(value);
    }

    /** The options read so far, in the order first given, with repeated ones collected. */
    private static final class Reading {
        private final Map<String, OptionSpec> spec;
        private final Map<String, Object> options = new LinkedHashMap<>();
        private final Map<String, List<Object>> repeated = new LinkedHashMap<>();

        Reading(Map<String, OptionSpec> spec) {
            this.spec = spec;
        }

        OptionSpec find(String name) {
            OptionSpec one = spec.get(name);
            if (one == null) {
                throw noSuchOption("--" + name);
            }
            return one;
        }

        void put(String name, Object value) {
            if (spec.get(name).repeat()) {
                repeated.computeIfAbsent(name, key -> {
                            List<Object> list = new ArrayList<>();
                            options.put(key, list);
                            return list;
                        })
                        .add(value);
            } else {
                options.put(name, value);
            }
        }

        Map<String, Object> finished() {
            for (Map.Entry<String, OptionSpec> entry : spec.entrySet()) {
                String name = entry.getKey();
                OptionSpec one = entry.getValue();
                if (!options.containsKey(name) && one.fallback().isPresent()) {
                    options.put(name, one.fallback().get());
                }
                if (one.repeat() && !options.containsKey(name)) {
                    options.put(name, List.of());
                }
            }
            repeated.forEach((name, values) -> options.put(name, List.copyOf(values)));
            return options;
        }
    }
}
