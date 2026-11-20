package io.github.rkaushikdev.decklewright.plan;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.measure.PhysicalSize;
import io.github.rkaushikdev.decklewright.measure.PhysicalSizes;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Unit;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A crop plan, and the record of how it got that way.
 *
 * <p>The rect is the answer. The steps are why, and they matter as much: a curator who thinks a crop
 * is wrong wants to know which rule moved it, and an operator who has to override one wants to know
 * which one to override.
 *
 * <p>A plan is immutable. Every rule hands back a new one with its step recorded, so the trail cannot
 * be lost by a rule that forgot to append to it.
 *
 * @param capture the whole capture, in pixels, in the frame the plan is drawn in
 * @param rect the crop as it stands
 * @param sampling the frequency the capture was taken at
 * @param deskew the turn to take out of the capture before cropping
 * @param steps every move, the start first; copied
 * @param warnings what an operator should read before trusting the crop, each said once; copied
 */
public record CropPlan(
        Rect capture, Rect rect, Sampling sampling, Angle deskew, List<Step> steps, List<String> warnings)
        implements JsonValue {

    /** What the first step of every plan is called. */
    private static final String START = "start";

    /** A plan of the given parts; the steps and warnings are copied. */
    public CropPlan {
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(deskew, "deskew");
        steps = List.copyOf(steps);
        warnings = List.copyOf(warnings);
    }

    /**
     * A plan at the anchor, with no turn to take out.
     *
     * @see #start(Rect, Rect, Sampling, Angle)
     */
    public static CropPlan start(Rect capture, Rect rect, Sampling sampling) {
        return start(capture, rect, sampling, Angle.zero());
    }

    /**
     * A plan at the anchor, recorded as its first step, with no warnings yet.
     *
     * @param rect the anchor the profile named
     * @param deskew the turn to take out before cropping
     */
    public static CropPlan start(Rect capture, Rect rect, Sampling sampling, Angle deskew) {
        Step first = new Step(START, "the anchor the profile named", rect, rect);
        return new CropPlan(capture, rect, sampling, deskew, List.of(first), List.of());
    }

    /** The extent of the crop. */
    public Size size() {
        return rect.size();
    }

    /** True when the crop reaches past the capture. */
    public boolean overhangs() {
        return !capture.containsRect(rect);
    }

    /** How much of the crop, in square pixels, falls outside the capture. */
    public double overhangArea() {
        return rect.area() - rect.intersect(capture).area();
    }

    /** The physical size the crop will come out at, in inches. */
    public PhysicalSize physical() {
        return physical(Unit.INCH);
    }

    /** The physical size the crop will come out at, in the given unit. */
    public PhysicalSize physical(Unit unit) {
        return PhysicalSizes.physicalSize(rect.size(), sampling, unit);
    }

    /**
     * The crop moved, with the rule that did it recorded. A rule that changes nothing, to within a
     * billionth of a pixel, records nothing and gets this same plan back, so the trail holds only what
     * actually happened.
     *
     * @param rule the name of the rule that moved it
     * @param note what the rule did, in words
     */
    public CropPlan moved(Rect rect, String rule, String note) {
        if (rect.approxEquals(this.rect)) {
            return this;
        }
        List<Step> trail = new ArrayList<>(steps);
        trail.add(new Step(rule, note, this.rect, rect));
        return new CropPlan(capture, rect, sampling, deskew, trail, warnings);
    }

    /** This plan with a warning added; this same plan when it already says exactly that. */
    public CropPlan warned(String text) {
        if (warnings.contains(text)) {
            return this;
        }
        List<String> said = new ArrayList<>(warnings);
        said.add(text);
        return new CropPlan(capture, rect, sampling, deskew, steps, said);
    }

    /** This plan with another turn to take out, everything else the same. */
    public CropPlan withDeskew(Angle angle) {
        return new CropPlan(capture, rect, sampling, angle, steps, warnings);
    }

    /** True when the named rule moved the crop. */
    public boolean touchedBy(String rule) {
        return steps.stream().anyMatch(step -> step.rule().equals(rule));
    }

    /** The rules that moved the crop, in order, without the start. */
    public List<String> trail() {
        return steps.stream()
                .map(Step::rule)
                .filter(rule -> !rule.equals(START))
                .toList();
    }

    /**
     * {@code {rect, capture, sampling, deskew, steps, warnings}}, in a new map the caller may keep,
     * with the deskew in degrees and each step as {@code {rule, note, before, after}}.
     */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("rect", rect.toJson());
        json.put("capture", capture.toJson());
        json.put("sampling", sampling.toJson());
        json.put("deskew", deskew.degrees());
        json.put("steps", new ArrayList<Object>(steps.stream().map(Step::toJson).toList()));
        json.put("warnings", new ArrayList<Object>(warnings));
        return json;
    }

    /** The crop and how many rules moved it, such as {@code 10x10+0+0 after 1 rules}. */
    @Override
    public String toString() {
        return rect + " after " + trail().size() + " rules";
    }

    /**
     * One move in a plan's trail.
     *
     * @param rule the name of the rule that made it, or {@code start} for the anchor itself
     * @param note what the rule did, in words
     * @param before the crop before the move
     * @param after the crop after it
     */
    public record Step(String rule, String note, Rect before, Rect after) implements JsonValue {

        /** A step of the given parts. */
        public Step {
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(note, "note");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }

        /** {@code {rule, note, before, after}}, in a new map the caller may keep. */
        @Override
        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("rule", rule);
            json.put("note", note);
            json.put("before", before.toJson());
            json.put("after", after.toJson());
            return json;
        }
    }
}
