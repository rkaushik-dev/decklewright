package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.Backgrounds.objectMask;
import static io.github.rkaushikdev.decklewright.detect.Captures.componentIdAt;
import static io.github.rkaushikdev.decklewright.detect.Captures.isUsable;
import static io.github.rkaushikdev.decklewright.detect.Captures.readCapture;
import static io.github.rkaushikdev.decklewright.detect.Captures.summarise;
import static io.github.rkaushikdev.decklewright.detect.Sheets.findSheet;
import static io.github.rkaushikdev.decklewright.testing.Bench.bench;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Unit;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.Bench;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CaptureTest {

    private static final CaptureOptions SIGMA_4 = CaptureOptions.defaults().withSigma(4);

    /** A full bench: a sheet, a step wedge above it and a steel rule below. */
    static Surface fullBench() {
        return fullBench(0, true, true, false);
    }

    /** The full bench with no wedge. */
    static Surface withoutWedge() {
        return fullBench(0, false, true, false);
    }

    /** The full bench with no rule. */
    static Surface withoutRule() {
        return fullBench(0, true, false, false);
    }

    /** The full bench with a neatline drawn on the sheet. */
    static Surface ruled() {
        return fullBench(0, true, true, true);
    }

    /** The full bench with the sheet turned. */
    static Surface turned(double degrees) {
        return fullBench(degrees, true, true, false);
    }

    /**
     * A 260 by 220 bench with a 140 by 100 sheet centred at (140, 110), and the extras asked for.
     *
     * @param degrees how far the sheet is turned
     * @param wedge put an eight step wedge above the sheet
     * @param rule put a steel rule below the sheet
     * @param ruled draw a neatline on the sheet
     */
    static Surface fullBench(double degrees, boolean wedge, boolean rule, boolean ruled) {
        Surface surface = bench(Bench.Options.defaults()
                .withWidth(260)
                .withHeight(220)
                .withDegrees(degrees)
                .withHalfWidth(70)
                .withHalfHeight(50)
                .withCentre(140, 110));

        if (wedge) {
            for (int i = 0; i < 8; i += 1) {
                double tone = 20 + Num.roundHalfUp((i / 7.0) * 210);
                paint(surface, Rect.of(14 + i * 10, 12, 10, 14), tone);
            }
        }
        if (rule) {
            paint(surface, Rect.of(20, 200, 120, 7), 190);
        }
        if (ruled) {
            paint(surface, Rect.of(85, 75, 110, 2), 30);
            paint(surface, Rect.of(85, 135, 110, 2), 30);
            paint(surface, Rect.of(85, 75, 2, 62), 30);
            paint(surface, Rect.of(193, 75, 2, 62), 30);
        }
        return surface;
    }

    /** The reading with its sheet replaced by one of another confidence and edge flag. */
    static CaptureReading withSheet(CaptureReading reading, double confidence, boolean touchesEdge) {
        SheetFinding sheet = reading.sheet();
        SheetFinding changed = new SheetFinding(
                sheet.quad(),
                sheet.skew(),
                sheet.mask(),
                confidence,
                sheet.spread(),
                touchesEdge,
                sheet.coverage(),
                sheet.readings());
        return new CaptureReading(
                changed,
                reading.target(),
                reading.scaleBar(),
                reading.neatline(),
                reading.sampling(),
                reading.warnings());
    }

    @Nested
    class ReadCapture {
        @Test
        void findsTheSheetTheTargetAndTheRule() {
            CaptureReading reading = readCapture(fullBench(), SIGMA_4);
            assertThat(reading.sheet().confidence()).isGreaterThan(0.5);
            assertThat(reading.target()).isPresent();
            assertThat(reading.scaleBar()).isPresent();
            assertThat(reading.warnings()).isEqualTo(List.of());
        }

        @Test
        void doesNotMistakeTheSheetForTheTargetOrTheRule() {
            CaptureReading reading = readCapture(fullBench(), SIGMA_4);
            Rect sheetBox = reading.sheet().quad().boundingRect();
            assertThat(reading.target().orElseThrow().rect().intersects(sheetBox))
                    .isFalse();
            assertThat(reading.scaleBar().orElseThrow().rect().intersects(sheetBox))
                    .isFalse();
        }

        @Test
        void warnsWhenTheTargetIsMissing() {
            CaptureReading reading = readCapture(withoutWedge(), SIGMA_4);
            assertThat(reading.target()).isEmpty();
            assertThat(reading.warnings()).contains("no colour target on the bed");
        }

        @Test
        void warnsWhenTheRuleIsMissing() {
            CaptureReading reading = readCapture(withoutRule(), SIGMA_4);
            assertThat(reading.scaleBar()).isEmpty();
            assertThat(reading.warnings()).contains("no scale bar on the bed");
        }

        @Test
        void measuresTheSamplingFrequencyOffTheRule() {
            CaptureReading reading =
                    readCapture(fullBench(), SIGMA_4.withBarLength(6).withBarUnit(Unit.INCH));
            assertThat(reading.sampling().orElseThrow().x()).isCloseTo(20, byLessThan(0.5));
        }

        @Test
        void saysNothingAboutFrequencyWhenTheRuleLengthIsUnknown() {
            assertThat(readCapture(fullBench(), SIGMA_4).sampling()).isEmpty();
        }

        @Test
        void complainsWhenTheRuleDisagreesWithTheScanner() {
            CaptureReading reading = readCapture(
                    fullBench(), SIGMA_4.withBarLength(6).withBarUnit(Unit.INCH).withClaimed(Sampling.of(400)));
            assertThat(String.join(" ", reading.warnings())).containsPattern("against a claimed 400");
        }

        @Test
        void acceptsARuleThatAgreesWithTheScanner() {
            CaptureReading reading = readCapture(
                    fullBench(), SIGMA_4.withBarLength(6).withBarUnit(Unit.INCH).withClaimed(Sampling.of(20)));
            assertThat(String.join(" ", reading.warnings())).doesNotContainPattern("against a claimed");
        }

        @Test
        void looksForANeatlineOnlyWhenToldTo() {
            assertThat(readCapture(ruled(), SIGMA_4).neatline()).isEmpty();
            CaptureReading reading = readCapture(ruled(), SIGMA_4.withExpectNeatline(true));
            assertThat(reading.neatline()).isPresent();
        }

        @Test
        void warnsAboutTheSidesOfANeatlineItCouldNotFind() {
            CaptureReading reading = readCapture(fullBench(), SIGMA_4.withExpectNeatline(true));
            assertThat(String.join(" ", reading.warnings())).containsPattern("no neatline found");
        }

        @Test
        void readsATurnedSheetAsTurned() {
            CaptureReading reading = readCapture(turned(5), SIGMA_4);
            assertThat(Math.abs(reading.sheet().skew().degrees() - 5)).isLessThan(1);
        }
    }

    @Nested
    class ComponentIdAt {
        @Test
        void findsTheBlobTheSheetCentreSitsIn() {
            Surface surface = fullBench();
            SheetFinding sheet = findSheet(surface, SheetOptions.defaults().withSigma(4));
            Mask mask = objectMask(surface, 4).mask();
            OptionalInt id = componentIdAt(mask, sheet);
            assertThat(id).isPresent();
            assertThat(id.getAsInt()).isGreaterThan(0);
        }

        @Test
        void givesNothingBackWhenTheCentreIsNotOnAnyBlob() {
            Surface surface = fullBench();
            SheetFinding sheet = findSheet(surface, SheetOptions.defaults().withSigma(4));
            Mask mask = objectMask(surface, 4).mask();
            mask.data().fill(0);
            assertThat(componentIdAt(mask, sheet)).isEmpty();
        }
    }

    @Nested
    class Summarise {
        @Test
        void writesALineForEachFactItEstablished() {
            List<String> lines = summarise(readCapture(fullBench(), SIGMA_4.withBarLength(6)));
            assertThat(lines.get(0)).matches("sheet \\d+x\\d+ at -?\\d+\\.\\d\\d deg");
            assertThat(String.join("\n", lines)).containsPattern("target over \\d+ levels");
            assertThat(String.join("\n", lines)).containsPattern("rule \\d+ px long");
            assertThat(String.join("\n", lines)).containsPattern("measured .* ppi");
        }

        @Test
        void writesTheWarningsOutToo() {
            List<String> lines = summarise(readCapture(withoutRule(), SIGMA_4));
            assertThat(lines).contains("warning: no scale bar on the bed");
        }

        @Test
        void reportsTheCollarWhenANeatlineWasAskedFor() {
            List<String> lines = summarise(readCapture(ruled(), SIGMA_4.withExpectNeatline(true)));
            assertThat(String.join("\n", lines)).containsPattern("collar \\d+/\\d+/\\d+/\\d+");
        }
    }

    @Nested
    class IsUsable {
        @Test
        void acceptsACleanCapture() {
            assertThat(isUsable(readCapture(fullBench(), SIGMA_4))).isTrue();
        }

        @Test
        void rejectsACaptureWhoseSheetRanOffTheBed() {
            CaptureReading reading = readCapture(fullBench(), SIGMA_4);
            assertThat(isUsable(withSheet(reading, reading.sheet().confidence(), true)))
                    .isFalse();
        }

        @Test
        void rejectsACaptureTheDetectorWasUnsureAbout() {
            CaptureReading reading = readCapture(fullBench(), SIGMA_4);
            assertThat(isUsable(withSheet(reading, 0.2, reading.sheet().touchesEdge())))
                    .isFalse();
        }
    }
}
