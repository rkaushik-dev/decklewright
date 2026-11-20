package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything read off one capture: the four facts a crop plan needs, the target the quality checks
 * read tone off, and what could not be established.
 *
 * <p>Anything that was not found is empty, with a warning saying so, rather than a guess.
 *
 * @param sheet where the sheet is and how far it is turned
 * @param target the step wedge, when one was found
 * @param scaleBar the steel rule, when one was found
 * @param neatline the ruled border, when one was asked for; it may be incomplete
 * @param sampling the frequency measured off the rule, when the rule was found and its length known
 * @param warnings what the operator should know before trusting the reading; copied
 * @see Captures#readCapture(io.github.rkaushikdev.decklewright.raster.Surface)
 */
public record CaptureReading(
        SheetFinding sheet,
        Optional<TargetFinding> target,
        Optional<ScaleBarFinding> scaleBar,
        Optional<Neatline> neatline,
        Optional<Sampling> sampling,
        List<String> warnings)
        implements JsonValue {
    /** A reading of the given parts. */
    public CaptureReading {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(scaleBar, "scaleBar");
        Objects.requireNonNull(neatline, "neatline");
        Objects.requireNonNull(sampling, "sampling");
        warnings = List.copyOf(warnings);
    }

    /**
     * {@code {sheet, target, scaleBar, neatline, sampling, warnings}}, in a new map the caller may
     * keep, with null for each fact that was not established.
     */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("sheet", sheet.toJson());
        json.put("target", target.map(TargetFinding::toJson).orElse(null));
        json.put("scaleBar", scaleBar.map(ScaleBarFinding::toJson).orElse(null));
        json.put("neatline", neatline.map(Neatline::toJson).orElse(null));
        json.put("sampling", sampling.map(Sampling::toJson).orElse(null));
        json.put("warnings", new ArrayList<Object>(warnings));
        return json;
    }
}
