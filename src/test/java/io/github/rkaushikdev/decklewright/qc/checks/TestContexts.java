package io.github.rkaushikdev.decklewright.qc.checks;

import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.detect.TargetFinding;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Tolerance;
import io.github.rkaushikdev.decklewright.profile.QcLimits;
import java.util.List;

/**
 * What the check tests build their hand-made contexts from.
 *
 * <p>A {@link QcLimits} holds every limit, so these start from one set of figures and change the
 * limit a test is about; no check reads another's limit, so the rest cannot move a result.
 */
final class TestContexts {
    /** The house figures, at the 600 ppi the sampling tests are written against. */
    static final QcLimits LIMITS = new QcLimits(Sampling.of(600), Tolerance.percent(1), 0.3, 0.1, 6, 30, 0.005, 3);

    private TestContexts() {}

    static QcLimits skewLimit(double skewTolerance) {
        return new QcLimits(
                LIMITS.sampling(),
                LIMITS.samplingTolerance(),
                skewTolerance,
                LIMITS.illuminationTolerance(),
                LIMITS.grayBalanceTolerance(),
                LIMITS.minimumSnr(),
                LIMITS.maximumClipping(),
                LIMITS.minimumStars());
    }

    static QcLimits illuminationLimit(double illuminationTolerance) {
        return new QcLimits(
                LIMITS.sampling(),
                LIMITS.samplingTolerance(),
                LIMITS.skewTolerance(),
                illuminationTolerance,
                LIMITS.grayBalanceTolerance(),
                LIMITS.minimumSnr(),
                LIMITS.maximumClipping(),
                LIMITS.minimumStars());
    }

    static QcLimits grayBalanceLimit(double grayBalanceTolerance) {
        return new QcLimits(
                LIMITS.sampling(),
                LIMITS.samplingTolerance(),
                LIMITS.skewTolerance(),
                LIMITS.illuminationTolerance(),
                grayBalanceTolerance,
                LIMITS.minimumSnr(),
                LIMITS.maximumClipping(),
                LIMITS.minimumStars());
    }

    static QcLimits noiseLimits(double minimumSnr, double maximumClipping) {
        return new QcLimits(
                LIMITS.sampling(),
                LIMITS.samplingTolerance(),
                LIMITS.skewTolerance(),
                LIMITS.illuminationTolerance(),
                LIMITS.grayBalanceTolerance(),
                minimumSnr,
                maximumClipping,
                LIMITS.minimumStars());
    }

    /** A step wedge of the given patches, each one only a rect, which is all the checks read of it. */
    static TargetFinding targetOver(List<Rect> rects) {
        return new TargetFinding(Rect.empty(), patchesOver(rects), 0, true);
    }

    static List<Patch> patchesOver(List<Rect> rects) {
        return rects.stream().map(rect -> new Patch(rect, List.of(), List.of())).toList();
    }
}
