package io.github.rkaushikdev.decklewright.cli;

import java.util.Objects;
import java.util.Optional;

/**
 * One option a command declares: what it holds, the letter it also answers to, what the help says
 * about it, what it is when it is not given, and whether every use is collected into a list.
 *
 * <p>The factories make the three kinds and the {@code with} methods add the rest, so a command's
 * table reads as a list of what each option is rather than a list of constructor arguments.
 *
 * @param letter the one letter it also answers to, as {@code -p}; empty for none
 * @param type what it holds
 * @param describe what it does, for the help
 * @param fallback what it is when it is not given: a {@link String} or a {@link Double}; empty for
 *     nothing
 * @param repeat true when every use is collected into a list rather than the last one winning
 * @see Args#parseArgs(java.util.List, java.util.Map)
 */
public record OptionSpec(
        Optional<Character> letter, OptionType type, String describe, Optional<Object> fallback, boolean repeat) {

    /** An option of the given parts. */
    public OptionSpec {
        Objects.requireNonNull(letter, "letter");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(describe, "describe");
        Objects.requireNonNull(fallback, "fallback");
    }

    /** A flag with no letter and no fallback. */
    public static OptionSpec flag(String describe) {
        return new OptionSpec(Optional.empty(), OptionType.BOOLEAN, describe, Optional.empty(), false);
    }

    /** An option holding a word, with no letter and no fallback. */
    public static OptionSpec string(String describe) {
        return new OptionSpec(Optional.empty(), OptionType.STRING, describe, Optional.empty(), false);
    }

    /** An option holding a number, with no letter and no fallback. */
    public static OptionSpec number(String describe) {
        return new OptionSpec(Optional.empty(), OptionType.NUMBER, describe, Optional.empty(), false);
    }

    /** This option, also answering to {@code -letter}. */
    public OptionSpec withLetter(char other) {
        return new OptionSpec(Optional.of(other), type, describe, fallback, repeat);
    }

    /** This option, holding the given word when it is not given. */
    public OptionSpec withFallback(String value) {
        return new OptionSpec(letter, type, describe, Optional.of(value), repeat);
    }

    /** This option, holding the given number when it is not given. */
    public OptionSpec withFallback(double value) {
        return new OptionSpec(letter, type, describe, Optional.of(value), repeat);
    }

    /** This option, collecting every use into a list, which is empty when it is never used. */
    public OptionSpec repeated() {
        return new OptionSpec(letter, type, describe, fallback, true);
    }
}
