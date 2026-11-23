package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.starsFor;

import io.github.rkaushikdev.decklewright.qc.Check;
import io.github.rkaushikdev.decklewright.qc.Measurement;
import io.github.rkaushikdev.decklewright.qc.Need;
import io.github.rkaushikdev.decklewright.qc.Patches;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.raster.Histogram;
import io.github.rkaushikdev.decklewright.raster.Raster;
import io.github.rkaushikdev.decklewright.raster.Stats;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.SurfaceView;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.List;
import java.util.Optional;

/**
 * Noise and clipping.
 *
 * <p>Noise is measured on a patch that should be flat, because a measure of variation over
 * something that genuinely varies says nothing. The wedge is the only flat thing in the frame that
 * is not the bed, and the bed is out because the board has texture.
 *
 * <p>Clipping is measured on the whole crop, because a sheet with a burnt out highlight has lost
 * that part of the record and it does not matter where.
 */
public final class Noise {
    /** Signal to noise in decibels, per star. Higher is better here. */
    public static final List<Rung> NOISE_LADDER =
            List.of(new Rung(4, 40), new Rung(3, 30), new Rung(2, 24), new Rung(1, 18));

    /** Share of samples at either end of the range, per star. */
    public static final List<Rung> CLIPPING_LADDER =
            List.of(new Rung(4, 0.001), new Rung(3, 0.005), new Rung(2, 0.02), new Rung(1, 0.05));

    /** What a perfectly flat patch is recorded as, since its signal to noise has no finite value. */
    private static final double UNMEASURABLE_SNR = 99;

    private Noise() {}

    /**
     * Signal to noise on the lightest flat patch of the target, read on every colour channel and held
     * to the profile's floor on the noisiest of them. The measurement is in decibels, and 99 for a
     * patch with no variation at all; the note names the channel on a colour capture.
     */
    public static Check noiseCheck() {
        return Check.of("noise", List.of(Need.SURFACE, Need.TARGET, Need.LIMITS), Noise::measureNoise);
    }

    /**
     * The largest share of samples sitting at either end of the range, over every channel of the
     * crop, or of the whole capture when there is no crop, held to the profile's maximum. The
     * measurement is that share.
     */
    public static Check clippingCheck() {
        return Check.of("clipping", List.of(Need.SURFACE, Need.LIMITS), Noise::measureClipping);
    }

    /**
     * The colour channel of a flat patch with the lowest signal to noise, which is the one a sensor
     * is weakest in: on the stands here it is nearly always blue, because the lamps are warm and the
     * blue photosites are amplified hardest to make up for it. An alpha channel is not read.
     *
     * @return the noisiest channel, the earlier one on a tie; empty when no channel gives a reading
     */
    public static Optional<ChannelNoise> noisiestChannel(Raster patch) {
        List<String> names = patch.layout().channelNames();
        ChannelNoise worst = null;
        for (int channel = 0; channel < patch.layout().colourCount(); channel += 1) {
            double snr = Stats.signalToNoise(patch, channel);
            if (Double.isNaN(snr)) {
                continue;
            }
            if (worst == null || snr < worst.snr()) {
                worst = new ChannelNoise(names.get(channel), snr);
            }
        }
        return Optional.ofNullable(worst);
    }

    private static Measurement measureNoise(QcContext context) {
        Surface surface = context.surface().orElseThrow();
        Optional<ChannelNoise> read = Patches.lightestFlatPatch(
                        surface, context.target().orElseThrow().patches())
                .flatMap(Noise::noisiestChannel);
        if (read.isEmpty()) {
            return new Measurement(Double.NaN, 0, "no patch of the target is usable for noise");
        }

        double snr = read.get().snr();
        double allowed = context.limits().orElseThrow().minimumSnr();
        int stars = snr >= allowed ? starsFor(snr, NOISE_LADDER, true) : 0;
        boolean finite = Double.isFinite(snr);
        String where =
                surface.layout().isColour() && finite ? " in " + read.get().channel() : "";
        return new Measurement(
                finite ? snr : UNMEASURABLE_SNR,
                stars,
                (finite ? NumberText.fixed(snr, 1) : "no measurable") + " dB" + where + " against a floor of "
                        + NumberText.of(allowed));
    }

    private static Measurement measureClipping(QcContext context) {
        Surface surface = context.surface().orElseThrow();
        Raster region = context.crop()
                .<Raster>map(crop -> new SurfaceView(surface, crop.intersect(surface.bounds())))
                .orElse(surface);

        double worst = 0;
        String where = "nothing";
        for (int channel = 0; channel < region.channels(); channel += 1) {
            Histogram.Clipping clipping = Histogram.histogramOf(region, channel).clipping();
            if (clipping.low() > worst) {
                worst = clipping.low();
                where = "the shadows";
            }
            if (clipping.high() > worst) {
                worst = clipping.high();
                where = "the highlights";
            }
        }

        double allowed = context.limits().orElseThrow().maximumClipping();
        int stars = worst <= allowed ? starsFor(worst, CLIPPING_LADDER) : 0;
        String note = worst == 0
                ? "nothing is clipped"
                : NumberText.fixed(worst * 100, 3) + " percent clipped in " + where + ", against "
                        + NumberText.fixed(allowed * 100, 3);
        return new Measurement(worst, stars, note);
    }
}
