package io.github.rkaushikdev.decklewright.qc.checks;

import io.github.rkaushikdev.decklewright.QcError;
import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.profile.QcLimits;
import io.github.rkaushikdev.decklewright.qc.Check;
import io.github.rkaushikdev.decklewright.qc.Measurement;
import io.github.rkaushikdev.decklewright.qc.Need;
import io.github.rkaushikdev.decklewright.qc.Patches;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.raster.ChannelStats;
import io.github.rkaushikdev.decklewright.raster.Stats;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.SurfaceView;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Tone response, read off the step wedge against the values each patch should come back as.
 *
 * <p>Gray balance says whether a patch is neutral. It cannot say whether the patch is the right
 * gray: a capture a stop over reads every patch neutral and every patch too light, and the curve a
 * viewer draws from it flattens the pale tints a surveyor used for woodland. The guidelines grade
 * that with aim values per patch, so a profile that knows its wedge writes them down.
 *
 * <p>The wedge goes on the bed whichever way round the operator laid it, so the aims are read in
 * whichever order matches the way the patches step. It is the worst patch that counts.
 */
public final class ToneResponse {
    /** The largest difference between a patch and its aim, in sample values, per star. */
    public static final List<Rung> TONE_LADDER =
            List.of(new Rung(4, 3), new Rung(3, 6), new Rung(2, 10), new Rung(1, 16));

    private ToneResponse() {}

    /**
     * How far each patch reads from its aim, in the order the patches were read. The aims are taken
     * the other way round when the patches step the other way from them, which is a wedge laid down
     * upside down.
     *
     * @param tones each patch's tone, in the order read
     * @param aims what each should read, lightest or darkest first
     * @return the signed difference per patch, the tone less its aim
     * @throws QcError when there are not as many aims as patches
     */
    public static List<Double> toneErrors(List<Double> tones, List<Double> aims) {
        if (tones.size() != aims.size()) {
            throw new QcError(
                    "the wedge has " + tones.size() + " patches and the profile aims " + aims.size(),
                    Map.of("patches", tones.size(), "aims", aims.size()));
        }
        boolean flipped = tones.size() > 1 && rising(tones) != rising(aims);
        List<Double> out = new ArrayList<>(tones.size());
        for (int i = 0; i < tones.size(); i += 1) {
            double aim = aims.get(flipped ? aims.size() - 1 - i : i);
            out.add(tones.get(i) - aim);
        }
        return List.copyOf(out);
    }

    /**
     * The tone of one patch: the mean of its colour channels over its inner part, so a cast moves it
     * no more than it moves the lightness a viewer sees.
     *
     * @return in sample values; empty when nothing of the patch is on the capture
     */
    public static OptionalDouble toneOf(Surface surface, Patch patch) {
        return readTone(surface, patch)
                .map(read -> OptionalDouble.of(read.tone()))
                .orElseGet(OptionalDouble::empty);
    }

    /**
     * The tone of one patch and how much it varies, both over the inner part of the patch, both the
     * mean over its colour channels and both on the eight bit scale whatever the capture's depth.
     *
     * @return empty when nothing of the patch is on the capture
     */
    public static Optional<PatchTone> readTone(Surface surface, Patch patch) {
        Optional<SurfaceView> view = Patches.patchView(surface, patch.rect());
        if (view.isEmpty()) {
            return Optional.empty();
        }
        int colours = surface.layout().colourCount();
        double tone = 0;
        double spread = 0;
        for (int c = 0; c < colours; c += 1) {
            ChannelStats stats = Stats.channelStats(view.get(), c);
            tone += Patches.onEightBitScale(stats.mean(), surface);
            spread += Patches.onEightBitScale(stats.stdDev(), surface);
        }
        return Optional.of(new PatchTone(tone / colours, spread / colours));
    }

    /**
     * The worst patch of the wedge against the profile's aims, held to its tone tolerance. The
     * measurement is in sample values. A profile with no aims has nothing to measure against, and
     * the check declines rather than guessing.
     */
    public static Check toneResponseCheck() {
        return Check.of("toneResponse", List.of(Need.SURFACE, Need.TARGET, Need.LIMITS), ToneResponse::measure);
    }

    private static Measurement measure(QcContext context) {
        QcLimits limits = context.limits().orElseThrow();
        if (limits.toneAims().isEmpty()) {
            throw new QcError("the profile sets no tone aims", Map.of());
        }
        Surface surface = context.surface().orElseThrow();
        List<Patch> patches = context.target().orElseThrow().patches();
        List<Double> tones = new ArrayList<>(patches.size());
        for (int i = 0; i < patches.size(); i += 1) {
            OptionalDouble tone = toneOf(surface, patches.get(i));
            if (tone.isEmpty()) {
                return new Measurement(Double.NaN, 0, "patch " + (i + 1) + " of the wedge could not be read");
            }
            tones.add(tone.getAsDouble());
        }

        List<Double> errors = toneErrors(tones, limits.toneAims());
        int at = 0;
        for (int i = 1; i < errors.size(); i += 1) {
            if (Math.abs(errors.get(i)) > Math.abs(errors.get(at))) {
                at = i;
            }
        }
        double worst = Math.abs(errors.get(at));
        double allowed = limits.toneTolerance();
        int stars = Check.starsWithin(worst, allowed, TONE_LADDER, false);
        String way = errors.get(at) > 0 ? "light" : "dark";
        return new Measurement(
                worst,
                stars,
                "patch " + (at + 1) + " reads " + NumberText.fixed(worst, 1) + " " + way + " of its aim across "
                        + NumberText.of(errors.size()) + " patches, against " + NumberText.of(allowed));
    }

    private static boolean rising(List<Double> values) {
        return values.get(values.size() - 1) >= values.get(0);
    }
}
