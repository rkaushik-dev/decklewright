package io.github.rkaushikdev.decklewright.raster;

import static io.github.rkaushikdev.decklewright.raster.Sampler.resolveIndex;
import static io.github.rkaushikdev.decklewright.raster.Sampler.samplerFor;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.gray;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SamplerTest {

    private static final Surface QUAD = gray(row(0, 100), row(200, 255));

    @Nested
    class ResolveIndex {
        @Test
        void passesAnInteriorIndexThrough() {
            assertThat(resolveIndex(2, 5, EdgeMode.CLAMP)).isEqualTo(2);
            assertThat(resolveIndex(0, 5, EdgeMode.CONSTANT)).isEqualTo(0);
        }

        @Test
        void clampsToTheNearestColumn() {
            assertThat(resolveIndex(-3, 5, EdgeMode.CLAMP)).isEqualTo(0);
            assertThat(resolveIndex(9, 5, EdgeMode.CLAMP)).isEqualTo(4);
        }

        @Test
        void mirrorsWithoutRepeatingTheEdge() {
            assertThat(resolveIndex(-1, 4, EdgeMode.MIRROR)).isEqualTo(1);
            assertThat(resolveIndex(4, 4, EdgeMode.MIRROR)).isEqualTo(2);
            assertThat(resolveIndex(-4, 4, EdgeMode.MIRROR)).isEqualTo(2);
        }

        @Test
        void mirrorsASingleColumnOntoItself() {
            assertThat(resolveIndex(7, 1, EdgeMode.MIRROR)).isEqualTo(0);
        }

        @Test
        void reportsNoColumnUnderTheConstantMode() {
            assertThat(resolveIndex(-1, 5, EdgeMode.CONSTANT)).isEqualTo(-1);
            assertThat(resolveIndex(5, 5, EdgeMode.CONSTANT)).isEqualTo(-1);
        }
    }

    @Nested
    class BuildingASampler {
        @Test
        void defaultsToClampingAndABlackBackground() {
            Sampler s = new Sampler(QUAD);
            assertThat(s.edge()).isEqualTo(EdgeMode.CLAMP);
            assertThat(s.background()).containsExactly(0.0);
            // Frozen: every field is final and the background is handed out as a copy.
            assertThat(Arrays.stream(Sampler.class.getDeclaredFields())
                            .filter(field -> !Modifier.isStatic(field.getModifiers()))
                            .map(Field::getModifiers))
                    .allMatch(Modifier::isFinal);
            s.background()[0] = 99;
            assertThat(s.background()).containsExactly(0.0);
        }

        @Test
        void takesAPerChannelBackground() {
            Surface colour = Surface.create(1, 1, Layout.RGB);
            assertThat(new Sampler(colour, EdgeMode.CLAMP, Fill.perChannel(1, 2, 3)).background())
                    .containsExactly(1.0, 2.0, 3.0);
            assertThat(new Sampler(colour, EdgeMode.CLAMP, Fill.uniform(8)).background())
                    .containsExactly(8.0, 8.0, 8.0);
        }

        @Test
        void refusesAnEdgeModeItDoesNotKnow() {
            // An edge mode is an enum, so an unknown one can only arrive by name.
            assertThatThrownBy(() -> new Sampler(QUAD, EdgeMode.parse("wrap"), Fill.uniform(0)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("edge must be one of");
        }
    }

    @Nested
    class At {
        @Test
        void readsInsideTheSurface() {
            assertThat(samplerFor(QUAD).at(1, 0, 0)).isEqualTo(100);
        }

        @Test
        void clampsOutsideByDefault() {
            assertThat(samplerFor(QUAD).at(-5, 0, 0)).isEqualTo(0);
            assertThat(samplerFor(QUAD).at(9, 1, 0)).isEqualTo(255);
        }

        @Test
        void answersTheBackgroundUnderTheConstantMode() {
            Sampler s = samplerFor(QUAD, EdgeMode.CONSTANT, Fill.uniform(42));
            assertThat(s.at(-1, 0, 0)).isEqualTo(42);
            assertThat(s.at(0, 5, 0)).isEqualTo(42);
        }
    }

    @Nested
    class Nearest {
        @Test
        void landsOnThePixelCentre() {
            Sampler s = samplerFor(QUAD);
            assertThat(s.nearest(0, 0, 0)).isEqualTo(0);
            assertThat(s.nearest(0.4, 0, 0)).isEqualTo(0);
            assertThat(s.nearest(0.6, 0, 0)).isEqualTo(100);
        }
    }

    @Nested
    class Bilinear {
        private final Sampler s = samplerFor(QUAD);

        @Test
        void returnsTheSampleExactlyAtAPixelCentre() {
            assertThat(s.bilinear(0, 0, 0)).isEqualTo(0);
            assertThat(s.bilinear(1, 1, 0)).isEqualTo(255);
        }

        @Test
        void interpolatesAlongOneAxis() {
            assertThat(s.bilinear(0.5, 0, 0)).isEqualTo(50);
            assertThat(s.bilinear(0, 0.5, 0)).isEqualTo(100);
        }

        @Test
        void interpolatesAcrossBoth() {
            assertThat(s.bilinear(0.5, 0.5, 0)).isCloseTo((0 + 100 + 200 + 255) / 4.0, byLessThan(0.5e-9));
        }

        @Test
        void usesTheEdgePolicyWhenItReachesPastTheSurface() {
            assertThat(s.bilinear(1.5, 0, 0)).isEqualTo(100);
            Sampler constant = samplerFor(QUAD, EdgeMode.CONSTANT, Fill.uniform(0));
            assertThat(constant.bilinear(1.5, 0, 0)).isEqualTo(50);
        }

        @Test
        void doesNotInterpolateWhenBothFractionsAreZero() {
            Sampler constant = samplerFor(QUAD, EdgeMode.CONSTANT, Fill.uniform(7));
            assertThat(constant.bilinear(1, 1, 0)).isEqualTo(255);
        }
    }

    @Nested
    class SampleAndPixel {
        @Test
        void dispatchesOnTheNamedMode() {
            Sampler s = samplerFor(QUAD);
            assertThat(s.sample(0.5, 0, 0, SampleMode.NEAREST)).isEqualTo(100);
            assertThat(s.sample(0.5, 0, 0, SampleMode.BILINEAR)).isEqualTo(50);
            assertThat(s.sample(0.5, 0, 0)).isEqualTo(50);
        }

        @Test
        void refusesAModeItDoesNotKnow() {
            // A sample mode is an enum, so an unknown one can only arrive by name.
            assertThatThrownBy(() -> samplerFor(QUAD).sample(0, 0, 0, SampleMode.parse("cubic")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("mode must be one of");
        }

        @Test
        void readsEveryChannelOfAPixel() {
            Surface colour = Surface.create(2, 1, Layout.RGB);
            colour.setPixel(0, 0, 0, 10, 20);
            colour.setPixel(1, 0, 100, 110, 120);
            assertThat(samplerFor(colour).pixel(0.5, 0)).containsExactly(50, 60, 70);
        }
    }

    @Nested
    class Quantised {
        @Test
        void roundsAndClampsToTheDepth() {
            Sampler s = samplerFor(QUAD);
            assertThat(s.quantised(0.5, 0.5, 0)).isEqualTo(139);
        }

        @Test
        void neverLeavesTheRangeEvenWithAWildBackground() {
            Sampler s = samplerFor(QUAD, EdgeMode.CONSTANT, Fill.uniform(1e6));
            assertThat(s.quantised(-5, -5, 0)).isEqualTo(255);
        }
    }
}
