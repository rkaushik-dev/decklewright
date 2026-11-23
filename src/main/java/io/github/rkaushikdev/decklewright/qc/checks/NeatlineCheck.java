package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.starsFor;

import io.github.rkaushikdev.decklewright.detect.Neatline;
import io.github.rkaushikdev.decklewright.detect.Side;
import io.github.rkaushikdev.decklewright.qc.Check;
import io.github.rkaushikdev.decklewright.qc.Measurement;
import io.github.rkaushikdev.decklewright.qc.Need;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Whether the ruled border of a sheet was found all the way round.
 *
 * <p>A quadrangle is cropped to its neatline and a strip of collar. Where a side of the line is not
 * found the planner falls back to the paper edge on that side, so the cut keeps a ragged strip of
 * margin the others do not, and the legend on the bottom collar can lose its top line. The crop
 * still settles and nothing else in the grading notices. This check grades how much of the border
 * the crop really stood on.
 */
public final class NeatlineCheck {
    /** How many sides of the border may be missing, per star. */
    public static final List<Rung> NEATLINE_LADDER = List.of(new Rung(4, 0), new Rung(2, 1), new Rung(1, 2));

    private NeatlineCheck() {}

    /**
     * How many sides of the neatline were not found, graded on the ladder. The measurement is that
     * count. It needs only the neatline, and a capture that was never searched for one declines.
     */
    public static Check neatlineCheck() {
        return Check.of("neatline", List.of(Need.NEATLINE), NeatlineCheck::measure);
    }

    private static Measurement measure(QcContext context) {
        Neatline found = context.neatline().orElseThrow();
        List<Side> missing = found.missing();
        if (missing.isEmpty()) {
            return new Measurement(0, starsFor(0, NEATLINE_LADDER), "the border was found on all four sides");
        }
        String sides = missing.stream().map(Side::label).collect(Collectors.joining(" and "));
        return new Measurement(
                missing.size(),
                starsFor(missing.size(), NEATLINE_LADDER),
                "no border on the " + sides + ", " + NumberText.of(missing.size())
                        + (missing.size() == 1 ? " side" : " sides") + " cut to the paper edge");
    }
}
