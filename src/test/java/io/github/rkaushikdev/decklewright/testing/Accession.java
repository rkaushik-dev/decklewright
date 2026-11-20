package io.github.rkaushikdev.decklewright.testing;

import io.github.rkaushikdev.decklewright.derive.Derivatives;
import io.github.rkaushikdev.decklewright.derive.DerivedFile;
import io.github.rkaushikdev.decklewright.derive.DerivedSet;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.manifest.Deliveries;
import io.github.rkaushikdev.decklewright.manifest.DeliveryOptions;
import io.github.rkaushikdev.decklewright.manifest.FinishedRun;
import io.github.rkaushikdev.decklewright.manifest.Payload;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.plan.CropPlanner;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.profile.Settings;
import io.github.rkaushikdev.decklewright.qc.QcInput;
import io.github.rkaushikdev.decklewright.qc.QcResult;
import io.github.rkaushikdev.decklewright.qc.QcRunner;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.Num;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A whole accession, worked the way the pipeline works one: read, planned, derived, graded and,
 * for {@link #delivery}, packed into a delivery ready to be written.
 *
 * <p>Every test that needs a delivery gets it from here, so when the order of the steps changes,
 * the tests that are supposed to notice are the ones about the order, not every test that happens
 * to need a delivery.
 */
public final class Accession {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();

    private Accession() {}

    /** An upright capture off the stand, with the wedge and the scale bar beside the sheet. */
    public static Surface capture() {
        return capture(0, true);
    }

    /** A capture with the sheet turned by the given angle, wedge and scale bar included. */
    public static Surface capture(double degrees) {
        return capture(degrees, true);
    }

    /**
     * A 300 by 240 colour capture with a 160 by 112 sheet centred at (170, 130), turned by the given
     * angle, an eight step wedge above it when asked for, and a scale bar below it.
     */
    public static Surface capture(double degrees, boolean wedge) {
        Surface surface = Bench.bench(Bench.Options.defaults()
                .withWidth(300)
                .withHeight(240)
                .withDegrees(degrees)
                .withHalfWidth(80)
                .withHalfHeight(56)
                .withCentre(170, 130)
                .withLayout(Layout.RGB));
        if (wedge) {
            for (int i = 0; i < 8; i += 1) {
                Bench.paint(surface, Rect.of(14 + i * 10, 12, 10, 14), 20 + Num.roundHalfUp((i / 7.0) * 210));
            }
        }
        Bench.paint(surface, Rect.of(20, 220, 120, 7), 190);
        return surface;
    }

    /** The default accession, worked. */
    public static Worked worked() {
        return worked(Options.defaults());
    }

    /** Read, plan, derive and grade one, and hand back every piece. */
    public static Worked worked(Options options) {
        Surface surface = capture(options.degrees());
        Profile profile = REGISTRY.get(options.profile());
        CaptureReading reading = Captures.readCapture(surface);
        Sampling sampling = Sampling.of(options.ppi());
        CropPlan plan = CropPlanner.planCrop(reading, profile, surface.bounds(), sampling)
                .plan();

        DerivedSet derived =
                Derivatives.deriveFromPlan(surface, plan, profile, options.id(), Settings.derivativeRecipes(profile));

        Optional<QcResult> quality = options.graded()
                ? Optional.of(QcRunner.runQc(QcInput.of(surface, reading, profile)
                        .withSampling(sampling)
                        .withCrop(plan.rect())))
                : Optional.empty();

        return new Worked(
                options.id(),
                profile,
                surface,
                reading,
                plan,
                derived.files(),
                derived.steps(),
                derived.bytes(),
                quality,
                plan.warnings());
    }

    /** The default accession, packed into a delivery. */
    public static Payload delivery() {
        return delivery(Options.defaults());
    }

    /** The accession, worked and packed into a delivery under the options' version and time. */
    public static Payload delivery(Options options) {
        return Deliveries.deliveryFor(
                worked(options).run(),
                DeliveryOptions.defaults().withVersion(options.version()).withAt(options.at()));
    }

    /**
     * Which accession to work, and how.
     *
     * @param id the accession
     * @param profile the built in profile it is worked under
     * @param degrees how far the sheet is turned on the bed
     * @param ppi the frequency the capture was taken at
     * @param graded whether the quality checks are run
     * @param version what a delivery records as the agent's version
     * @param at when a delivery says it was made
     */
    public record Options(
            String id, String profile, double degrees, double ppi, boolean graded, String version, Instant at) {

        /** Options of the given parts. */
        public Options {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(at, "at");
        }

        /**
         * Accession {@code g4181-1907} under the house profile, upright, at 400 ppi and graded,
         * delivered as version {@code 1.4.0} at noon on 18 December 2025.
         */
        public static Options defaults() {
            return new Options("g4181-1907", "house", 0, 400, true, "1.4.0", Instant.parse("2025-12-18T12:00:00Z"));
        }

        /** These options for another accession. */
        public Options withId(String other) {
            return new Options(other, profile, degrees, ppi, graded, version, at);
        }

        /** These options under another profile. */
        public Options withProfile(String other) {
            return new Options(id, other, degrees, ppi, graded, version, at);
        }

        /** These options with the sheet turned. */
        public Options withDegrees(double other) {
            return new Options(id, profile, other, ppi, graded, version, at);
        }

        /** These options at another frequency. */
        public Options withPpi(double other) {
            return new Options(id, profile, degrees, other, graded, version, at);
        }

        /** These options with or without the quality checks. */
        public Options withGraded(boolean other) {
            return new Options(id, profile, degrees, ppi, other, version, at);
        }

        /** These options delivered as another version. */
        public Options withVersion(String other) {
            return new Options(id, profile, degrees, ppi, graded, other, at);
        }

        /** These options delivered at another time. */
        public Options withAt(Instant other) {
            return new Options(id, profile, degrees, ppi, graded, version, other);
        }
    }

    /**
     * Every piece of a worked accession.
     *
     * @param accession what it is of
     * @param profile the resolved profile
     * @param surface the capture
     * @param reading what was read off it
     * @param plan the crop
     * @param files the derivatives
     * @param steps what deriving them did
     * @param bytes the size of every file together
     * @param quality the grading, when it was graded
     * @param warnings the plan's warnings
     */
    public record Worked(
            String accession,
            Profile profile,
            Surface surface,
            CaptureReading reading,
            CropPlan plan,
            List<DerivedFile> files,
            List<String> steps,
            long bytes,
            Optional<QcResult> quality,
            List<String> warnings) {

        /** A worked accession of the given parts; the lists are copied. */
        public Worked {
            files = List.copyOf(files);
            steps = List.copyOf(steps);
            warnings = List.copyOf(warnings);
        }

        /** What the manifest records of it. */
        public FinishedRun run() {
            return new FinishedRun(accession, profile, surface, reading, plan, files, quality, warnings);
        }
    }
}
