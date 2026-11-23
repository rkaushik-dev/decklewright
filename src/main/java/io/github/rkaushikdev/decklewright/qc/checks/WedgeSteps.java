package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.starsFor;

import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.qc.Check;
import io.github.rkaushikdev.decklewright.qc.Measurement;
import io.github.rkaushikdev.decklewright.qc.Need;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Whether every step of the wedge still reads apart from the next.
 *
 * <p>A capture that crushes its shadows runs the darkest steps of the wedge together, and the same
 * thing happens on the sheet to a hachured hill and the ink line drawn through it. Tone aims catch
 * that only on a profile that writes them down; this works off the wedge alone, so it runs on every
 * capture with a target on the bed.
 *
 * <p>A step is lost when its two patches are closer together than twice their combined noise, or
 * than one sample value, whichever is wider. The noise matters: two steps three values apart are
 * plain on a quiet sensor and a coin toss on a loud one.
 */
public final class WedgeSteps {
    /** How many steps of the wedge may run together, per star. */
    public static final List<Rung> STEPS_LADDER =
            List.of(new Rung(4, 0), new Rung(3, 1), new Rung(2, 2), new Rung(1, 3));

    /** How many combined deviations two neighbouring patches must stand apart by. */
    private static final double APART = 2;

    /** The least two neighbouring patches must stand apart by, in sample values. */
    private static final double LEAST = 1;

    private WedgeSteps() {}

    /**
     * The steps between neighbouring patches that cannot be told apart, by the index of the first
     * patch of each, in order.
     */
    public static List<Integer> lostSteps(List<PatchTone> tones) {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i < tones.size(); i += 1) {
            PatchTone before = tones.get(i - 1);
            PatchTone after = tones.get(i);
            double noise = Math.hypot(before.spread(), after.spread());
            if (Math.abs(after.tone() - before.tone()) < Math.max(LEAST, APART * noise)) {
                out.add(i - 1);
            }
        }
        return List.copyOf(out);
    }

    /**
     * How many steps of the wedge run together, graded on the ladder. The measurement is that count.
     * A wedge with a patch off the capture is not measured.
     */
    public static Check wedgeStepsCheck() {
        return Check.of("wedgeSteps", List.of(Need.SURFACE, Need.TARGET), WedgeSteps::measure);
    }

    private static Measurement measure(QcContext context) {
        Surface surface = context.surface().orElseThrow();
        List<Patch> patches = context.target().orElseThrow().patches();
        if (patches.size() < 2) {
            return new Measurement(Double.NaN, 0, "a wedge of one patch has no steps");
        }
        List<PatchTone> tones = new ArrayList<>(patches.size());
        for (int i = 0; i < patches.size(); i += 1) {
            Optional<PatchTone> read = ToneResponse.readTone(surface, patches.get(i));
            if (read.isEmpty()) {
                return new Measurement(Double.NaN, 0, "patch " + (i + 1) + " of the wedge could not be read");
            }
            tones.add(read.get());
        }

        List<Integer> lost = lostSteps(tones);
        int steps = tones.size() - 1;
        if (lost.isEmpty()) {
            return new Measurement(0, starsFor(0, STEPS_LADDER), "all " + NumberText.of(steps) + " steps read apart");
        }
        int first = lost.get(0) + 1;
        return new Measurement(
                lost.size(),
                starsFor(lost.size(), STEPS_LADDER),
                NumberText.of(lost.size()) + " of " + NumberText.of(steps)
                        + " steps run together, first between patches " + first + " and " + (first + 1));
    }
}
