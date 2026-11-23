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
    static final QcLimits LIMITS =
            new QcLimits(Sampling.of(600), Tolerance.percent(1), 0.3, 0.1, 6, 30, 0.005, 3, List.of(), 6);

    private TestContexts() {}

    static QcLimits skewLimit(double skewTolerance) {
        return LIMITS.withSkewTolerance(skewTolerance);
    }

    static QcLimits illuminationLimit(double illuminationTolerance) {
        return LIMITS.withIlluminationTolerance(illuminationTolerance);
    }

    static QcLimits grayBalanceLimit(double grayBalanceTolerance) {
        return LIMITS.withGrayBalanceTolerance(grayBalanceTolerance);
    }

    static QcLimits noiseLimits(double minimumSnr, double maximumClipping) {
        return LIMITS.withNoise(minimumSnr, maximumClipping);
    }

    /** A step wedge of the given patches, each one only a rect, which is all the checks read of it. */
    static TargetFinding targetOver(List<Rect> rects) {
        return new TargetFinding(Rect.empty(), patchesOver(rects), 0, true);
    }

    static List<Patch> patchesOver(List<Rect> rects) {
        return rects.stream().map(rect -> new Patch(rect, List.of(), List.of())).toList();
    }
}
