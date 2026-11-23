package io.github.rkaushikdev.decklewright.profile;

import static io.github.rkaushikdev.decklewright.profile.Schema.choice;
import static io.github.rkaushikdev.decklewright.profile.Schema.edges;
import static io.github.rkaushikdev.decklewright.profile.Schema.flag;
import static io.github.rkaushikdev.decklewright.profile.Schema.group;
import static io.github.rkaushikdev.decklewright.profile.Schema.length;
import static io.github.rkaushikdev.decklewright.profile.Schema.list;
import static io.github.rkaushikdev.decklewright.profile.Schema.number;
import static io.github.rkaushikdev.decklewright.profile.Schema.readGroup;
import static io.github.rkaushikdev.decklewright.profile.Schema.text;
import static io.github.rkaushikdev.decklewright.profile.Schema.tolerance;

import io.github.rkaushikdev.decklewright.ProfileError;
import io.github.rkaushikdev.decklewright.geom.AspectDirection;
import io.github.rkaushikdev.decklewright.geom.RoundPolicy;
import io.github.rkaushikdev.decklewright.geom.SnapDirection;
import io.github.rkaushikdev.decklewright.measure.Length;
import io.github.rkaushikdev.decklewright.measure.Tolerance;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * What a collection profile actually says.
 *
 * <p>Read this class to find out what a curator may write. Everything a profile can control is here
 * and nothing is controlled anywhere else, which is the whole point: the code has no opinions about
 * a collection, only about how to carry out the ones written down.
 */
public final class Fields {

    /** The formats a derivative may be written in. */
    public static final List<String> OUTPUT_FORMATS = OutputFormat.LABELS;

    /** What a crop is measured from. */
    public static final List<String> ANCHORS = Anchor.LABELS;

    /** The resampling kernels a derivative may name. Declared before the tree, which reads it. */
    private static final List<String> KERNELS =
            List.of("box", "triangle", "catmullRom", "mitchell", "lanczos2", "lanczos3");

    /** Every field of a profile, as one tree, in reading order. */
    public static final Map<String, Field> PROFILE_FIELDS = profileFields();

    /** A list index the way a json path writes one: no sign and no leading zero. */
    private static final Pattern INDEX = Pattern.compile("0|[1-9][0-9]*");

    private Fields() {}

    /**
     * Read a written profile into typed values. Nothing here looks at whether the settings make
     * sense together, which is a separate pass with its own errors.
     *
     * @param document the written profile, as {@link io.github.rkaushikdev.decklewright.util.Json#parse}
     *     gives it
     * @throws ProfileError naming the path of the first setting that will not read
     */
    public static Profile parseProfile(Object document) {
        return toProfile(readGroup(PROFILE_FIELDS, document, ""));
    }

    /** Every path a profile may set, in reading order, groups before the settings inside them. */
    public static List<String> settingPaths() {
        List<String> out = new ArrayList<>();
        walk(PROFILE_FIELDS, "", out);
        return List.copyOf(out);
    }

    /**
     * The value at a dotted path, such as {@code crop.minimumPpi}, in the profile's json form, which
     * is what {@code deckle profile --setting} prints. The walk reaches anything {@link
     * Profile#toJson()} writes, including the parts of a length ({@code crop.bleed.top.unit}) and a
     * derivative by its index ({@code derivatives.0.name}).
     *
     * @return the plain data at the path: a {@link Double}, a string or a boolean for a single
     *     setting, a choice as its label, and a read only map or list for anything json writes as
     *     an object or an array, such as a group or a {@link Length}; empty when nothing is there
     */
    public static Optional<Object> settingAt(Profile profile, String path) {
        Object current = plain(profile);
        for (String part : path.split("\\.", -1)) {
            Optional<Object> next = member(current, part);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            current = next.get();
        }
        return Optional.of(current);
    }

    /** The value with every {@link JsonValue} in it replaced by its json form, all the way down. */
    private static Object plain(Object value) {
        return switch (value) {
            case JsonValue json -> plain(json.toJson());
            case Map<?, ?> map -> {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((key, member) -> out.put(String.valueOf(key), plain(member)));
                yield Collections.unmodifiableMap(out);
            }
            case List<?> list -> list.stream().map(Fields::plain).toList();
            case null, default -> value;
        };
    }

    private static Optional<Object> member(Object value, String key) {
        return switch (value) {
            case Map<?, ?> bag -> Optional.ofNullable(bag.get(key));
            case List<?> items -> item(items, key);
            case null, default -> Optional.empty();
        };
    }

