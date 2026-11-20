package io.github.rkaushikdev.decklewright.codec.png;

import java.util.List;

/**
 * Whether a file matches a chunk policy, and which chunks broke it.
 *
 * @param ok true when nothing is unexpected and nothing is missing
 * @param unexpected the chunk types present that the policy does not allow, sorted
 * @param missing which of {@code IHDR}, {@code IDAT} and {@code IEND} the file lacks, in that order
 * @see Rewriting#checkChunkPolicy(byte[], List)
 */
public record PolicyCheck(boolean ok, List<String> unexpected, List<String> missing) {
    /** A check of the given parts; the lists are copied. */
    public PolicyCheck {
        unexpected = List.copyOf(unexpected);
        missing = List.copyOf(missing);
    }
}
