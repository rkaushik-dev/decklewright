package io.github.rkaushikdev.decklewright.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InsetsTest {

    @Nested
    class BuildingInsets {
        @Test
        void keepsFourIndependentEdges() {
            Insets i = new Insets(1, 2, 3, 4);
            assertThat(i.toJson()).isEqualTo(Map.of("top", 1.0, "right", 2.0, "bottom", 3.0, "left", 4.0));
            // A record's components are final and these are primitives, so nothing can change them.
            assertThat(Insets.class.isRecord()).isTrue();
        }

        @Test
        void hasUniformSymmetricAndZeroShorthands() {
            assertThat(Insets.uniform(5).isUniform()).isTrue();
            assertThat(Insets.symmetric(1, 2).toJson())
                    .isEqualTo(Map.of("top", 1.0, "right", 2.0, "bottom", 1.0, "left", 2.0));
            assertThat(Insets.zero().isZero()).isTrue();
        }

        @Test
        void allowsNegativeEdgesSinceAnOutsetIsANegativeInset() {
            assertThat(new Insets(-1, -1, -1, -1).largest()).isEqualTo(-1);
        }
    }

    @Nested
    class InsetsFrom {
        @Test
        void readsANumber() {
            assertThat(Insets.from(3).approxEquals(Insets.uniform(3))).isTrue();
        }

        @Test
        void readsTheCssStyleLists() {
            assertThat(Insets.from(List.of(3)).approxEquals(Insets.uniform(3))).isTrue();
            assertThat(Insets.from(List.of(1, 2)).approxEquals(Insets.symmetric(1, 2)))
                    .isTrue();
            assertThat(Insets.from(List.of(1, 2, 3, 4)).toJson())
                    .isEqualTo(Map.of("top", 1.0, "right", 2.0, "bottom", 3.0, "left", 4.0));
        }

        @Test
        void refusesAListOfThree() {
            assertThatThrownBy(() -> Insets.from(List.of(1, 2, 3)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("one, two or four numbers");
        }

        @Test
        void readsAPartialBagAndDefaultsTheRestToZero() {
            assertThat(Insets.from(Map.of("bottom", 10)).toJson())
                    .isEqualTo(Map.of("top", 0.0, "right", 0.0, "bottom", 10.0, "left", 0.0));
        }

        @Test
        void passesAnInstanceThroughAndRejectsJunk() {
            Insets i = Insets.uniform(2);
            assertThat(Insets.from(i)).isSameAs(i);
            assertThatThrownBy(() -> Insets.from("2px"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read insets from that");
        }
    }

    @Nested
    class TheDerivedSums {
        private final Insets i = new Insets(1, 2, 3, 4);

        @Test
        void addsOppositeEdges() {
            assertThat(i.horizontal()).isEqualTo(6);
            assertThat(i.vertical()).isEqualTo(4);
            assertThat(i.largest()).isEqualTo(4);
        }
    }

    @Nested
    class ApplyTo {
        private final Rect rect = Rect.of(0, 0, 100, 80);

        @Test
        void pullsEachEdgeInByItsOwnAmount() {
            Rect inner = new Insets(5, 10, 15, 20).applyTo(rect);
            assertThat(inner.toJson()).isEqualTo(Map.of("x", 20.0, "y", 5.0, "width", 70.0, "height", 60.0));
        }

        @Test
        void isANoOpForZero() {
            assertThat(Insets.zero().applyTo(rect).approxEquals(rect)).isTrue();
        }

        @Test
        void collapsesAtTheCentreWhenTheDemandExceedsTheRect() {
            Rect collapsed = Insets.uniform(60).applyTo(rect);
            assertThat(collapsed.isEmpty()).isTrue();
            assertThat(collapsed.origin().toJson()).isEqualTo(Map.of("x", 50.0, "y", 40.0));
        }

        @Test
        void collapsesWhenExactlyConsumedRatherThanLeavingAZeroWidthRectInPlace() {
            Rect collapsed = Insets.symmetric(40, 50).applyTo(rect);
            assertThat(collapsed.isEmpty()).isTrue();
        }
    }

    @Nested
    class ExpandFrom {
        @Test
        void pushesTheEdgesOutward() {
            Rect outer = new Insets(5, 10, 15, 20).expandFrom(Rect.of(20, 5, 70, 60));
            assertThat(outer.toJson()).isEqualTo(Map.of("x", 0.0, "y", 0.0, "width", 100.0, "height", 80.0));
        }
    }

    @Nested
    class Combining {
        private final Insets a = new Insets(1, 2, 3, 4);
        private final Insets b = new Insets(4, 3, 2, 1);

        @Test
        void addsEdgeByEdge() {
            assertThat(a.plus(b).toJson()).isEqualTo(Map.of("top", 5.0, "right", 5.0, "bottom", 5.0, "left", 5.0));
        }

        @Test
        void scalesEveryEdge() {
            assertThat(a.times(2).toJson()).isEqualTo(Map.of("top", 2.0, "right", 4.0, "bottom", 6.0, "left", 8.0));
            assertThatThrownBy(() -> a.times(Double.NaN))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("factor must be a finite number");
        }

        @Test
        void takesTheLargerDemandOnEachEdgeIndependently() {
            assertThat(a.maxWith(b).toJson()).isEqualTo(Map.of("top", 4.0, "right", 3.0, "bottom", 3.0, "left", 4.0));
        }

        @Test
        void negatesEveryEdge() {
            assertThat(a.negated().toJson())
                    .isEqualTo(Map.of("top", -1.0, "right", -2.0, "bottom", -3.0, "left", -4.0));
        }

        @Test
        void roundsEveryEdge() {
            assertThat(new Insets(1.4, 1.6, 1.5, 2.5).rounded(Math::floor).toJson())
                    .isEqualTo(Map.of("top", 1.0, "right", 1.0, "bottom", 1.0, "left", 2.0));
        }
    }

    @Nested
    class EqualityAndPrinting {
        @Test
        void comparesWithinATolerance() {
            assertThat(Insets.uniform(1).approxEquals(Insets.uniform(1))).isTrue();
            assertThat(Insets.uniform(1).approxEquals(Insets.uniform(1.4))).isFalse();
            assertThat(Insets.uniform(1).approxEquals(Insets.uniform(1.4), 0.5)).isTrue();
            // approxEquals only takes insets, so a bag is compared through equals instead.
            assertThat(Insets.uniform(1).equals(Map.of("top", 1.0, "right", 1.0, "bottom", 1.0, "left", 1.0)))
                    .isFalse();
        }

        @Test
        void printsTheShortFormWhenEveryEdgeMatches() {
            assertThat(String.valueOf(Insets.uniform(3))).isEqualTo("3 all round");
            assertThat(String.valueOf(new Insets(1, 2, 3, 4))).isEqualTo("1 2 3 4");
        }
    }
}
