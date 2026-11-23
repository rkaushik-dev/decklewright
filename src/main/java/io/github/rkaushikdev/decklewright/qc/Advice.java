package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.detect.Side;
import io.github.rkaushikdev.decklewright.qc.checks.DarkCorner;
import io.github.rkaushikdev.decklewright.qc.checks.GrayBalance;
import io.github.rkaushikdev.decklewright.qc.checks.Illumination;
import io.github.rkaushikdev.decklewright.qc.checks.Noise;
import io.github.rkaushikdev.decklewright.qc.checks.PatchTone;
import io.github.rkaushikdev.decklewright.qc.checks.Sharpness;
import io.github.rkaushikdev.decklewright.qc.checks.StripOptions;
import io.github.rkaushikdev.decklewright.qc.checks.ToneResponse;
import io.github.rkaushikdev.decklewright.qc.checks.WedgeSteps;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What to do about a check that fell short, in one table.
 *
 * <p>Every standard check has a remedy, the one line an operator acts on, and most can also say
 * which way the fault goes: which corner is dark, which channel is noisy, which end of the wedge ran
 * together. The two used to be kept in separate tables in separate classes, and a check added to one
 * and not the other came out with a remedy and no direction, or the other way round. Here each
 * check's remedy and its detail sit on one line.
 *
 * <p>A detail reads only the {@link QcContext}, so it says what the check measured, off the same
 * readings, and never more.
 */
public final class Advice {

    /** What a check with no advice of its own is told. */
    public static final String NO_REMEDY = "look at the capture";

    /** The edges in the order {@link Sharpness#edgeStrips} gives their strips. */
    private static final List<String> EDGES = List.of("top", "bottom", "left", "right");

    private static final Map<String, Entry> TABLE = Map.ofEntries(
            entry("sampling", "set the scanner to the frequency the profile asks for", Advice::bothAxes),
            entry("samplingFloor", "capture again at a higher frequency", Advice::bothAxes),
            entry("skew", "square the sheet on the bed"),
            entry("skewAgreement", "look at the capture, the detector was not sure what it found"),
            entry("neatline", "flatten the sheet and keep the target clear of the collar", Advice::missingSides),
            entry("illumination", "move a lamp towards the darker side", Advice::darkSide),
            entry("grayBalance", "white balance the stand against the target", Advice::castWay),
            entry("toneResponse", "set the exposure so the wedge reads its aims", Advice::offAim),
            entry("wedgeSteps", "open the exposure up until every step of the wedge reads apart", Advice::crushedEnd),
            entry("noise", "lower the gain or lengthen the exposure", Advice::noisyChannel),
            entry("clipping", "lower the exposure and capture again"),
            entry("sharpness", "refocus the stand"),
            entry("sharpening", "turn the capture sharpening down"),
            entry("channelRegistration", "stop the lens down or check the back is seated", Advice::fringedEdge));

    private Advice() {}

    /** What an operator changes for a check that fell short, or {@link #NO_REMEDY} for a site's own. */
    public static String remedyFor(String check) {
        Entry entry = TABLE.get(check);
        return entry == null ? NO_REMEDY : entry.remedy();
    }

    /**
     * Which way a short check's fault goes, read off the context the check ran against.
     *
     * @return empty for a check that cannot say, or when what it would read is not in the context
     */
    public static Optional<String> detailFor(String check, QcContext context) {
        Entry entry = TABLE.get(check);
        return entry == null ? Optional.empty() : entry.detail().apply(context);
    }

    /** The names of the checks that have advice of their own, in no order. */
    public static List<String> advised() {
        return List.copyOf(TABLE.keySet());
    }

    /** One check's remedy and the detail beside it. */
    private record Entry(String remedy, Function<QcContext, Optional<String>> detail) {}

    private static Map.Entry<String, Entry> entry(String check, String remedy) {
        return Map.entry(check, new Entry(remedy, context -> Optional.empty()));
    }

    private static Map.Entry<String, Entry> entry(
            String check, String remedy, Function<QcContext, Optional<String>> detail) {
        return Map.entry(check, new Entry(remedy, detail));
    }

    private static Optional<String> darkSide(QcContext context) {
        Optional<Surface> surface = context.surface();
        if (surface.isEmpty()) {
            return Optional.empty();
        }
        DarkCorner corner = Illumination.darkestCorner(
                surface.get(),
                context.bed().orElseGet(surface.get()::bounds),
                context.avoid().orElse(List.of()));
        if (!Double.isFinite(corner.mean())) {
            return Optional.empty();
        }
        return Optional.of("the " + corner.corner() + " is the dark side, " + NumberText.fixed(corner.under() * 100, 1)
                + " percent under the brightest");
    }

