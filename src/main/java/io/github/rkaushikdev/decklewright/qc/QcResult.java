package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What grading one capture found: every finding, and the level the capture delivers at once the
 * required checks are taken into account.
 *
 * <p>This is what the rest of the tree reads: the sidecar's quality section, the pipeline policy
 * that sets the stars against the minimum, and the store's rejections.
 *
 * @param findings one per check, in the order the checks ran; copied
 * @param stars the level, zero when a required check could not run
 * @param worst the check holding the level down, or the first required check that could not run;
 *     empty when nothing ran
 * @param missing the required checks that could not run; copied
 * @param passed true when the level reaches the one wanted
 * @param wanted the level the profile asked for
 * @see QcRunner#runQc(QcInput, QcOptions)
 */
public record QcResult(
        List<Finding> findings, int stars, Optional<String> worst, List<String> missing, boolean passed, int wanted)
        implements Graded, JsonValue {

    /** A result of the given parts. */
    public QcResult {
        findings = List.copyOf(findings);
        Objects.requireNonNull(worst, "worst");
        missing = List.copyOf(missing);
    }

    /**
     * {@code {findings, stars, worst, missing, passed, wanted}}, in a new map the caller may keep,
     * with null for a worst check that is not there and every number a {@link Double}.
     */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        List<Object> checks = new ArrayList<>(findings.size());
        findings.forEach(finding -> checks.add(finding.toJson()));
        json.put("findings", checks);
        json.put("stars", (double) stars);
        json.put("worst", worst.orElse(null));
        json.put("missing", new ArrayList<Object>(missing));
        json.put("passed", passed);
        json.put("wanted", (double) wanted);
        return json;
    }
}
