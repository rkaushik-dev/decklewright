package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.util.Json;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The house styles and the collections that ship with the tool.
 *
 * <p>These are the profiles the lab actually runs. They are here rather than in a data directory
 * because they are part of what the tool promises: a run that names one of these gets the same crop
 * it got last year, and a change to one of them is a change to the tool that has to go through
 * review.
 *
 * <p>Each is a written document, the plain data a profile file holds, and nobody can change one:
 * every map and list in it is read only. A site with its own collections writes its own files and
 * registers them.
 */
public final class BuiltIn {

    /** The base every other profile extends. Nothing here is collection specific. */
    public static final Map<String, Object> HOUSE = document(
            """
            {
              "name": "house",
              "title": "house style",
              "version": 2,
              "capture": {
                "sigma": 6,
                "speckle": 2,
                "gap": 3,
                "expectNeatline": false,
                "barLength": "6in",
                "claimedPpi": 400
              },
              "crop": {
                "anchor": "sheet",
                "bleed": "0mm",
                "safeArea": "0mm",
                "minimumPpi": 300,
                "snapTo": 2,
                "snapDirection": "up",
                "rounding": "outward",
                "background": 0,
                "deskew": true
              },
              "derivatives": [
                { "name": "master", "depth": "16", "layout": "rgb", "kernel": "lanczos3", "keepMetadata": true },
                { "name": "service", "longestSide": 3000, "depth": "8", "layout": "rgb", "kernel": "catmullRom" },
                { "name": "thumb", "longestSide": 400, "depth": "8", "layout": "rgb", "kernel": "box" }
              ],
              "qc": {
                "samplingPpi": 400,
                "samplingTolerance": "1%",
                "skewTolerance": 0.3,
                "illuminationTolerance": 0.1,
                "grayBalanceTolerance": 6,
                "minimumSnr": 30,
                "maximumClipping": 0.005,
                "minimumStars": 3
              },
              "naming": { "pattern": "{accession}_{derivative}", "lowercase": true }
            }
            """);

    /**
     * Bound volumes photographed on the copy stand. The paper edge is the crop, because a plate that
     * has been trimmed by a binder is still the whole plate. The stand is set to the 600 ppi the
     * plates are graded at, so a plate nobody measured is planned at what it was taken at.
     */
    public static final Map<String, Object> ATLAS_PLATES = document(
            """
            {
              "name": "atlas-plates",
              "title": "county atlas plates",
              "extends": "house",
              "capture": { "claimedPpi": 600 },
              "crop": {
                "bleed": "2mm",
                "minimumPpi": 400
              },
              "qc": {
                "samplingPpi": 600,
                "skewTolerance": 0.2
              }
            }
            """);

    /**
     * Quadrangle sheets. The crop is the neatline plus a strip of collar, so the legend at the bottom
     * survives and the grid ticks on the other three sides do not. A sheet whose border could not be
     * read at all is held rather than graded, since its crop stood on nothing.
     */
    public static final Map<String, Object> QUADRANGLES = document(
            """
            {
              "name": "quadrangles",
              "title": "usgs quadrangle sheets",
              "extends": "house",
              "capture": {
                "expectNeatline": true,
                "claimedPpi": 600
              },
              "crop": {
                "anchor": "neatline",
                "bleed": { "top": "3mm", "right": "3mm", "bottom": "38mm", "left": "3mm" },
                "minimumPpi": 500,
                "snapTo": 8
              },
              "qc": {
                "samplingPpi": 600,
                "skewTolerance": 0.15,
                "minimumStars": 4,
                "maximumClipping": 0.001,
                "required": ["sampling", "skew", "clipping", "neatline"]
              }
            }
            """);

    /**
     * Rolled drawings on the large flatbed. They relax slowly and the edges are rarely straight, so
     * the tolerances are wider and the crop leaves a margin.
     */
    public static final Map<String, Object> ROLLED_DRAWINGS = document(
            """
            {
              "name": "rolled-drawings",
              "title": "oversize rolled drawings",
              "extends": "house",
              "capture": {
                "sigma": 4,
                "gap": 6,
                "barLength": "12in",
                "claimedPpi": 300
              },
              "crop": {
                "bleed": "6mm",
                "minimumPpi": 250,
                "rounding": "outward"
              },
              "derivatives": [
                { "name": "master", "depth": "16", "layout": "rgb", "kernel": "lanczos3", "keepMetadata": true },
                { "name": "service", "longestSide": 4000, "depth": "8", "layout": "rgb", "kernel": "catmullRom" }
              ],
              "qc": {
                "samplingPpi": 300,
                "skewTolerance": 0.6,
                "minimumSnr": 24,
                "minimumStars": 2
              }
            }
            """);

    /** Microfilm frames, which arrive as grayscale and never carry a colour target. */
    public static final Map<String, Object> MICROFILM = document(
            """
            {
              "name": "microfilm",
              "title": "microfilm frames",
              "extends": "house",
              "capture": { "sigma": 8, "speckle": 1, "gap": 1, "claimedPpi": 300 },
              "crop": { "bleed": "0mm", "minimumPpi": 200, "snapTo": 2 },
              "derivatives": [
                { "name": "master", "depth": "8", "layout": "gray", "kernel": "lanczos3", "keepMetadata": true },
                { "name": "service", "longestSide": 2000, "depth": "8", "layout": "gray", "kernel": "catmullRom" }
              ],
              "qc": {
                "samplingPpi": 300,
                "grayBalanceTolerance": 255,
                "minimumSnr": 20,
                "minimumStars": 2
              }
            }
            """);

    /** Every profile that ships, in the order a listing shows them. */
    public static final List<Map<String, Object>> BUILT_IN =
            List.of(HOUSE, ATLAS_PLATES, QUADRANGLES, ROLLED_DRAWINGS, MICROFILM);

    private BuiltIn() {}

    /** The shipped profile of the given name; empty when none ships under it. */
    public static Optional<Map<String, Object>> builtInNamed(String name) {
        return BUILT_IN.stream()
                .filter(profile -> profile.get("name").equals(name))
                .findFirst();
    }

    /** The name of every shipped profile, in listing order. */
    public static List<String> builtInNames() {
        return BUILT_IN.stream().map(profile -> (String) profile.get("name")).toList();
    }

    private static Map<String, Object> document(String json) {
        return PlainData.frozen((Map<?, ?>) Json.parse(json));
    }
}
