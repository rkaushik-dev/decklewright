package io.github.rkaushikdev.decklewright.measure;

import static io.github.rkaushikdev.decklewright.measure.Tolerance.toTolerance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.measure.Tolerance.Bounds;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToleranceTest {

    @Nested
    class BuildingABand {
        @Test
        void keepsTheTwoSidesApart() {
            Tolerance t = new Tolerance(1, 3);
            assertThat(t.minus()).isEqualTo(1);
            assertThat(t.plus()).isEqualTo(3);
            assertThat(t.isSymmetric()).isFalse();
            // A record's components are final and these are primitives and an enum, so nothing can change them.
            assertThat(Tolerance.class.isRecord()).isTrue();
        }

        @Test
        void hasTheShorthandsTheProfilesUse() {
            assertThat(Tolerance.symmetric(2).isSymmetric()).isTrue();
            assertThat(Tolerance.exact().isExact()).isTrue();
            assertThat(Tolerance.upOnly(2).minus()).isEqualTo(0);
            assertThat(Tolerance.downOnly(2).plus()).isEqualTo(0);
            assertThat(Tolerance.percent(5).plus()).isCloseTo(0.05, byLessThan(5e-13));
            assertThat(Tolerance.percent(5).kind()).isEqualTo(ToleranceKind.RELATIVE);
        }

        @Test
        void refusesANegativeSideSinceTheSidesAreMagnitudes() {
            assertThatThrownBy(() -> new Tolerance(-1, 1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("minus must not be negative");
            assertThatThrownBy(() -> new Tolerance(1, -1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("plus must not be negative");
        }

        @Test
        void refusesAnUnknownKind() {
            // The kind is typed, so the unknown label fails where it is read.
            assertThatThrownBy(() -> new Tolerance(1, 1, ToleranceKind.parse("fractional")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("kind must be one of");
        }
    }

    @Nested
    class Parse {
        @Test
        void readsABareNumberAsSymmetricAndAbsolute() {
            Tolerance t = Tolerance.parse("0.5");
            assertThat(t.toJson()).isEqualTo(Map.of("minus", 0.5, "plus", 0.5, "kind", "absolute"));
        }

        @Test
        void readsASignedNumberAsOneSided() {
            assertThat(Tolerance.parse("+2").toJson()).isEqualTo(Map.of("minus", 0.0, "plus", 2.0, "kind", "absolute"));
            assertThat(Tolerance.parse("-2").toJson()).isEqualTo(Map.of("minus", 2.0, "plus", 0.0, "kind", "absolute"));
        }

        @Test
        void readsAPercentage() {
            Tolerance t = Tolerance.parse("5%");
            assertThat(t.kind()).isEqualTo(ToleranceKind.RELATIVE);
            assertThat(t.plus()).isCloseTo(0.05, byLessThan(5e-13));
        }

        @Test
        void readsTheTwoSidedForm() {
            Tolerance t = Tolerance.parse("+3/-1");
            assertThat(t.plus()).isEqualTo(3);
            assertThat(t.minus()).isEqualTo(1);
        }

        @Test
        void readsTheTwoSidedFormAsAPercentage() {
            Tolerance t = Tolerance.parse("+10/-5%");
            assertThat(t.kind()).isEqualTo(ToleranceKind.RELATIVE);
            assertThat(t.plus()).isCloseTo(0.1, byLessThan(5e-13));
            assertThat(t.minus()).isCloseTo(0.05, byLessThan(5e-13));
        }

        @Test
        void toleratesSurroundingSpace() {
            assertThat(Tolerance.parse("  0.5  ").plus()).isEqualTo(0.5);
        }

        @Test
        void refusesJunk() {
            assertThatThrownBy(() -> Tolerance.parse("about a bit"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read about a bit as a tolerance");
            assertThatThrownBy(() -> Tolerance.parse("+/-1"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read");
        }
    }

    @Nested
    class BoundsAndMembershipOnAnAbsoluteBand {
        private final Tolerance t = new Tolerance(1, 3);

        @Test
        void offsetsTheTargetByEachSide() {
            assertThat(t.boundsFor(10)).isEqualTo(new Bounds(9, 13));
        }

        @Test
        void isInclusiveAtBothEdges() {
            assertThat(t.contains(10, 9)).isTrue();
            assertThat(t.contains(10, 13)).isTrue();
            assertThat(t.contains(10, 8.999)).isFalse();
            assertThat(t.contains(10, 13.001)).isFalse();
        }

        @Test
        void reportsASignedExcursionAndZeroInside() {
            assertThat(t.excursion(10, 8)).isEqualTo(-1);
            assertThat(t.excursion(10, 15)).isEqualTo(2);
            assertThat(t.excursion(10, 11)).isEqualTo(0);
        }

        @Test
        void clampsIntoTheBand() {
            assertThat(t.clampInto(10, 5)).isEqualTo(9);
            assertThat(t.clampInto(10, 20)).isEqualTo(13);
            assertThat(t.clampInto(10, 11)).isEqualTo(11);
        }
    }

    @Nested
    class ARelativeBand {
        private final Tolerance t = Tolerance.percent(10);

        @Test
        void scalesWithTheTarget() {
            assertThat(t.boundsFor(100)).isEqualTo(new Bounds(90, 110));
            assertThat(t.boundsFor(400)).isEqualTo(new Bounds(360, 440));
        }

        @Test
        void usesTheMagnitudeOfANegativeTargetSoTheBandDoesNotInvert() {
            Bounds bounds = t.boundsFor(-100);
            assertThat(bounds.low()).isCloseTo(-110, byLessThan(5e-10));
            assertThat(bounds.high()).isCloseTo(-90, byLessThan(5e-10));
        }

        @Test
        void collapsesToTheTargetWhenTheTargetIsZero() {
            assertThat(t.boundsFor(0)).isEqualTo(new Bounds(0, 0));
        }
    }

    @Nested
    class Exact {
        @Test
        void admitsOnlyTheTargetItself() {
            Tolerance t = Tolerance.exact();
            assertThat(t.contains(400, 400)).isTrue();
            assertThat(t.contains(400, 400.0001)).isFalse();
        }
    }

    @Nested
    class Widened {
        @Test
        void scalesBothSidesAndKeepsTheKind() {
            Tolerance wide = new Tolerance(1, 3, ToleranceKind.RELATIVE).widened(2);
            assertThat(wide.toJson()).isEqualTo(Map.of("minus", 2.0, "plus", 6.0, "kind", "relative"));
        }

        @Test
        void collapsesToExactAtAFactorOfZero() {
            assertThat(new Tolerance(1, 3).widened(0).isExact()).isTrue();
        }

        @Test
        void refusesANegativeFactor() {
            assertThatThrownBy(() -> new Tolerance(1, 3).widened(-1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("factor must not be negative");
        }
    }

    @Nested
    class EqualityAndPrinting {
        @Test
        void comparesAllThreeFields() {
            assertThat(new Tolerance(1, 1).equals(Tolerance.symmetric(1))).isTrue();
            assertThat(new Tolerance(1, 1).equals(new Tolerance(1, 1, ToleranceKind.RELATIVE)))
                    .isFalse();
            assertThat(new Tolerance(1, 1).equals(1.0)).isFalse();
        }

        @Test
        void printsTheShortFormWhenSymmetric() {
            assertThat(String.valueOf(Tolerance.symmetric(0.5))).isEqualTo("+/-0.5");
            assertThat(String.valueOf(new Tolerance(1, 3))).isEqualTo("+3/-1");
            assertThat(String.valueOf(Tolerance.percent(5))).isEqualTo("+/-5%");
            assertThat(String.valueOf(new Tolerance(0.05, 0.1, ToleranceKind.RELATIVE)))
                    .isEqualTo("+10%/-5%");
        }
    }

    @Nested
    class ToTolerance {
        @Test
        void takesTheShapesAProfileMayHold() {
            Tolerance t = new Tolerance(1, 3);
            assertThat(toTolerance(t)).isSameAs(t);
            assertThat(toTolerance(2).equals(Tolerance.symmetric(2))).isTrue();
            assertThat(toTolerance("+3/-1").equals(t)).isTrue();
            assertThat(toTolerance(Map.of("minus", 1.0, "plus", 3.0)).equals(t)).isTrue();
            assertThat(toTolerance(Map.of("minus", 1.0, "plus", 3.0, "kind", "relative"))
                            .kind())
                    .isEqualTo(ToleranceKind.RELATIVE);
        }

        @Test
        void rejectsAnythingElse() {
            assertThatThrownBy(() -> toTolerance(null))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read a tolerance from that");
            assertThatThrownBy(() -> toTolerance(List.of(1.0, 3.0)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read a tolerance from that");
        }
    }
}
