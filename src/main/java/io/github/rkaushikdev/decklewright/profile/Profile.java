package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.geom.AspectDirection;
import io.github.rkaushikdev.decklewright.geom.RoundPolicy;
import io.github.rkaushikdev.decklewright.geom.SnapDirection;
import io.github.rkaushikdev.decklewright.measure.Length;
import io.github.rkaushikdev.decklewright.measure.Tolerance;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A collection profile once it has been read: every setting typed, every fallback filled in.
 *
 * <p>{@link Fields#parseProfile(Object)} makes one from a written document, and {@link
 * ProfileRegistry#get(String)} makes one with its extends chain flattened and its settings checked,
 * which is the form every stage in the tree takes. Nothing here says whether the settings make sense
 * together; {@link Validation} does.
 *
 * <p>{@link #toJson()} is the profile as {@code deckle profile --json} prints it: the sections in
 * the order {@link Fields#PROFILE_FIELDS} declares them, optional settings that were not written
 * left out, lengths as {@code {value, unit}}, choices as their labels and every number as a {@link
 * Double}, as json reads it back.
 *
 * <p>The numbers the schema only bounds from below, such as {@link #version()}, {@link
 * Crop#snapTo()} and {@link Derivative#longestSide()}, stay doubles so a whole number of any size
 * reads back as it was written; the bounded counts are ints.
 *
 * @param name what the profile is registered as, such as {@code quadrangles}
 * @param title what it is called in a report, such as {@code usgs quadrangle sheets}
 * @param version the profile version it was written for, a whole number from one
 * @param parent the name of the profile this one extends; always empty once the chain is flattened
 * @param capture what the detection stage is told about a capture
 * @param crop how the planner cuts a sheet
 * @param derivatives what is written from each cut, in the order written
 * @param qc where the quality bar sits
 * @param naming how the derivative files are named
 */
public record Profile(
        String name,
        String title,
        double version,
        Optional<String> parent,
        Capture capture,
        Crop crop,
        List<Derivative> derivatives,
        Qc qc,
        Naming naming)
        implements JsonValue {

    /** A profile of the given settings; the derivatives are copied. */
    public Profile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(crop, "crop");
        derivatives = List.copyOf(derivatives);
        Objects.requireNonNull(qc, "qc");
        Objects.requireNonNull(naming, "naming");
    }

    /** This profile held to another quality bar, everything else the same. */
    public Profile withQc(Qc qc) {
        return new Profile(name, title, version, parent, capture, crop, derivatives, qc, naming);
    }

    /** This profile with its files named another way, everything else the same. */
    public Profile withNaming(Naming naming) {
        return new Profile(name, title, version, parent, capture, crop, derivatives, qc, naming);
    }

    /**
     * The profile as json holds it, in a new map the caller may keep. The sections are their own
     * {@link JsonValue}s, so {@link io.github.rkaushikdev.decklewright.util.Json#stringify(Object,
     * int)} writes the whole tree.
     */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", name);
        json.put("title", title);
        json.put("version", version);
        parent.ifPresent(written -> json.put("extends", written));
        json.put("capture", capture);
        json.put("crop", crop);
        json.put("derivatives", derivatives);
        json.put("qc", qc);
        json.put("naming", naming);
        return json;
    }

    /**
     * What the detection stage is told about a capture.
     *
     * @param sigma how far from the bed a sample has to be to count as object
     * @param speckle the radius of the opening that removes dust from the object mask
     * @param gap the radius of the closing that mends a torn sheet edge
     * @param expectNeatline true when the capture is searched for a printed border
     * @param barLength the true length of the scale rule on the copy stand
     * @param claimedPpi what the scanner says it captured at
     */
    public record Capture(
            double sigma, int speckle, int gap, boolean expectNeatline, Length barLength, double claimedPpi)
            implements JsonValue {

        /** Capture settings of the given values. */
        public Capture {
            Objects.requireNonNull(barLength, "barLength");
        }

        /** The settings in field order, in a new map the caller may keep. */
        @Override
        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("sigma", sigma);
            json.put("speckle", (double) speckle);
            json.put("gap", (double) gap);
            json.put("expectNeatline", expectNeatline);
            json.put("barLength", barLength);
            json.put("claimedPpi", claimedPpi);
            return json;
        }
    }

    /**
     * How the planner cuts a sheet, in the units the curator wrote.
     *
     * @param anchor what the crop is measured from
     * @param bleed how much beyond the anchor is kept, per edge
     * @param safeArea how much inside the anchor is held clear, per edge
     * @param aspect the width over height the crop is forced to; empty when it is not forced
     * @param aspectDirection whether a forced aspect grows or shrinks the crop
     * @param minimumPpi the lowest sampling frequency a crop may come out at
     * @param snapTo the multiple, in pixels, the extents are snapped to; one means no snap
     * @param snapDirection which way a snap goes
     * @param rounding how a crop is made whole pixels
     * @param background the sample value that fills whatever a crop takes from outside the capture
     * @param deskew true when the sheet is straightened before it is cut
     * @param deskewAbove the skew, in degrees, a sheet has to exceed before it is straightened; zero
     *     straightens every sheet that is off square at all
     */
    public record Crop(
            Anchor anchor,
            Edges bleed,
            Edges safeArea,
            OptionalDouble aspect,
            AspectDirection aspectDirection,
            double minimumPpi,
            double snapTo,
            SnapDirection snapDirection,
            RoundPolicy rounding,
            int background,
            boolean deskew,
            double deskewAbove)
            implements JsonValue {

        /** Crop settings of the given values. */
        public Crop {
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(bleed, "bleed");
            Objects.requireNonNull(safeArea, "safeArea");
            Objects.requireNonNull(aspect, "aspect");
            Objects.requireNonNull(aspectDirection, "aspectDirection");
            Objects.requireNonNull(snapDirection, "snapDirection");
            Objects.requireNonNull(rounding, "rounding");
        }

        /** The settings in field order, the aspect left out when it is not forced, in a new map. */
        @Override
        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("anchor", anchor);
            json.put("bleed", bleed);
            json.put("safeArea", safeArea);
            aspect.ifPresent(value -> json.put("aspect", value));
            json.put("aspectDirection", aspectDirection);
            json.put("minimumPpi", minimumPpi);
            json.put("snapTo", snapTo);
            json.put("snapDirection", snapDirection);
            json.put("rounding", rounding);
            json.put("background", (double) background);
            json.put("deskew", deskew);
            json.put("deskewAbove", deskewAbove);
            return json;
        }
    }

    /**
     * One file written from each cut. A derivative that sets none of a longest side, a scale and a
     * sampling frequency is written at full size.
     *
     * @param name what the derivative is called, which goes into its file name
     * @param format the format it is written in
     * @param longestSide the pixels its longest side is reduced to; empty when it is not
     * @param scale the factor it is resampled by; empty when it is not
     * @param ppi the sampling frequency it is reduced to, against the master's; empty when it is not
     * @param depth the bits per sample it is written at
     * @param layout the channels it is written with, gray or rgb
     * @param kernel the name of the resampling kernel, such as {@code catmullRom}
     * @param keepMetadata true when the master's text metadata is carried into the file
     */
    public record Derivative(
            String name,
            OutputFormat format,
            OptionalDouble longestSide,
            OptionalDouble scale,
            OptionalDouble ppi,
            Depth depth,
            Layout layout,
            String kernel,
            boolean keepMetadata)
            implements JsonValue {

        /** A derivative of the given settings. */
        public Derivative {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(longestSide, "longestSide");
            Objects.requireNonNull(scale, "scale");
            Objects.requireNonNull(ppi, "ppi");
            Objects.requireNonNull(depth, "depth");
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(kernel, "kernel");
        }

        /** The settings in field order, the size instructions left out when unset, in a new map. */
        @Override
        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("name", name);
            json.put("format", format);
            longestSide.ifPresent(value -> json.put("longestSide", value));
            scale.ifPresent(value -> json.put("scale", value));
            ppi.ifPresent(value -> json.put("ppi", value));
            json.put("depth", depth);
            json.put("layout", layout);
            json.put("kernel", kernel);
            json.put("keepMetadata", keepMetadata);
            return json;
        }
    }

    /**
     * Where the quality bar sits. These are the funder's numbers.
     *
     * @param samplingPpi the sampling frequency a capture has to reach
     * @param samplingTolerance how far from it a capture may be
     * @param skewTolerance the most skew, in degrees, a cut may keep
     * @param illuminationTolerance the widest spread of brightness across the bed, as a share of
     *     its mean
     * @param grayBalanceTolerance the largest cast a neutral patch of the target may carry
     * @param minimumSnr the lowest signal to noise ratio a capture may have
     * @param maximumClipping the largest share of samples that may sit at either end of the range
     * @param minimumStars the grade, one to four stars, a capture has to reach to be delivered
     * @param toneAims what each patch of the step wedge should read, in sample values, lightest or
     *     darkest first; copied, and empty when the profile does not aim its wedge
     * @param toneTolerance how far off its aim any one patch may read, in sample values
     */
    public record Qc(
            double samplingPpi,
            Tolerance samplingTolerance,
            double skewTolerance,
            double illuminationTolerance,
            double grayBalanceTolerance,
            double minimumSnr,
            double maximumClipping,
            int minimumStars,
            List<Double> toneAims,
            double toneTolerance)
            implements JsonValue {

        /** Quality settings of the given values; the tone aims are copied. */
        public Qc {
            Objects.requireNonNull(samplingTolerance, "samplingTolerance");
            toneAims = List.copyOf(toneAims);
        }

        /** These settings asking for another grade, everything else the same. */
        public Qc withMinimumStars(int stars) {
            return new Qc(
                    samplingPpi,
                    samplingTolerance,
                    skewTolerance,
                    illuminationTolerance,
                    grayBalanceTolerance,
                    minimumSnr,
                    maximumClipping,
                    stars,
                    toneAims,
                    toneTolerance);
        }

        /** The settings in field order, the tone aims left out when there are none, in a new map. */
        @Override
        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("samplingPpi", samplingPpi);
            json.put("samplingTolerance", samplingTolerance);
            json.put("skewTolerance", skewTolerance);
            json.put("illuminationTolerance", illuminationTolerance);
            json.put("grayBalanceTolerance", grayBalanceTolerance);
            json.put("minimumSnr", minimumSnr);
            json.put("maximumClipping", maximumClipping);
            json.put("minimumStars", (double) minimumStars);
            if (!toneAims.isEmpty()) {
                json.put("toneAims", new ArrayList<Object>(toneAims));
            }
            json.put("toneTolerance", toneTolerance);
            return json;
        }
    }

    /**
     * How the derivative files are named.
     *
     * @param pattern the file name with placeholders, such as {@code {accession}_{derivative}}
     * @param lowercase true when the filled name is lower cased
     * @see Settings#fillName(Profile, Map)
     */
    public record Naming(String pattern, boolean lowercase) implements JsonValue {

        /** Naming of the given pattern. */
        public Naming {
            Objects.requireNonNull(pattern, "pattern");
        }

        /** {@code {pattern, lowercase}}, in a new map the caller may keep. */
        @Override
        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("pattern", pattern);
            json.put("lowercase", lowercase);
            return json;
        }
    }
}
