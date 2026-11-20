package io.github.rkaushikdev.decklewright.manifest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Whether some bytes still match a fixity record.
 *
 * @param ok true only when every digest that could be checked matched
 * @param checked the digests that were compared, by label, in the record's order; copied
 * @param failed the ones that did not match; copied
 * @param reason why it is not ok, in words; empty when it is
 * @see Checksums#verifyAgainst(byte[], java.util.Map)
 */
public record FixityCheck(boolean ok, List<String> checked, List<String> failed, Optional<String> reason) {

    /** A check of the given parts; the lists are copied. */
    public FixityCheck {
        checked = List.copyOf(checked);
        failed = List.copyOf(failed);
        Objects.requireNonNull(reason, "reason");
    }
}
