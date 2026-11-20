package io.github.rkaushikdev.decklewright.geom;

import static io.github.rkaushikdev.decklewright.geom.Size.requireNotEmpty;
import static io.github.rkaushikdev.decklewright.geom.Size.sizeFromAspect;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.GeometryError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SizeTest {

    @Nested
    class BuildingASize {
        @Test
        void allowsZeroButNotNegative() {
            assertThat(Size.of(0, 0).isEmpty()).isTrue();
            assertThatThrownBy(() -> Size.of(-1, 10))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("width must not be negative");
            assertThatThrownBy(() -> Size.of(10, -1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("height must not be negative");
        }

        @Test
        void hasTheShorthands() {
            assertThat(Size.square(5).toJson()).isEqualTo(Map.of("width", 5.0, "height", 5.0));
            assertThat(Size.empty().area()).isEqualTo(0);
        }

        @Test
        void readsTheShapesACallerMayHandIt() {
            Size s = Size.of(4, 3);
            assertThat(Size.from(s)).isSameAs(s);
            assertThat(Size.from(List.of(4, 3)).approxEquals(s)).isTrue();
            assertThat(Size.from(Map.of("width", 4, "height", 3)).approxEquals(s))
                    .isTrue();
            assertThatThrownBy(() -> Size.from("4x3"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read a size from that");
        }
    }

    @Nested
    class OrientationAndAspect {
        @Test
        void separatesLandscapePortraitAndSquare() {
            assertThat(Size.of(4, 3).isLandscape()).isTrue();
            assertThat(Size.of(3, 4).isPortrait()).isTrue();
            assertThat(Size.square(4).isLandscape()).isFalse();
            assertThat(Size.square(4).isPortrait()).isFalse();
        }

        @Test
        void reportsWidthOverHeight() {
            assertThat(Size.of(4, 2).aspect()).isEqualTo(2);
        }

        @Test
        void refusesAnAspectWhenThereIsNoHeight() {
            assertThatThrownBy(() -> Size.of(4, 0).aspect())
                    .isInstanceOf(GeometryError.class)
                    .hasMessageFindingMatch("no height has no aspect");
        }

        @Test
        void swapsTheAxes() {
            assertThat(Size.of(4, 3).swapped().toJson()).isEqualTo(Map.of("width", 3.0, "height", 4.0));
        }
    }

    @Nested
    class ScalingAndGrowing {
        @Test
        void scalesUniformlyOrPerAxis() {
            assertThat(Size.of(4, 3).scaled(2).toJson()).isEqualTo(Map.of("width", 8.0, "height", 6.0));
            assertThat(Size.of(4, 3).scaled(2, 1).toJson()).isEqualTo(Map.of("width", 8.0, "height", 3.0));
        }

        @Test
        void refusesANegativeFactor() {
            assertThatThrownBy(() -> Size.of(4, 3).scaled(-1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("factor must not be negative");
        }

        @Test
        void clampsAShrinkAtZeroRatherThanGoingNegative() {
            assertThat(Size.of(4, 3).grown(-10).toJson()).isEqualTo(Map.of("width", 0.0, "height", 0.0));
            assertThat(Size.of(4, 3).grown(2, 0).toJson()).isEqualTo(Map.of("width", 6.0, "height", 3.0));
        }

        @Test
        void roundsWithoutDippingBelowZero() {
            assertThat(Size.of(4.6, 3.4).rounded().toJson()).isEqualTo(Map.of("width", 5.0, "height", 3.0));
            assertThat(Size.of(0.2, 0.2).rounded(Math::floor).toJson()).isEqualTo(Map.of("width", 0.0, "height", 0.0));
        }
    }

    @Nested
    class ContainedInAndCovering {
        private final Size plate = Size.of(400, 300);

        @Test
        void fitsInsideTheBoundWithoutDistortion() {
            Size fitted = plate.containedIn(Size.of(200, 200));
            assertThat(fitted.toJson()).isEqualTo(Map.of("width", 200.0, "height", 150.0));
            assertThat(fitted.fitsInside(Size.of(200, 200))).isTrue();
        }

        @Test
        void coversTheBoundWithoutDistortion() {
            Size covered = plate.covering(Size.of(200, 200));
            assertThat(covered.toJson()).isEqualTo(Map.of("width", 266.66666666666663, "height", 200.0));
        }

        @Test
        void givesUpOnAnEmptySide() {
            assertThat(plate.containedIn(Size.empty()).isEmpty()).isTrue();
            assertThat(Size.empty().covering(plate).isEmpty()).isTrue();
        }
    }

    @Nested
    class RatioLabel {
        @Test
        void reducesWholeNumbers() {
            assertThat(Size.of(1200, 800).ratioLabel()).isEqualTo("3:2");
            assertThat(Size.of(1920, 1080).ratioLabel()).isEqualTo("16:9");
        }

        @Test
        void fallsBackToThePlainRatioWhenTheSizeIsFractional() {
            assertThat(Size.of(4.5, 3).ratioLabel()).isEqualTo("4.5:3");
        }

        @Test
        void doesNotDivideByZeroOnAnEmptySize() {
            assertThat(Size.of(0, 0).ratioLabel()).isEqualTo("0:0");
        }
    }

    @Nested
    class EqualsAndPrinting {
        @Test
        void comparesWithinATolerance() {
            assertThat(Size.of(4, 3).approxEquals(Size.of(4, 3))).isTrue();
            assertThat(Size.of(4, 3).approxEquals(Size.of(4.4, 3))).isFalse();
            assertThat(Size.of(4, 3).approxEquals(Size.of(4.4, 3), 0.5)).isTrue();
            // approxEquals only takes a size, so a bag is compared through equals instead.
            assertThat(Size.of(4, 3).equals(Map.of("width", 4.0, "height", 3.0)))
                    .isFalse();
        }

        @Test
        void printsWithAnXBetween() {
            assertThat(String.valueOf(Size.of(1.23456, 3))).isEqualTo("1.235x3");
        }
    }

    @Nested
    class TheHelpers {
        @Test
        void rejectsAnEmptySizeByName() {
            assertThatThrownBy(() -> requireNotEmpty(Size.of(4, 0), "crop"))
                    .isInstanceOf(GeometryError.class)
                    .hasMessageFindingMatch("crop must have both extents");
            Size good = Size.of(4, 3);
            assertThat(requireNotEmpty(good, "crop")).isSameAs(good);
        }

        @Test
        void buildsASizeFromAnAspectAndAWidth() {
            assertThat(sizeFromAspect(2, 400).toJson()).isEqualTo(Map.of("width", 400.0, "height", 200.0));
            assertThatThrownBy(() -> sizeFromAspect(0, 400))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("aspect must be greater than zero");
            assertThatThrownBy(() -> sizeFromAspect(2, 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("width must be greater than zero");
        }
    }
}
