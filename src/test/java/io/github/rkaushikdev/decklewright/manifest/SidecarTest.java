package io.github.rkaushikdev.decklewright.manifest;

import static io.github.rkaushikdev.decklewright.manifest.Sidecars.SIDECAR_VERSION;
import static io.github.rkaushikdev.decklewright.manifest.Sidecars.describeSidecar;
import static io.github.rkaushikdev.decklewright.manifest.Sidecars.objectionsToSidecar;
import static io.github.rkaushikdev.decklewright.manifest.Sidecars.parseSidecar;
import static io.github.rkaushikdev.decklewright.manifest.Sidecars.qualityOf;
import static io.github.rkaushikdev.decklewright.manifest.Sidecars.sidecarFor;
import static io.github.rkaushikdev.decklewright.manifest.Sidecars.writeSidecar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.ManifestError;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.QcResult;
import io.github.rkaushikdev.decklewright.testing.Accession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The sidecar: built from a run, written in a fixed form, read back, objected to and described. */
class SidecarTest {

    private static final Instant CREATED = Instant.parse("2025-11-04T09:15:00Z");

    private static Map<String, Object> sidecar(Accession.Options options) {
        return sidecarFor(Accession.worked(options).run(), CREATED);
    }

    private static Map<String, Object> sidecar() {
        return sidecar(Accession.Options.defaults());
    }

    /** The value at a path of member names and list indexes. */
    private static Object at(Object document, Object... path) {
        Object value = document;
        for (Object step : path) {
            value = step instanceof Integer index ? ((List<?>) value).get(index) : ((Map<?, ?>) value).get(step);
        }
        return value;
    }

    private static double number(Object document, Object... path) {
        return ((Number) at(document, path)).doubleValue();
    }

