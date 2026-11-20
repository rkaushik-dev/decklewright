package io.github.rkaushikdev.decklewright.measure;

import static io.github.rkaushikdev.decklewright.measure.PhysicalSizes.achievedFrom;
import static io.github.rkaushikdev.decklewright.measure.PhysicalSizes.describeSize;
import static io.github.rkaushikdev.decklewright.measure.PhysicalSizes.inUnit;
import static io.github.rkaushikdev.decklewright.measure.PhysicalSizes.matchNominal;
import static io.github.rkaushikdev.decklewright.measure.PhysicalSizes.nominalInPixels;
import static io.github.rkaushikdev.decklewright.measure.PhysicalSizes.physicalSize;
import static io.github.rkaushikdev.decklewright.measure.PhysicalSizes.pixelSize;
import static io.github.rkaushikdev.decklewright.measure.PhysicalSizes.sizeError;
import static io.github.rkaushikdev.decklewright.measure.Unit.INCH;
import static io.github.rkaushikdev.decklewright.measure.Unit.MILLIMETRE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.measure.PhysicalSizes.Mismatch;
import io.github.rkaushikdev.decklewright.measure.PhysicalSizes.NominalMatch;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The conversions take a {@link Sampling}, so a bare frequency in ppi is written here as {@code
 * Sampling.of(ppi)}.
 */
class PhysicalTest {

    @Nested
    class PhysicalSizeBehaviour {
        @Test
        void turnsPixelsIntoInchesAtAFrequency() {
            PhysicalSize size = physicalSize(Size.of(6800, 5600), Sampling.of(400));
            assertThat(size).isEqualTo(new PhysicalSize(17, 14, INCH));
        }

        @Test
        void answersInWhateverUnitIsAskedFor() {
            PhysicalSize size = physicalSize(Size.of(400, 400), Sampling.of(400), MILLIMETRE);
            assertThat(size.width()).isCloseTo(25.4, byLessThan(5e-10));
            assertThat(size.unit()).isEqualTo(MILLIMETRE);
        }

        @Test
        void usesEachAxisOfANonSquareFrequency() {
            PhysicalSize size = physicalSize(Size.of(600, 300), new Sampling(600, 300));
            assertThat(size).isEqualTo(new PhysicalSize(1, 1, INCH));
        }

