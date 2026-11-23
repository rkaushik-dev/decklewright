package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.profile.Problem;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.Validation;
import io.github.rkaushikdev.decklewright.qc.checks.GrayBalance;
import io.github.rkaushikdev.decklewright.qc.checks.Illumination;
import io.github.rkaushikdev.decklewright.qc.checks.Noise;
import io.github.rkaushikdev.decklewright.qc.checks.SamplingAndSkew;
import io.github.rkaushikdev.decklewright.qc.checks.Sharpness;
import io.github.rkaushikdev.decklewright.qc.checks.ToneResponse;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.ToDoubleFunction;

/**
 * The profile's limits set beside the ladders the checks grade on.
 *
 * <p>A profile limit and a ladder answer different questions. The limit says how bad a capture may
 * be before it gets no stars at all; the ladder says how many stars it earns inside that. A curator
 * who loosens a limit past the rung for the level the collection asks for has written a profile in
 * which a capture can sit inside every limit and still never reach the level, and the run log says
 * nothing about why. The profile layer cannot see the ladders, so the cross check lives here.
 */
public final class Ladders {

    /** Every profile limit that sits over a ladder, in the order the profile writes them. */
    public static final List<LadderedLimit> LADDERED = List.of(
            new LadderedLimit(
                    "skew", "qc.skewTolerance", SamplingAndSkew.SKEW_LADDER, false, Profile.Qc::skewTolerance),
            new LadderedLimit(
                    "illumination",
                    "qc.illuminationTolerance",
                    Illumination.ILLUMINATION_LADDER,
                    false,
                    Profile.Qc::illuminationTolerance),
            new LadderedLimit(
                    "grayBalance",
                    "qc.grayBalanceTolerance",
                    GrayBalance.GRAY_BALANCE_LADDER,
                    false,
                    Profile.Qc::grayBalanceTolerance),
            new LadderedLimit("noise", "qc.minimumSnr", Noise.NOISE_LADDER, true, Profile.Qc::minimumSnr),
            new LadderedLimit(
                    "clipping", "qc.maximumClipping", Noise.CLIPPING_LADDER, false, Profile.Qc::maximumClipping),
            new LadderedLimit(
                    "toneResponse", "qc.toneTolerance", ToneResponse.TONE_LADDER, false, Profile.Qc::toneTolerance),
            new LadderedLimit(
                    "channelRegistration",
                    "qc.registrationTolerance",
                    Sharpness.REGISTRATION_LADDER,
                    false,
                    Profile.Qc::registrationTolerance));

    /** The checks a grayscale capture always passes, since it has one channel. */
    private static final List<String> COLOUR_ONLY = List.of("grayBalance", "channelRegistration");

    private Ladders() {}

    /**
     * The limits a profile sets looser than the rung for the level it asks for, each as a note: a
     * capture can pass them and still never reach the level. The tone tolerance is only read when
     * the profile aims its wedge, since the check does not run otherwise, and the gray balance and
     * registration tolerances are not read for a collection whose every derivative is gray, since a
     * grayscale capture has one channel.
     */
    public static List<Problem> unreachableLevels(Profile profile) {
        Profile.Qc qc = profile.qc();
        int wanted = qc.minimumStars();
        List<Problem> out = new ArrayList<>();
        for (LadderedLimit limit : LADDERED) {
            if (limit.check().equals("toneResponse") && qc.toneAims().isEmpty()
                    || grayOnly(profile) && COLOUR_ONLY.contains(limit.check())) {
                continue;
            }
            OptionalDouble rung = Check.limitAt(limit.ladder(), wanted);
            double written = limit.read().applyAsDouble(qc);
            if (rung.isPresent() && limit.looser(written, rung.getAsDouble())) {
                out.add(Validation.note(
                        limit.path(),
                        "is looser than " + Levels.levelName(wanted) + " allows, which is "
                                + NumberText.of(rung.getAsDouble()) + ", so a capture can pass it and miss the level"));
            }
        }
        return List.copyOf(out);
    }

    /**
     * Each laddered limit of a profile in a line, with the ladder beside it and the rung the level
     * asked for, such as {@code skew: qc.skewTolerance 0.15, rungs 0.1 0.3 0.6 1.5, four stars at
     * 0.1, looser}. The rungs run from four stars down. A limit looser than its rung ends {@code
     * looser}, one at it or tighter ends {@code within}.
     */
    public static List<String> describeLadders(Profile profile) {
        Profile.Qc qc = profile.qc();
        int wanted = qc.minimumStars();
        List<String> lines = new ArrayList<>();
        for (LadderedLimit limit : LADDERED) {
            double written = limit.read().applyAsDouble(qc);
            List<String> rungs = limit.ladder().stream()
                    .map(rung -> NumberText.of(rung.limit()))
                    .toList();
            String line = limit.check() + ": " + limit.path() + " " + NumberText.of(written) + ", rungs "
                    + String.join(" ", rungs);
            OptionalDouble rung = Check.limitAt(limit.ladder(), wanted);
            if (rung.isPresent()) {
                line += ", " + Levels.levelName(wanted) + " at " + NumberText.of(rung.getAsDouble()) + ", "
                        + (limit.looser(written, rung.getAsDouble()) ? "looser" : "within");
            }
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    private static boolean grayOnly(Profile profile) {
        return !profile.derivatives().isEmpty()
                && profile.derivatives().stream().allMatch(derivative -> derivative.layout() == Layout.GRAY);
    }

    /**
     * One profile limit over the ladder its check grades on.
     *
     * @param check the check's name, such as {@code noise}
     * @param path where the profile writes the limit, such as {@code qc.minimumSnr}
     * @param ladder the check's ladder, best first
     * @param higherIsBetter true when the limit is a floor, as for signal to noise
     * @param read the limit off a profile's quality section
     */
    public record LadderedLimit(
            String check, String path, List<Rung> ladder, boolean higherIsBetter, ToDoubleFunction<Profile.Qc> read) {
        /** A pairing of the given parts. */
        public LadderedLimit {
            Objects.requireNonNull(check, "check");
            Objects.requireNonNull(path, "path");
            ladder = List.copyOf(ladder);
            Objects.requireNonNull(read, "read");
        }

        /** True when the written limit lets through a reading the rung would not. */
        public boolean looser(double written, double rung) {
            return higherIsBetter ? written < rung : written > rung;
        }
    }
}