    private static Optional<Object> item(List<?> items, String key) {
        // Eighteen digits always fit a long, and an index any longer is past the end of every list.
        if (!INDEX.matcher(key).matches() || key.length() > 18) {
            return Optional.empty();
        }
        long index = Long.parseLong(key);
        return index < items.size() ? Optional.ofNullable(items.get((int) index)) : Optional.empty();
    }

    private static void walk(Map<String, Field> fields, String prefix, List<String> out) {
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            out.add(path);
            walk(entry.getValue().fields(), path, out);
        }
    }

    private static Map<String, Field> profileFields() {
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("name", text(required()));
        fields.put("title", text(fallback("untitled collection")));
        fields.put("version", number(bounded().withMin(1).withInteger(true).withFallback(1.0)));
        fields.put("extends", text());
        fields.put("capture", group(captureFields(), fallback(Map.of())));
        fields.put("crop", group(cropFields(), fallback(Map.of())));
        fields.put("derivatives", list(group(derivativeFields()), fallback(List.of())));
        fields.put("qc", group(qcFields(), fallback(Map.of())));
        fields.put("naming", group(namingFields(), fallback(Map.of())));
        return Collections.unmodifiableMap(fields);
    }

    private static Map<String, Field> captureFields() {
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("sigma", number(bounded().withMin(1).withMax(40).withFallback(6.0)));
        fields.put(
                "speckle",
                number(bounded().withMin(0).withMax(20).withInteger(true).withFallback(2.0)));
        fields.put(
                "gap", number(bounded().withMin(0).withMax(20).withInteger(true).withFallback(3.0)));
        fields.put("expectNeatline", flag(fallback(false)));
        fields.put("barLength", length(fallback("6in")));
        fields.put("claimedPpi", number(bounded().withMin(1).withFallback(400.0)));
        return fields;
    }

    private static Map<String, Field> cropFields() {
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("anchor", choice(ANCHORS, fallback("sheet")));
        fields.put("bleed", edges(fallback("0mm")));
        fields.put("safeArea", edges(fallback("0mm")));
        fields.put("aspect", number(bounded().withMin(0.05).withMax(20)));
        fields.put("aspectDirection", choice(labels(AspectDirection.values()), fallback("grow")));
        fields.put("minimumPpi", number(bounded().withMin(1).withFallback(300.0)));
        fields.put("snapTo", number(bounded().withMin(1).withInteger(true).withFallback(2.0)));
        fields.put("snapDirection", choice(labels(SnapDirection.values()), fallback("up")));
        fields.put("rounding", choice(labels(RoundPolicy.values()), fallback("outward")));
        fields.put(
                "background",
                number(bounded().withMin(0).withMax(65535).withInteger(true).withFallback(0.0)));
        fields.put("deskew", flag(fallback(true)));
        fields.put("deskewAbove", number(bounded().withMin(0).withMax(45).withFallback(0.0)));
        return fields;
    }

    private static Map<String, Field> derivativeFields() {
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("name", text(required()));
        fields.put("format", choice(OUTPUT_FORMATS, fallback("png")));
        fields.put("longestSide", number(bounded().withMin(1).withInteger(true)));
        fields.put("scale", number(bounded().withMin(0.001).withMax(4)));
        fields.put("depth", choice(Depth.LABELS, fallback("8")));
        fields.put("layout", choice(labels(Layout.GRAY, Layout.RGB), fallback("rgb")));
        fields.put("kernel", choice(KERNELS, fallback("catmullRom")));
        fields.put("keepMetadata", flag(fallback(false)));
        return fields;
    }

    private static Map<String, Field> qcFields() {
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("samplingPpi", number(bounded().withMin(1).withFallback(400.0)));
        fields.put("samplingTolerance", tolerance(fallback("1%")));
        fields.put("skewTolerance", number(bounded().withMin(0).withMax(45).withFallback(0.3)));
        fields.put(
                "illuminationTolerance", number(bounded().withMin(0).withMax(1).withFallback(0.1)));
        fields.put(
                "grayBalanceTolerance", number(bounded().withMin(0).withMax(255).withFallback(6.0)));
        fields.put("minimumSnr", number(bounded().withMin(0).withFallback(30.0)));
        fields.put("maximumClipping", number(bounded().withMin(0).withMax(1).withFallback(0.005)));
        fields.put(
                "minimumStars",
                number(bounded().withMin(1).withMax(4).withInteger(true).withFallback(3.0)));
        fields.put("toneAims", list(number(bounded().withMin(0).withMax(65535))));
        fields.put("toneTolerance", number(bounded().withMin(0).withMax(255).withFallback(6.0)));
        return fields;
    }

    private static Map<String, Field> namingFields() {
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("pattern", text(fallback("{accession}_{derivative}")));
        fields.put("lowercase", flag(fallback(true)));
        return fields;
    }

    private static FieldOptions required() {
        return FieldOptions.defaults().withRequired(true);
    }

    private static FieldOptions fallback(Object written) {
        return FieldOptions.defaults().withFallback(written);
    }

    private static NumberOptions bounded() {
        return NumberOptions.defaults();
    }

    /** The labels of the given choices, in the order given, which is the order a refusal lists. */
    private static List<String> labels(JsonValue... choices) {
        return Arrays.stream(choices).map(choice -> (String) choice.toJson()).toList();
    }

    // What the schema read, turned into the typed profile. Every cast below is to the type the
    // schema's own reader for that field returns, so none of them can fail.

    private static Profile toProfile(Map<String, Object> read) {
        List<Profile.Derivative> derivatives = new ArrayList<>();
        for (Object derivative : (List<?>) read.get("derivatives")) {
            derivatives.add(toDerivative((Map<?, ?>) derivative));
        }
        return new Profile(
                (String) read.get("name"),
                (String) read.get("title"),
                numberAt(read, "version"),
                Optional.ofNullable((String) read.get("extends")),
                toCapture((Map<?, ?>) read.get("capture")),
                toCrop((Map<?, ?>) read.get("crop")),
                derivatives,
                toQc((Map<?, ?>) read.get("qc")),
                toNaming((Map<?, ?>) read.get("naming")));
    }

    private static Profile.Capture toCapture(Map<?, ?> read) {
        return new Profile.Capture(
                numberAt(read, "sigma"),
                wholeAt(read, "speckle"),
                wholeAt(read, "gap"),
                (Boolean) read.get("expectNeatline"),
                (Length) read.get("barLength"),
                numberAt(read, "claimedPpi"));
    }

    private static Profile.Crop toCrop(Map<?, ?> read) {
        return new Profile.Crop(
                Anchor.parse((String) read.get("anchor")),
                (Edges) read.get("bleed"),
                (Edges) read.get("safeArea"),
                optionalAt(read, "aspect"),
                AspectDirection.parse((String) read.get("aspectDirection")),
                numberAt(read, "minimumPpi"),
                numberAt(read, "snapTo"),
                SnapDirection.parse((String) read.get("snapDirection")),
                RoundPolicy.parse((String) read.get("rounding")),
                wholeAt(read, "background"),
                (Boolean) read.get("deskew"),
                numberAt(read, "deskewAbove"));
    }

    private static Profile.Derivative toDerivative(Map<?, ?> read) {
        return new Profile.Derivative(
                (String) read.get("name"),
                OutputFormat.parse((String) read.get("format")),
                optionalAt(read, "longestSide"),
                optionalAt(read, "scale"),
                Depth.parse((String) read.get("depth")),
                Layout.parse((String) read.get("layout")),
                (String) read.get("kernel"),
                (Boolean) read.get("keepMetadata"));
    }

    private static Profile.Qc toQc(Map<?, ?> read) {
        return new Profile.Qc(
                numberAt(read, "samplingPpi"),
                (Tolerance) read.get("samplingTolerance"),
                numberAt(read, "skewTolerance"),
                numberAt(read, "illuminationTolerance"),
                numberAt(read, "grayBalanceTolerance"),
                numberAt(read, "minimumSnr"),
                numberAt(read, "maximumClipping"),
                wholeAt(read, "minimumStars"),
                numbersAt(read, "toneAims"),
                numberAt(read, "toneTolerance"));
    }

    private static Profile.Naming toNaming(Map<?, ?> read) {
        return new Profile.Naming((String) read.get("pattern"), (Boolean) read.get("lowercase"));
    }

    private static double numberAt(Map<?, ?> read, String key) {
        return (Double) read.get(key);
    }

    /** A list of numbers, or none when the setting was not written. */
    private static List<Double> numbersAt(Map<?, ?> read, String key) {
        return read.get(key) instanceof List<?> written
                ? written.stream().map(Double.class::cast).toList()
                : List.of();
    }

    /** A number the schema bounds to a small whole range, so it is exact as an int. */
    private static int wholeAt(Map<?, ?> read, String key) {
        return (int) numberAt(read, key);
    }

    private static OptionalDouble optionalAt(Map<?, ?> read, String key) {
        return read.get(key) instanceof Double value ? OptionalDouble.of(value) : OptionalDouble.empty();
    }
}