        @Test
        void refusesAUnitItDoesNotKnow() {
            // The unit is typed, so the unknown label fails where it is read.
            assertThatThrownBy(() -> physicalSize(Size.of(1, 1), Sampling.of(400), Unit.parse("px")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("unknown unit px");
        }
    }

    @Nested
    class PixelSize {
        @Test
        void isTheInverseOfPhysicalSize() {
            Size pixels = Size.of(6800, 5600);
            assertThat(pixelSize(physicalSize(pixels, Sampling.of(400)), Sampling.of(400))
                            .approxEquals(pixels, 1e-6))
                    .isTrue();
        }

        @Test
        void worksFromMillimetres() {
            Size pixels = pixelSize(new PhysicalSize(25.4, 25.4, MILLIMETRE), Sampling.of(400));
            assertThat(pixels.width()).isCloseTo(400, byLessThan(5e-7));
        }
    }

    @Nested
    class InUnit {
        @Test
        void returnsTheSameObjectWhenTheUnitAlreadyMatches() {
            PhysicalSize size = new PhysicalSize(17, 14, INCH);
            assertThat(inUnit(size, INCH)).isSameAs(size);
        }

        @Test
        void convertsBothSides() {
            PhysicalSize size = inUnit(new PhysicalSize(17, 14, INCH), MILLIMETRE);
            assertThat(size.width()).isCloseTo(431.8, byLessThan(5e-7));
            assertThat(size.height()).isCloseTo(355.6, byLessThan(5e-7));
        }
    }

    @Nested
    class SizeError {
        @Test
        void isZeroForAnExactMatch() {
            PhysicalSize size = new PhysicalSize(17, 14, INCH);
            assertThat(sizeError(size, size)).isEqualTo(new Mismatch(0, false));
        }

        @Test
        void takesTheWorseOfTheTwoSides() {
            Mismatch out = sizeError(new PhysicalSize(17.1, 14.4, INCH), new PhysicalSize(17, 14, INCH));
            assertThat(out.error()).isCloseTo(0.4, byLessThan(5e-10));
            assertThat(out.rotated()).isFalse();
        }

        @Test
        void spotsASheetPlacedTheOtherWayRound() {
            Mismatch out = sizeError(new PhysicalSize(14, 17, INCH), new PhysicalSize(17, 14, INCH));
            assertThat(out.error()).isCloseTo(0, byLessThan(5e-10));
            assertThat(out.rotated()).isTrue();
        }

        @Test
        void comparesAcrossUnits() {
            Mismatch out = sizeError(new PhysicalSize(431.8, 355.6, MILLIMETRE), new PhysicalSize(17, 14, INCH));
            assertThat(out.error()).isLessThan(0.001);
        }
    }

    @Nested
    class MatchNominal {
        @Test
        void namesAnAtlasPlate() {
            NominalMatch found =
                    matchNominal(new PhysicalSize(17.1, 13.9, INCH)).orElseThrow();
            assertThat(found.name()).isEqualTo("atlasPlate");
            assertThat(found.rotated()).isFalse();
        }

        @Test
        void namesAPlatBookWhichIsTheSamePlateTurned() {
            NominalMatch found = matchNominal(new PhysicalSize(14, 17, INCH)).orElseThrow();
            assertThat(found.name()).isNotEmpty();
            assertThat(found.error()).isLessThan(0.01);
        }

        @Test
        void namesAnArchitecturalSheet() {
            assertThat(matchNominal(new PhysicalSize(24, 36, INCH)).map(NominalMatch::name))
                    .contains("archD");
            assertThat(matchNominal(new PhysicalSize(36, 48, INCH)).map(NominalMatch::name))
                    .contains("archE");
        }

        @Test
        void namesNothingForAnOddSize() {
            assertThat(matchNominal(new PhysicalSize(9, 6, INCH))).isEmpty();
        }

        @Test
        void honoursTheTolerance() {
            PhysicalSize measured = new PhysicalSize(17.3, 14, INCH);
            assertThat(matchNominal(measured)).isPresent();
            assertThat(matchNominal(measured, 0.2)).isEmpty();
        }

        @Test
        void picksTheCloserOfTwoCandidates() {
            Optional<NominalMatch> found = matchNominal(new PhysicalSize(21.4, 27, INCH), 1);
            assertThat(found.map(NominalMatch::name)).contains("quad15");
        }
    }

    @Nested
    class NominalInPixels {
        @Test
        void sizesANominalSheetAtAFrequency() {
            assertThat(nominalInPixels("archD", Sampling.of(400)).toJson())
                    .isEqualTo(Map.of("width", 9600.0, "height", 14400.0));
        }

        @Test
        void refusesANameItDoesNotKnow() {
            assertThatThrownBy(() -> nominalInPixels("tabloid", Sampling.of(400)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no nominal size called tabloid");
        }
    }

    @Nested
    class AchievedFrom {
        @Test
        void reportsWhatACaptureOfAKnownObjectReallyReached() {
            Sampling sampling = achievedFrom(Size.of(6800, 5600), new PhysicalSize(17, 14, INCH));
            assertThat(sampling.x()).isEqualTo(400);
            assertThat(sampling.y()).isEqualTo(400);
        }

        @Test
        void reportsEachAxisSeparatelyWhenTheyDiffer() {
            Sampling sampling = achievedFrom(Size.of(6800, 2800), new PhysicalSize(17, 14, INCH));
            assertThat(sampling.x()).isEqualTo(400);
            assertThat(sampling.y()).isEqualTo(200);
            assertThat(sampling.isSquare()).isFalse();
        }

        @Test
        void refusesAPhysicalSizeWithASideAtZero() {
            assertThatThrownBy(() -> achievedFrom(Size.of(10, 10), new PhysicalSize(0, 1, INCH)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("both sides above zero");
        }
    }

    @Nested
    class DescribeSize {
        @Test
        void writesBothSidesWithTheUnit() {
            assertThat(describeSize(new PhysicalSize(17, 14, INCH))).isEqualTo("17.0000 in by 14.0000 in");
            assertThat(describeSize(new PhysicalSize(431.8, 355.6, MILLIMETRE), 1))
                    .isEqualTo("431.8 mm by 355.6 mm");
        }
    }
}
