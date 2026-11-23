package io.github.rkaushikdev.decklewright.plan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.detect.Neatline;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Point;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.rules.Landmarks;
import io.github.rkaushikdev.decklewright.plan.rules.Rule;
import io.github.rkaushikdev.decklewright.profile.Anchor;
import io.github.rkaushikdev.decklewright.profile.CropOptions;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.Settings;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * From a read capture and a profile to a crop.
 *
 * <p>The one thing this has to get right, and the one thing that has gone wrong twice, is the order
 * of the deskew and the crop. The detector reports the sheet in the coordinates of the raw capture.
 * The render straightens the capture first, which moves every one of those coordinates, and a plan
 * drawn against the raw coordinates is then wrong by the same rotation.
 *
 * <p>So the anchor is turned into the straightened frame here, before any rule sees it, and the plan
 * that comes out is in the frame the render will actually work in.
 */
public final class CropPlanner {
    private CropPlanner() {}

    /**
     * The mapping from raw capture coordinates into the straightened frame, plus the canvas that frame
     * has. The rotation is about the centre of the capture and the canvas grows, so the offset of the
     * new canvas is in the mapping too.
     *
     * @param skew the turn the detector measured, which straightening takes out
     */
    public static StraightFrame straightFrame(Rect capture, Angle skew) {
        return StraightFrame.of(capture, skew);
    }

    /**
     * The box round a set of points once the capture is straightened. Handing the quad's own corners
     * in rather than its bounding box matters: boxing first and turning second grows the crop twice,
     * once for the turn the sheet has and once for the turn being taken out, and the second growth is
     * not real.
     */
    public static Straightened pointsIntoStraightFrame(List<Point> points, Rect capture, Angle skew) {
        StraightFrame frame = straightFrame(capture, skew);
        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (Point p : points) {
            Point moved = frame.point(p.x(), p.y());
            left = Math.min(left, moved.x());
            top = Math.min(top, moved.y());
            right = Math.max(right, moved.x());
            bottom = Math.max(bottom, moved.y());
        }
        return new Straightened(Rect.fromEdges(left, top, right, bottom), frame.capture());
    }

    /** Where a rect ends up once the capture is straightened; the rect and capture themselves when there is no skew. */
    public static Straightened intoStraightFrame(Rect rect, Rect capture, Angle skew) {
        if (skew.isZero()) {
            return new Straightened(rect, capture);
        }
        return pointsIntoStraightFrame(rect.corners(), capture, skew);
    }

    /**
     * The crop for a capture, at the frequency the reading measured off the rule.
     *
     * @see #planCrop(CaptureReading, Profile, Rect, Optional, PlanOptions)
     */
    public static PlannedCrop planCrop(CaptureReading reading, Profile profile, Rect capture) {
        return planCrop(reading, profile, capture, Optional.empty(), PlanOptions.defaults());
    }

    /**
     * The crop for a capture, at a frequency the caller measured.
     *
     * @see #planCrop(CaptureReading, Profile, Rect, Optional, PlanOptions)
     */
    public static PlannedCrop planCrop(CaptureReading reading, Profile profile, Rect capture, Sampling sampling) {
        return planCrop(reading, profile, capture, Optional.of(sampling), PlanOptions.defaults());
    }

    /**
     * The crop for a capture: the sheet and any neatline turned into the straightened frame, the rule
     * set the profile asks for run over the anchor, and the detector's warnings carried onto the plan.
     *
     * @param reading what the detector read off the capture
     * @param profile the resolved profile
     * @param capture the bounds of the raw capture
     * @param sampling a measured frequency, which is used over the one the reading carries
     * @param options the per run choices, including the pass limit
     * @return the plan, in the straightened frame, with the rules that ran and whether they settled
     * @throws PlanError when there is no frequency to plan against, the profile crops to a neatline
     *     that was not found, or a rule refuses the plan
     * @throws BadArgumentError when a setting cannot make a rule or the pass limit is not above zero
     */
    public static PlannedCrop planCrop(
            CaptureReading reading, Profile profile, Rect capture, Optional<Sampling> sampling, PlanOptions options) {
        Sampling frequency = sampling.or(reading::sampling)
                .orElseThrow(() -> new PlanError(
                        "there is no sampling frequency to plan against", Map.of("profile", profile.name())));

        CropOptions crop = Settings.cropOptions(profile, frequency);
        Angle skew = crop.turnFor(reading.sheet().skew());

        Straightened sheetMoved = pointsIntoStraightFrame(reading.sheet().quad().corners(), capture, skew);
        Optional<Rect> neatline = reading.neatline()
                .map(Neatline::rect)
                .map(raw -> intoStraightFrame(raw, capture, skew).rect().outward());
        Landmarks found = new Landmarks(sheetMoved.rect().outward(), neatline);

        List<Rule> rules = RuleSets.rulesFor(crop, found, options);
        PlannedCrop out = Engine.planInDetail(
                rules,
                new PlanInput(sheetMoved.capture(), startingAnchor(crop, found), frequency, skew),
                options.maxPasses());

        CropPlan plan = out.plan();
        for (String warning : reading.warnings()) {
            plan = plan.warned(warning);
        }
        return out.withPlan(plan);
    }

    /** Where the crop starts: the neatline when the profile crops to one and one was found, the sheet otherwise. */
    static Rect startingAnchor(CropOptions crop, Landmarks found) {
        return found.neatline().filter(rect -> crop.anchor() == Anchor.NEATLINE).orElse(found.sheet());
    }
}
