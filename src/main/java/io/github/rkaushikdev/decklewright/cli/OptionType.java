package io.github.rkaushikdev.decklewright.cli;

/**
 * What an option on the command line holds: a word, a number, or nothing at all, in which case
 * being there is the whole of what it says.
 */
public enum OptionType {
    /** A value kept as it was typed. */
    STRING("string"),
    /** A value read as a number, refused when it is not a finite one. */
    NUMBER("number"),
    /** A flag: true when it is there, unless it was written {@code --name=false}. */
    BOOLEAN("boolean");

    private final String label;

    OptionType(String label) {
        this.label = label;
    }

    /** The name the help prints after an option that takes a value, such as {@code string}. */
    public String label() {
        return label;
    }

    /** True for the types that take a value after the option. */
    public boolean takesValue() {
        return this != BOOLEAN;
    }

    /** The {@linkplain #label() label}. */
    @Override
    public String toString() {
        return label;
    }
}
