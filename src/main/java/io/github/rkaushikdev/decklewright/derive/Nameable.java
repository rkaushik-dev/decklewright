package io.github.rkaushikdev.decklewright.derive;

/**
 * A derivative about to be written, as far as naming it goes: what the profile calls it and the
 * extension of the format it was encoded in. {@link Encoded} is one.
 *
 * @see FileNames#namesFor
 */
public interface Nameable {
    /** The derivative's name in the profile, such as {@code master} or {@code thumb}. */
    String name();

    /** The extension its format takes, including the leading dot. */
    String extension();
}
