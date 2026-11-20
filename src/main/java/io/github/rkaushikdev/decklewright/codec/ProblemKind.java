package io.github.rkaushikdev.decklewright.codec;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/** What kind of trouble a file in the intake folder is in. */
public enum ProblemKind implements JsonValue {
    /** Nothing recognises the bytes, or the codec that does could not make sense of them. */
    UNREADABLE("unreadable"),
    /** The file stops early, most likely a capture still being written when the watcher saw it. */
    TRUNCATED("truncated"),
    /** A real variant of the format that is not read here, such as a palette png. */
    UNSUPPORTED("unsupported"),
    /** The name's extension says one format and the bytes another; a note, not a failure. */
    WRONG_EXTENSION("wrongExtension"),
    /** The file holds no bytes at all. */
    EMPTY("empty");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(ProblemKind::label).toList();

    private final String label;

    ProblemKind(String label) {
        this.label = label;
    }

    /**
     * The kind a report names.
     *
     * @throws BadArgumentError naming every kind when the value is none of them
     */
    public static ProblemKind parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "kind"))];
    }

    /** The name a report writes, such as {@code wrongExtension}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a kind is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a kind reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