    private static Map<String, Object> pair(String first, Object one, String second, Object two) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(first, one);
        out.put(second, two);
        return out;
    }

    /** Derivatives, a crop rect and a capture size, the parts the objections look for. */
    private static Map<String, Object> handWritten(List<?> derivatives) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("derivatives", derivatives);
        document.put("crop", Map.of("rect", Map.of()));
        document.put("capture", Map.of("pixels", Map.of()));
        return document;
    }

    @Nested
    class SidecarFor {
        @Test
        void namesTheAccessionAndTheProfile() {
            Map<String, Object> out = sidecar();
            assertThat(out.get("accession")).isEqualTo("g4181-1907");
            assertThat(out.get("profile")).isEqualTo(pair("name", "house", "title", "house style"));
        }

        @Test
        void saysWhichVersionWroteIt() {
            assertThat(sidecar().get("version")).isEqualTo((double) SIDECAR_VERSION);
        }

        @Test
        void writesTheKeysInAFixedOrder() {
            assertThat(new ArrayList<>(sidecar().keySet()).subList(0, 4))
                    .isEqualTo(List.of("version", "accession", "profile", "created"));
        }

        @Test
        void recordsTheCaptureAsItArrived() {
            Map<String, Object> out = sidecar();
            assertThat(at(out, "capture", "pixels")).isEqualTo(pair("width", 300.0, "height", 240.0));
            assertThat(at(out, "capture", "layout")).isEqualTo("rgb");
            assertThat(at(out, "capture", "target")).isEqualTo(true);
        }

        @Test
        void recordsTheCropAndHowItGotThere() {
            Map<String, Object> out = sidecar();
            assertThat(number(out, "crop", "rect", "width")).isGreaterThan(140);
            assertThat(at(out, "crop", "rules", 0)).isEqualTo("start");
            assertThat(at(out, "crop", "ppi")).isEqualTo(pair("x", 400.0, "y", 400.0));
        }

        @Test
        void recordsTheTurnThatWasTakenOut() {
            assertThat(at(sidecar(Accession.Options.defaults().withDegrees(3)), "crop", "deskewDegrees"))
                    .isNotEqualTo(0.0);
        }

        @Test
        void listsEveryDerivativeWithItsOwnFrequencyAndDigest() {
            Map<String, Object> out = sidecar();
            assertThat((List<?>) out.get("derivatives")).hasSize(3);
            assertThat((String) at(out, "derivatives", 0, "digests", "sha256")).hasSize(64);
            assertThat(number(out, "derivatives", 2, "ppi", "x"))
                    .isLessThanOrEqualTo(number(out, "derivatives", 0, "ppi", "x"));
        }

        @Test
        void carriesTheGradingWhenThereWasOne() {
            Map<String, Object> out = sidecar();
            assertThat(number(out, "quality", "stars")).isGreaterThanOrEqualTo(0);
            assertThat((List<?>) at(out, "quality", "checks")).hasSize(11);
        }

        @Test
        void saysNothingRatherThanNullsWhenThereWasNoGrading() {
            assertThat(sidecar(Accession.Options.defaults().withGraded(false))).containsEntry("quality", null);
        }

        @Test
        void writesTheTimeItWasToldNotTheTimeItIs() {
            assertThat(sidecar().get("created")).isEqualTo("2025-11-04T09:15:00.000Z");
        }

        @Test
        void writesNumbersAsNumbers() {
            Map<String, Object> out = sidecar();
            assertThat(at(out, "crop", "rect", "width")).isInstanceOf(Double.class);
            assertThat(at(out, "derivatives", 0, "bytes")).isInstanceOf(Double.class);
        }
    }

    @Nested
    class QualityOf {
        @Test
        void keepsAMeasurementThatIsNotANumberOutOfTheDocument() {
            QcResult result = new QcResult(
                    List.of(new Finding("noise", false, false, Double.NaN, 0, "no target", "")),
                    0,
                    Optional.of("noise"),
                    List.of(),
                    false,
                    3);
            assertThat(at(qualityOf(result), "checks", 0, "measured")).isNull();
        }

        @Test
        void copiesTheMissingListRatherThanSharingIt() {
            List<String> missing = new ArrayList<>(List.of("sampling"));
            Map<String, Object> out = qualityOf(new QcResult(List.of(), 0, Optional.empty(), missing, false, 3));
            missing.add("skew");
            assertThat(out.get("missing")).isEqualTo(List.of("sampling"));
        }
    }

    @Nested
    class WriteSidecar {
        @Test
        void endsWithANewline() {
            assertThat(writeSidecar(sidecar()).endsWith("}\n")).isTrue();
        }

        @Test
        void givesTheSameBytesForTheSameRunTwice() {
            assertThat(writeSidecar(sidecar())).isEqualTo(writeSidecar(sidecar()));
        }

        @Test
        void putsTheKeysInOrderEvenForADocumentAssembledByHand() {
            String text = writeSidecar(pair("accession", "a", "version", 2.0));
            assertThat(text.indexOf("version")).isLessThan(text.indexOf("accession"));
        }
    }

    @Nested
    class ParseSidecar {
        @Test
        void readsBackWhatItWrote() {
            Map<String, Object> document = sidecar();
            assertThat(parseSidecar(writeSidecar(document)).get("accession")).isEqualTo("g4181-1907");
        }

        @Test
        void refusesTextThatIsNotJson() {
            assertThatThrownBy(() -> parseSidecar("not json at all"))
                    .isInstanceOf(ManifestError.class)
                    .hasMessageFindingMatch("not even json");
        }

        @Test
        void refusesJsonThatIsNotAnObject() {
            assertThatThrownBy(() -> parseSidecar("[1, 2]"))
                    .isInstanceOf(ManifestError.class)
                    .hasMessageFindingMatch("has to be an object");
        }

        @Test
        void refusesADocumentWithNoVersion() {
            assertThatThrownBy(() -> parseSidecar("{\"accession\": \"a\"}"))
                    .isInstanceOf(ManifestError.class)
                    .hasMessageFindingMatch("does not say what version");
        }

        @Test
        void refusesADocumentFromALaterVersionRatherThanHalfReadingIt() {
            assertThatThrownBy(() -> parseSidecar("{\"version\": 99, \"accession\": \"a\"}"))
                    .isInstanceOf(ManifestError.class)
                    .hasMessageFindingMatch("later version");
        }

        @Test
        void readsADocumentFromAnEarlierVersion() {
            assertThat(parseSidecar("{\"version\": 1, \"accession\": \"a\"}").get("accession"))
                    .isEqualTo("a");
        }

        @Test
        void refusesADocumentThatNamesNoAccession() {
            assertThatThrownBy(() -> parseSidecar("{\"version\": 2}"))
                    .isInstanceOf(ManifestError.class)
                    .hasMessageFindingMatch("names no accession");
        }
    }

    @Nested
    class ObjectionsToSidecar {
        @Test
        void hasNothingToSayAboutOneItWroteItself() {
            assertThat(objectionsToSidecar(sidecar())).isEqualTo(List.of());
        }

        @Test
        void saysSoWhenThereAreNoDerivatives() {
            assertThat(objectionsToSidecar(pair("version", 2.0, "accession", "a"))
                            .get(0))
                    .containsPattern("no list of derivatives");
        }

        @Test
        void saysSoWhenTheListIsEmpty() {
            assertThat(objectionsToSidecar(handWritten(List.of())))
                    .isEqualTo(List.of("the list of derivatives is empty"));
        }

        @Test
        void namesADerivativeWithNoDigest() {
            Map<String, Object> derivative = new LinkedHashMap<>();
            derivative.put("name", "master");
            derivative.put("file", "a.png");
            derivative.put("digests", Map.of());
            assertThat(objectionsToSidecar(handWritten(List.of(derivative))))
                    .isEqualTo(List.of("a.png carries no sha256"));
        }

        @Test
        void collectsEveryFaultRatherThanStoppingAtTheFirst() {
            Map<String, Object> document = Map.of("derivatives", List.of(Map.of("name", "master")));
            assertThat(objectionsToSidecar(document).size()).isGreaterThan(2);
        }
    }

    @Nested
    class DescribeSidecar {
        @Test
        void opensWithTheAccessionAndTheProfile() {
            assertThat(describeSidecar(sidecar()).get(0)).isEqualTo("g4181-1907, profile house");
        }

        @Test
        void saysWhetherItWasGraded() {
            assertThat(describeSidecar(sidecar(Accession.Options.defaults().withGraded(false)))
                            .get(1))
                    .isEqualTo("not graded");
            assertThat(describeSidecar(sidecar()).get(1)).containsPattern("of 4 stars");
        }

        @Test
        void writesOneLinePerFile() {
            List<String> lines = describeSidecar(sidecar());
            assertThat(lines.stream().filter(line -> line.contains(".png"))).hasSize(3);
        }
    }
}
