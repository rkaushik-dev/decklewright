package io.github.rkaushikdev.decklewright.codec;

import java.util.List;

/**
 * An intake folder split into what can be worked and what cannot.
 *
 * @param good the files that came out {@linkplain Inspection#ok() ok}, in the order they were given
 * @param bad every other file, in the order they were given
 * @see Inspections#triage(List)
 */
public record Triage(List<Inspection> good, List<Inspection> bad) {
    /** A triage of the given parts; the lists are copied. */
    public Triage {
        good = List.copyOf(good);
        bad = List.copyOf(bad);
    }
}
