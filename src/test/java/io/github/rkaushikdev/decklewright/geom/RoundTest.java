package io.github.rkaushikdev.decklewright.geom;

import static io.github.rkaushikdev.decklewright.geom.Rounding.evenRect;
import static io.github.rkaushikdev.decklewright.geom.Rounding.isWholePixel;
import static io.github.rkaushikdev.decklewright.geom.Rounding.roundRect;
import static io.github.rkaushikdev.decklewright.geom.Rounding.snapRect;
import static io.github.rkaushikdev.decklewright.geom.Rounding.snapSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RoundTest {
    private final Rect loose = Rect.of(10.4, 20.6, 30.3, 40.7);

    @Nested
    class RoundRect {
        @Test
        void roundsEveryEdgeToTheNearestByDefault() {
            assertThat(roundRect(loose).toJson())
                    .isEqualTo(Map.of("x", 10.0, "y", 21.0, "width", 31.0, "height", 40.0));
        }

        @Test
        void growsOutward() {
            assertThat(roundRect(loose, RoundPolicy.OUTWARD).toJson())
                    .isEqualTo(Map.of("x", 10.0, "y", 20.0, "width", 31.0, "height", 42.0));
        }

        @Test
        void pullsInward() {
            assertThat(roundRect(loose, RoundPolicy.INWARD).toJson())
                    .isEqualTo(Map.of("x", 11.0, "y", 21.0, "width", 29.0, "height", 40.0));
        }

        @Test
        void floorsBothEdges() {
            assertThat(roundRect(loose, RoundPolicy.FLOOR).toJson())
                    .isEqualTo(Map.of("x", 10.0, "y", 20.0, "width", 30.0, "height", 41.0));
        }

        @Test
        void usesBankerRoundingOnAnExactHalf() {
            assertThat(roundRect(Rect.of(0.5, 1.5, 2, 2)).toJson())
                    .isEqualTo(Map.of("x", 0.0, "y", 2.0, "width", 2.0, "height", 2.0));
        }

        @Test
        void neverInvertsARectItRoundedInwardPastItself() {
            Rect thin = roundRect(Rect.of(10.2, 5, 0.3, 10), RoundPolicy.INWARD);
            assertThat(thin.width()).isEqualTo(0);
        }

        @Test
        void refusesAPolicyItDoesNotKnow() {
            // A policy arrives as a string from a profile, so the unknown one is refused as it is read.
            assertThatThrownBy(() -> roundRect(loose, RoundPolicy.parse("sideways")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("policy must be one of");
        }
    }

    @Nested
    class SnapSize {
        @Test
        void snapsToTheNearestMultipleByDefault() {
            assertThat(snapSize(Size.of(101, 99), 4).toJson()).isEqualTo(Map.of("width", 100.0, "height", 100.0));
        }

        @Test
        void snapsUpAndDownOnRequest() {
            assertThat(snapSize(Size.of(101, 99), 4, SnapDirection.UP).toJson())
                    .isEqualTo(Map.of("width", 104.0, "height", 100.0));
            assertThat(snapSize(Size.of(101, 99), 4, SnapDirection.DOWN).toJson())
                    .isEqualTo(Map.of("width", 100.0, "height", 96.0));
        }

        @Test
        void leavesASizeThatIsAlreadyOnTheGrid() {
            assertThat(snapSize(Size.of(96, 128), 32).toJson()).isEqualTo(Map.of("width", 96.0, "height", 128.0));
        }

        @Test
        void refusesAMultipleThatIsNotAWholeNumberAboveZero() {
            assertThatThrownBy(() -> snapSize(Size.of(10, 10), 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("above zero");
            assertThatThrownBy(() -> snapSize(Size.of(10, 10), 1.5))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("whole number");
            // A direction arrives as a string from a profile, so the unknown one is refused as it is read.
            assertThatThrownBy(() -> snapSize(Size.of(10, 10), 4, SnapDirection.parse("sideways")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("direction must be one of");
        }
    }

    @Nested
    class SnapRect {
        @Test
        void snapsTheExtentsAndKeepsTheCentreNearWhereItWas() {
            Rect snapped = snapRect(Rect.of(10, 10, 101, 99), 4);
            assertThat(snapped.size().toJson()).isEqualTo(Map.of("width", 100.0, "height", 100.0));
            assertThat(Math.abs(snapped.centre().x() - 60.5)).isLessThanOrEqualTo(1);
            assertThat(isWholePixel(snapped)).isTrue();
        }

        @Test
        void growsToTheNextMultipleWhenToldTo() {
            Rect snapped = snapRect(Rect.of(0, 0, 101, 99), 8, SnapDirection.UP);
            assertThat(snapped.size().toJson()).isEqualTo(Map.of("width", 104.0, "height", 104.0));
        }
    }

    @Nested
    class EvenRect {
        @Test
        void alwaysLandsOnEvenExtents() {
            for (double[] size : new double[][] {{99, 51}, {100, 50}, {1, 1}}) {
                Rect out = evenRect(Rect.of(0, 0, size[0], size[1]));
                assertThat(out.width() % 2).isEqualTo(0);
                assertThat(out.height() % 2).isEqualTo(0);
                assertThat(out.width()).isGreaterThanOrEqualTo(size[0]);
            }
        }
    }

    @Nested
    class IsWholePixel {
        @Test
        void separatesAPlannedCropFromARenderedOne() {
            assertThat(isWholePixel(Rect.of(1, 2, 3, 4))).isTrue();
            assertThat(isWholePixel(Rect.of(1.5, 2, 3, 4))).isFalse();
            assertThat(isWholePixel(Rect.of(1, 2, 3.25, 4))).isFalse();
        }
    }
}
