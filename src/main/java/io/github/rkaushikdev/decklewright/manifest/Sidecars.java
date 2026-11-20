package io.github.rkaushikdev.decklewright.manifest;

import io.github.rkaushikdev.decklewright.ManifestError;
import io.github.rkaushikdev.decklewright.derive.DerivedFile;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.QcResult;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.IsoTime;
import io.github.rkaushikdev.decklewright.util.Json;
import io.github.rkaushikdev.decklewright.util.JsonSyntaxException;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The document that goes beside a delivery.
 *
 * <p>Everything a repository needs to know about an accession that is not in the pixels: what was
 * photographed, at what frequency, where it was cut, how it graded, and what the files hash to. It
 * is json because json is the only format that both the catalogue and the tape robot can read
 * without a library that has to be maintained.
 *
 * <p>Two rules hold this together. The document is written in a fixed key order, so two runs of
 * the same accession produce byte identical sidecars and a diff between them means something. And
 * every number in it is written as a number rather than as a formatted string, because the first
 * thing anybody does with a sidecar is load it into a spreadsheet.
 *
 * <p>A sidecar is plain data, the shape {@link Json#parse(String)} gives: maps with string keys in
 * key order, lists, strings, a {@link Double} for every number, booleans and nulls. Documents are
 * built in that shape too, so one read back from its own text is equal to the one that was
 * written.
 */
public final class Sidecars {

    /** Bumped when the shape changes in a way a reader has to know about. */
    public static final int SIDECAR_VERSION = 2;

    /** The keys, in the order they are written. Any others follow, in their own order. */
    private static final List<String> ORDER = List.of(
            "version", "accession", "profile", "created", "capture", "crop", "derivatives", "quality", "warnings");

    private Sidecars() {}

    /**
     * The document for a run, with no time of creation.
     *
     * @see #sidecarFor(FinishedRun, Instant)
     */
    public static Map<String, Object> sidecarFor(FinishedRun run) {
        return build(run, null);
    }

    /**
     * The document for a run.
     *
     * <p>The quality section is null when the run was not graded, because a lab that is only cutting
     * sheets has no reason to run the checks, and a sidecar with a quality section full of nulls
     * reads as a failed grading rather than as one that was never asked for.
     *
     * @param created when the delivery was made, written as UTC with milliseconds
     * @return a new document the caller may keep and add to
     */
    public static Map<String, Object> sidecarFor(FinishedRun run, Instant created) {
        return build(run, IsoTime.format(created));
    }

    /**
     * The quality section for a grading: the level, what was wanted, whether it passed, the check
     * holding it down, the required checks that could not run, and one line per check. A measurement
     * that is not a finite number is written as null.
     *
     * @return a new section the caller may keep
     */
    public static Map<String, Object> qualityOf(QcResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stars", (double) result.stars());
        out.put("wanted", (double) result.wanted());
        out.put("passed", result.passed());
        out.put("worst", result.worst().orElse(null));
        out.put("missing", new ArrayList<Object>(result.missing()));
        List<Object> checks = new ArrayList<>();
        for (Finding finding : result.findings()) {
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("name", finding.name());
            check.put("ran", finding.ran());
            check.put("stars", (double) finding.stars());
            check.put("measured", Double.isFinite(finding.measured()) ? finding.measured() : null);
            check.put("note", finding.note());
            checks.add(check);
        }
        out.put("checks", checks);
        return out;
    }

    /**
     * Write a document out: the fixed keys first, two spaces of indentation because somebody will
     * read it in a terminal, and a trailing newline because somebody will {@code cat} it.
     */
    public static String writeSidecar(Map<String, ?> document) {
        return Json.stringify(ordered(document), 2) + "\n";
    }

    /**
     * Read the bytes of a sidecar file back: UTF-8, with a leading byte order mark ignored.
     *
     * @see #parseSidecar(String)
     */
    public static Map<String, Object> parseSidecar(byte[] bytes) {
        return parseSidecar(Documents.decode(bytes));
    }

    /**
     * Read one back, refusing anything this reader would misunderstand.
     *
     * <p>A version from the future is refused rather than read on a hope. A sidecar is the only
     * record of how a file was made, and half reading one is how a repository ends up with a crop
     * rect measured in the wrong units. Earlier versions are read.
     *
     * @return the document as written, as plain data
     * @throws ManifestError when the text is not json, is not an object, says no version or a later
     *     one, or names no accession
     */
    public static Map<String, Object> parseSidecar(String text) {
        Object parsed;
        try {
            parsed = Json.parse(text);
        } catch (JsonSyntaxException cause) {
            throw new ManifestError(
                    "that is not a sidecar, it is not even json",
                    Map.of("cause", cause.getClass().getSimpleName() + ": " + cause.getMessage()),
                    cause);
        }
        if (!(parsed instanceof Map<?, ?>)) {
            throw new ManifestError("a sidecar has to be an object", Map.of());
        }
        @SuppressWarnings("unchecked") // the json reader keys every object by its member names
        Map<String, Object> document = (Map<String, Object>) parsed;

        if (!(document.get("version") instanceof Number version)) {
            throw new ManifestError("this sidecar does not say what version it is", Map.of());
        }
        if (version.doubleValue() > SIDECAR_VERSION) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("found", version);
            details.put("understood", SIDECAR_VERSION);
            throw new ManifestError("this sidecar was written by a later version", details);
        }
        if (!(document.get("accession") instanceof String accession) || accession.isEmpty()) {
            throw new ManifestError("this sidecar names no accession", Map.of());
        }
        return document;
    }

    /**
     * Everything wrong with a document, without refusing it. The cli uses this to say what is
     * missing from a hand written sidecar rather than stopping at the first fault.
     *
     * @return every objection, in words; empty when there is nothing to say
     */
    public static List<String> objectionsToSidecar(Map<String, ?> document) {
        List<String> out = new ArrayList<>();
        if (!(document.get("derivatives") instanceof List<?> derivatives)) {
            out.add("there is no list of derivatives");
        } else {
            if (derivatives.isEmpty()) {
                out.add("the list of derivatives is empty");
            }
            for (Object item : derivatives) {
                Object file = Documents.field(item, "file");
                Object name = Documents.field(item, "name");
                if (!Documents.given(file)) {
                    out.add("a derivative called " + (name == null ? "nothing" : Documents.text(name))
                            + " names no file");
                }
                if (!Documents.given(Documents.field(Documents.field(item, "digests"), "sha256"))) {
                    out.add(Documents.text(file == null ? name : file) + " carries no sha256");
                }
            }
        }
        if (!Documents.given(Documents.field(document.get("crop"), "rect"))) {
            out.add("there is no crop rect");
        }
        if (!Documents.given(Documents.field(document.get("capture"), "pixels"))) {
            out.add("there is no capture size");
        }
        return out;
    }

    /**
     * One line per file, for a person who wants to know what is in a delivery without opening the
     * json: the accession and the profile, the grading, then a line for each derivative and each
     * warning.
     */
    public static List<String> describeSidecar(Map<String, ?> document) {
        Object profile = Documents.field(document.get("profile"), "name");
        Object quality = document.get("quality");
        List<String> lines = new ArrayList<>();
        lines.add(Documents.text(document.get("accession")) + ", profile "
                + (profile == null ? "unknown" : Documents.text(profile)));
        lines.add(
                Documents.given(quality)
                        ? Documents.text(Documents.field(quality, "stars")) + " of 4 stars, "
                                + (Documents.given(Documents.field(quality, "passed")) ? "passed" : "held back")
                        : "not graded");
        for (Object item : Documents.listOf(document.get("derivatives"))) {
            Object ppi = Documents.field(Documents.field(item, "ppi"), "x");
            lines.add("  " + Documents.text(Documents.field(item, "file")) + ", "
                    + Documents.text(Documents.field(item, "format")) + ", "
                    + Documents.text(Documents.field(item, "bytes")) + " bytes, "
                    + (ppi == null ? "?" : Documents.text(ppi)) + " ppi");
        }
        for (Object warning : Documents.listOf(document.get("warnings"))) {
            lines.add("  warning: " + Documents.text(warning));
        }
        return lines;
    }

    private static Map<String, Object> build(FinishedRun run, String created) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", run.profile().name());
        profile.put("title", run.profile().title());

        List<Object> derivatives = new ArrayList<>();
        for (DerivedFile file : run.files()) {
            derivatives.add(derivativeOf(file));
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("version", (double) SIDECAR_VERSION);
        document.put("accession", run.accession());
        document.put("profile", profile);
        document.put("created", created);
        document.put("capture", captureOf(run.reading(), run.surface()));
        document.put("crop", cropOf(run.plan()));
        document.put("derivatives", derivatives);
        document.put("quality", run.quality().map(Sidecars::qualityOf).orElse(null));
        document.put("warnings", new ArrayList<Object>(run.warnings()));
        return ordered(document);
    }

    private static Map<String, Object> derivativeOf(DerivedFile file) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", file.name());
        item.put("file", file.fileName());
        item.put("format", file.format().label());
        item.put("ppi", pair(file.sampling().x(), file.sampling().y()));
        item.putAll(Checksums.fixityOf(file.bytes()).toJson());
        item.put("notes", new ArrayList<Object>(file.notes()));
        return item;
    }

    private static Map<String, Object> captureOf(CaptureReading reading, Surface surface) {
        Map<String, Object> pixels = new LinkedHashMap<>();
        pixels.put("width", (double) surface.width());
        pixels.put("height", (double) surface.height());

        Map<String, Object> capture = new LinkedHashMap<>();
        capture.put("pixels", pixels);
        capture.put("layout", surface.layout().label());
        capture.put("depth", (double) surface.depth());
        capture.put("skewDegrees", rounded(reading.sheet().skew().degrees(), 4));
        capture.put("confidence", rounded(reading.sheet().confidence(), 3));
        capture.put("spreadDegrees", rounded(reading.sheet().spread(), 4));
        capture.put("target", reading.target().isPresent());
        capture.put("neatline", reading.neatline().isPresent());
        capture.put("scaleBar", reading.scaleBar().isPresent());
        return capture;
    }

    private static Map<String, Object> cropOf(CropPlan plan) {
        Rect rect = plan.rect();
        Map<String, Object> box = new LinkedHashMap<>();
        box.put("x", rect.x());
        box.put("y", rect.y());
        box.put("width", rect.width());
        box.put("height", rect.height());

        Map<String, Object> crop = new LinkedHashMap<>();
        crop.put("rect", box);
        crop.put("deskewDegrees", rounded(plan.deskew().degrees(), 4));
        crop.put("ppi", pair(plan.sampling().x(), plan.sampling().y()));
        crop.put(
                "rules",
                new ArrayList<Object>(
                        plan.steps().stream().map(CropPlan.Step::rule).toList()));
        crop.put("overhangs", plan.overhangs());
        return crop;
    }

    private static Map<String, Object> pair(double x, double y) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", x);
        out.put("y", y);
        return out;
    }

    /** The value rounded to a number of places, the way a fixed-point figure is, then read back. */
    static double rounded(double value, int places) {
        return Double.parseDouble(NumberText.fixed(value, places));
    }

    /** The same fields with the fixed keys first, in their order, then any others in theirs. */
    private static Map<String, Object> ordered(Map<String, ?> document) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : ORDER) {
            if (document.containsKey(key)) {
                out.put(key, document.get(key));
            }
        }
        for (Map.Entry<String, ?> entry : document.entrySet()) {
            if (!out.containsKey(entry.getKey())) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }
}