    private static Optional<String> castWay(QcContext context) {
        return context.surface()
                .flatMap(surface ->
                        context.target().flatMap(target -> GrayBalance.castDirection(surface, target.patches())))
                .map(cast -> "the cast is " + (cast.amount() > 0 ? "towards" : "away from") + " " + cast.channel());
    }

    private static Optional<String> noisyChannel(QcContext context) {
        return context.surface()
                .filter(surface -> surface.layout().isColour())
                .flatMap(surface ->
                        context.target().flatMap(target -> Patches.lightestFlatPatch(surface, target.patches())))
                .flatMap(Noise::noisiestChannel)
                .filter(read -> Double.isFinite(read.snr()))
                .map(read ->
                        "the noise is worst in " + read.channel() + ", at " + NumberText.fixed(read.snr(), 1) + " dB");
    }

    private static Optional<String> bothAxes(QcContext context) {
        return context.sampling()
                .filter(sampling -> !sampling.isSquare())
                .map(sampling -> "the capture is " + NumberText.fixed(sampling.x(), 1) + " ppi across and "
                        + NumberText.fixed(sampling.y(), 1) + " ppi down, so check the stepper as well");
    }

    private static Optional<String> fringedEdge(QcContext context) {
        if (context.surface().isEmpty() || context.sheet().isEmpty()) {
            return Optional.empty();
        }
        List<Double> shifts = Sharpness.channelShifts(
                context.surface().get(),
                context.sheet().get(),
                StripOptions.defaults().reach());
        if (shifts.size() != EDGES.size()) {
            return Optional.empty();
        }
        int at = 0;
        for (int i = 1; i < shifts.size(); i += 1) {
            if (shifts.get(i) > shifts.get(at)) {
                at = i;
            }
        }
        return Optional.of("the fringe is widest on the " + EDGES.get(at) + " edge of the sheet");
    }

    private static Optional<String> missingSides(QcContext context) {
        return context.neatline()
                .filter(neatline -> !neatline.complete())
                .map(neatline -> "the border was not found on the "
                        + neatline.missing().stream().map(Side::label).collect(Collectors.joining(" or ")));
    }

    /** Which way the worst patch reads from its aim, which says whether to open up or stop down. */
    private static Optional<String> offAim(QcContext context) {
        Optional<List<PatchTone>> tones = tonesOf(context);
        List<Double> aims = context.limits().map(limits -> limits.toneAims()).orElse(List.of());
        if (tones.isEmpty() || aims.size() != tones.get().size() || aims.isEmpty()) {
            return Optional.empty();
        }
        List<Double> read = tones.get().stream().map(PatchTone::tone).toList();
        List<Double> errors = ToneResponse.toneErrors(read, aims);
        int at = 0;
        for (int i = 1; i < errors.size(); i += 1) {
            if (Math.abs(errors.get(i)) > Math.abs(errors.get(at))) {
                at = i;
            }
        }
        boolean light = errors.get(at) > 0;
        return Optional.of("patch " + (at + 1) + " reads " + (light ? "light" : "dark") + ", so "
                + (light ? "stop the exposure down" : "open the exposure up"));
    }

    /** Whether the steps that ran together are in the shadows or the highlights of the wedge. */
    private static Optional<String> crushedEnd(QcContext context) {
        Optional<List<PatchTone>> read = tonesOf(context);
        if (read.isEmpty()) {
            return Optional.empty();
        }
        List<PatchTone> tones = read.get();
        List<Integer> lost = WedgeSteps.lostSteps(tones);
        if (lost.isEmpty()) {
            return Optional.empty();
        }
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (PatchTone tone : tones) {
            low = Math.min(low, tone.tone());
            high = Math.max(high, tone.tone());
        }
        int first = lost.get(0);
        double where = (tones.get(first).tone() + tones.get(first + 1).tone()) / 2;
        return Optional.of(where < (low + high) / 2 ? "the shadows run together" : "the highlights run together");
    }

    private static Optional<List<PatchTone>> tonesOf(QcContext context) {
        if (context.surface().isEmpty() || context.target().isEmpty()) {
            return Optional.empty();
        }
        List<PatchTone> out = new ArrayList<>();
        for (Patch patch : context.target().get().patches()) {
            Optional<PatchTone> tone = ToneResponse.readTone(context.surface().get(), patch);
            if (tone.isEmpty()) {
                return Optional.empty();
            }
            out.add(tone.get());
        }
        return Optional.of(out);
    }
}
