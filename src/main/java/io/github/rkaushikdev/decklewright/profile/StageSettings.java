package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.measure.Sampling;
import java.util.List;
import java.util.Objects;

/**
 * Every stage's arguments at once, which is what the pipeline holds for the length of a run.
 *
 * @param profile the resolved profile they were made from
 * @param sampling the frequency the lengths were turned into pixels at
 * @param capture what the detection stage takes
 * @param crop what the planner takes
 * @param derivatives what the derive stage takes, one recipe per derivative
 * @param qc what the quality checks take
 * @see Settings#settingsFor(Profile, Sampling)
 */
public record StageSettings(
        Profile profile,
        Sampling sampling,
        CaptureOptions capture,
        CropOptions crop,
        List<DerivativeRecipe> derivatives,
        QcLimits qc) {

    /** Settings of the given parts; the recipes are copied. */
    public StageSettings {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(crop, "crop");
        derivatives = List.copyOf(derivatives);
        Objects.requireNonNull(qc, "qc");
    }
}
