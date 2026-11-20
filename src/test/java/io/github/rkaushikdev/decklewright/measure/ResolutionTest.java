package io.github.rkaushikdev.decklewright.measure;

import static io.github.rkaushikdev.decklewright.measure.Sampling.achievedSampling;
import static io.github.rkaushikdev.decklewright.measure.Sampling.requiredPixels;
import static io.github.rkaushikdev.decklewright.measure.Sampling.toSampling;
import static io.github.rkaushikdev.decklewright.measure.Unit.INCH;
import static io.github.rkaushikdev.decklewright.measure.Unit.MILLIMETRE;
import static io.github.rkaushikdev.decklewright.measure.Unit.POINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResolutionTest {

    @Nested
    class SamplingBehaviour {
        @Test
        void mirrorsASingleFigureOntoBothAxes() {
            Sampling s = Sampling.of(600);
            assertThat(s.x()).isEqualTo(600);
            assertThat(s.y()).isEqualTo(600);
            assertThat(s.isSquare()).isTrue();
        }

        @Test
        void keepsNonSquarePixelsApart() {
            Sampling s = new Sampling(600, 400);
            assertThat(s.isSquare()).isFalse();
            assertThat(s.along(Axis.X)).isEqualTo(600);
            assertThat(s.along(Axis.Y)).isEqualTo(400);
        }

        @Test
        void isFrozenOnceBuilt() {
            Sampling s = Sampling.of(400);
            assertThat(s.x()).isEqualTo(400);
            // A record's components are final and these are primitives, so nothing can change them.
            assertThat(Sampling.class.isRecord()).isTrue();
        }

        @Test
        void refusesAFrequencyOfZeroOrLess() {
            assertThatThrownBy(() -> Sampling.of(0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("x must be greater than zero");
            assertThatThrownBy(() -> new Sampling(600, -1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("y must be greater than zero");
        }

        @Test
        void refusesAnAxisItDoesNotHave() {
            // The axis is typed, so the unknown label fails where it is read.
            assertThatThrownBy(() -> Sampling.of(600).along(Axis.parse("z")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("axis must be one of x, y");
        }
    }

    @Nested
    class ConvertingBetweenPixelsAndLength {
        private final Sampling s = Sampling.of(400);

        @Test
        void turnsInchesIntoPixels() {
            assertThat(s.toPixels(1, INCH)).isEqualTo(400);
            assertThat(s.toPixels(25.4, MILLIMETRE)).isCloseTo(400, byLessThan(5e-10));
        }

        @Test
        void turnsPixelsBackIntoLength() {
            assertThat(s.toLength(400, INCH)).isCloseTo(1, byLessThan(5e-13));
            assertThat(s.toLength(400, MILLIMETRE)).isCloseTo(25.4, byLessThan(5e-10));
        }

        @Test
        void usesTheRightAxisWhenThePixelsAreNotSquare() {
            Sampling odd = new Sampling(600, 300);
            assertThat(odd.toPixels(1, INCH, Axis.X)).isEqualTo(600);
            assertThat(odd.toPixels(1, INCH, Axis.Y)).isEqualTo(300);
            assertThat(odd.toLength(600, INCH, Axis.Y)).isEqualTo(2);
        }

        @Test
        void reportsSamplesPerUnit() {
            assertThat(Sampling.of(254).per(MILLIMETRE)).isCloseTo(10, byLessThan(5e-10));
            assertThat(Sampling.of(72).per(POINT)).isCloseTo(1, byLessThan(5e-10));
        }
    }

    @Nested
    class PpiFrom {
        @Test
        void calibratesFromAMeasuredObject() {
            assertThat(Sampling.ppiFrom(6800, 17, INCH)).isEqualTo(400);
            assertThat(Sampling.ppiFrom(1000, 25.4, MILLIMETRE)).isCloseTo(1000, byLessThan(5e-10));
        }

        @Test
        void refusesAZeroLengthOrZeroPixelCount() {
            assertThatThrownBy(() -> Sampling.ppiFrom(0, 17, INCH))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("pixels must be greater than zero");
            assertThatThrownBy(() -> Sampling.ppiFrom(10, 0, INCH))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("length must be greater than zero");
        }
    }

    @Nested
    class ScaledAndEquals {
        @Test
        void scalesBothAxesTogether() {
            Sampling half = new Sampling(600, 300).scaled(0.5);
            assertThat(half.x()).isEqualTo(300);
            assertThat(half.y()).isEqualTo(150);
        }

        @Test
        void comparesWithinAToleranceInPpi() {
            assertThat(Sampling.of(400).approxEquals(Sampling.of(400))).isTrue();
            assertThat(Sampling.of(400).approxEquals(Sampling.of(400.5))).isFalse();
            assertThat(Sampling.of(400).approxEquals(Sampling.of(400.5), 1)).isTrue();
            // approxEquals only takes a Sampling, so the bare number goes through equals, and null
            // through approxEquals.
            assertThat(Sampling.of(400).equals(400.0)).isFalse();
            assertThat(Sampling.of(400).approxEquals(null)).isFalse();
        }
    }

    @Nested
    class Serialisation {
        @Test
        void printsOneFigureForSquarePixelsAndTwoOtherwise() {
            assertThat(String.valueOf(Sampling.of(600))).isEqualTo("600 ppi");
            assertThat(String.valueOf(new Sampling(600, 300))).isEqualTo("600x300 ppi");
            assertThat(String.valueOf(Sampling.of(599.996))).isEqualTo("600 ppi");
        }

        @Test
        void serialisesToAFlatPair() {
            assertThat(new Sampling(600, 300).toJson()).isEqualTo(Map.of("x", 600.0, "y", 300.0));
        }
    }

    @Nested
    class ToSampling {
        @Test
        void passesAnInstanceThroughUntouched() {
            Sampling s = Sampling.of(400);
            assertThat(toSampling(s)).isSameAs(s);
        }

        @Test
        void acceptsABareNumberAndAPlainPair() {
            assertThat(toSampling(400).x()).isEqualTo(400);
            assertThat(toSampling(Map.of("x", 600.0, "y", 300.0)).y()).isEqualTo(300);
        }

        @Test
        void refusesAnythingElse() {
            assertThatThrownBy(() -> toSampling("400"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read a sampling frequency");
            assertThatThrownBy(() -> toSampling(Map.of("x", 400.0)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read a sampling frequency");
        }
    }

    @Nested
    class RequiredPixels {
        @Test
        void roundsUpSoTheFloorIsNeverMissedByAPixel() {
            assertThat(requiredPixels(17, INCH, 400)).isEqualTo(6800);
            assertThat(requiredPixels(17.001, INCH, 400)).isEqualTo(6801);
        }

        @Test
        void worksFromMillimetres() {
            assertThat(requiredPixels(431.8, MILLIMETRE, 400)).isEqualTo(6800);
        }
    }

    @Nested
    class AchievedSampling {
        @Test
        void saysWhatARenderedDerivativeActuallyReached() {
            assertThat(achievedSampling(3400, 17, INCH).x()).isEqualTo(200);
        }
    }
}
