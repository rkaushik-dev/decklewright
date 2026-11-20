package io.github.rkaushikdev.decklewright.raster;

import static io.github.rkaushikdev.decklewright.raster.Kernels.BOX;
import static io.github.rkaushikdev.decklewright.raster.Kernels.CATMULL_ROM;
import static io.github.rkaushikdev.decklewright.raster.Kernels.KERNEL_NAMES;
import static io.github.rkaushikdev.decklewright.raster.Kernels.MITCHELL;
import static io.github.rkaushikdev.decklewright.raster.Kernels.TRIANGLE;
import static io.github.rkaushikdev.decklewright.raster.Kernels.buildContributions;
import static io.github.rkaushikdev.decklewright.raster.Kernels.haloFor;
import static io.github.rkaushikdev.decklewright.raster.Kernels.kernelFor;
import static io.github.rkaushikdev.decklewright.raster.Kernels.lanczos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KernelsTest {

    @Nested
    class TheKernelsThemselves {
        @Test
        void allPeakAtTheCentre() {
            for (String name : KERNEL_NAMES) {
                Kernel k = kernelFor(name);
                assertThat(k.weight(0)).isGreaterThan(0);
                assertThat(k.weight(k.support() + 0.001)).isEqualTo(0);
            }
        }

        @Test
        void boxCoversHalfASampleEitherSide() {
            assertThat(BOX.weight(0)).isEqualTo(1);
            assertThat(BOX.weight(0.5)).isEqualTo(1);
            assertThat(BOX.weight(0.51)).isEqualTo(0);
        }

        @Test
        void triangleFallsOffLinearly() {
            assertThat(TRIANGLE.weight(0)).isEqualTo(1);
            assertThat(TRIANGLE.weight(0.25)).isEqualTo(0.75);
            assertThat(TRIANGLE.weight(1)).isEqualTo(0);
            assertThat(TRIANGLE.weight(-0.5)).isEqualTo(0.5);
        }

        @Test
        void catmullRomPassesThroughTheSamplesAndUndershootsBetweenThem() {
            assertThat(CATMULL_ROM.weight(0)).isCloseTo(1, byLessThan(0.5e-12));
            assertThat(CATMULL_ROM.weight(1)).isCloseTo(0, byLessThan(0.5e-12));
            assertThat(CATMULL_ROM.weight(2)).isCloseTo(0, byLessThan(0.5e-12));
            assertThat(CATMULL_ROM.weight(1.5)).isLessThan(0);
        }

        @Test
        void mitchellNeverGoesNegativeNearTheCentre() {
            assertThat(MITCHELL.weight(0)).isCloseTo(8.0 / 9, byLessThan(0.5e-9));
            assertThat(MITCHELL.weight(0.5)).isGreaterThan(0);
        }

        @Test
        void lanczosIsOneAtTheCentreAndZeroAtTheWholeOffsets() {
            Kernel k = lanczos(3);
            assertThat(k.weight(0)).isEqualTo(1);
            assertThat(k.weight(1)).isCloseTo(0, byLessThan(0.5e-9));
            assertThat(k.weight(2)).isCloseTo(0, byLessThan(0.5e-9));
            assertThat(k.weight(3)).isEqualTo(0);
            assertThat(k.name()).isEqualTo("lanczos3");
        }

        @Test
        void refusesALobeCountThatIsNotAWholeNumberAboveZero() {
            assertThatThrownBy(() -> lanczos(0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("above zero");
        }
    }

    @Nested
    class KernelFor {
        @Test
        void findsTheNamedKernels() {
            assertThat(kernelFor("box")).isSameAs(BOX);
            assertThat(kernelFor("lanczos3").name()).isEqualTo("lanczos3");
        }

        @Test
        void passesAnObjectThrough() {
            assertThat(kernelFor(TRIANGLE)).isSameAs(TRIANGLE);
        }

        @Test
        void listsWhatItKnowsWhenItCannotFindOne() {
            BadArgumentError err = catchThrowableOfType(BadArgumentError.class, () -> kernelFor("sinc"));
            assertThat(err).isNotNull();
            assertThat(err.getMessage()).isEqualTo("unknown kernel sinc");
            assertThat(err.details().get("known")).asInstanceOf(LIST).contains("catmullRom");
        }
    }

    @Nested
    class BuildContributions {
        @Test
        void producesOneEntryPerOutputSample() {
            List<Contribution> taps = buildContributions(8, 4, BOX);
            assertThat(taps).hasSize(4);
        }

        @Test
        void normalisesEveryTapListToOne() {
            for (String name : KERNEL_NAMES) {
                List<Contribution> taps = buildContributions(9, 5, kernelFor(name));
                for (Contribution tap : taps) {
                    double total = Arrays.stream(tap.weights()).reduce(0, Double::sum);
                    assertThat(total).isCloseTo(1, byLessThan(0.5e-9));
                }
            }
        }

        @Test
        void mergesPairsOfSamplesWhenHalvingWithABox() {
            List<Contribution> taps = buildContributions(4, 2, BOX);
            assertThat(taps.get(0).indices()).containsExactly(0, 1);
            assertThat(taps.get(1).indices()).containsExactly(2, 3);
            assertThat(taps.get(0).weights()).containsExactly(0.5, 0.5);
        }

        @Test
        void takesASingleSampleWhenTheSizesMatch() {
            List<Contribution> taps = buildContributions(4, 4, BOX);
            assertThat(taps.stream().map(Contribution::indices))
                    .containsExactly(new int[] {0}, new int[] {1}, new int[] {2}, new int[] {3});
        }

        @Test
        void reachesToTwoNeighboursWhenDoublingWithATriangle() {
            List<Contribution> taps = buildContributions(2, 4, TRIANGLE);
            assertThat(taps.get(0).indices()).containsExactly(0, 0);
            assertThat(taps.get(1).indices()).containsExactly(0, 1);
            assertThat(taps.get(1).weight(0)).isCloseTo(0.75, byLessThan(0.5e-9));
            assertThat(taps.get(3).indices()).containsExactly(1, 1);
        }

        @Test
        void keepsEveryIndexInsideTheSourceUnderTheClampPolicy() {
            List<Contribution> taps = buildContributions(3, 9, CATMULL_ROM);
            for (Contribution tap : taps) {
                for (int index : tap.indices()) {
                    assertThat(index).isGreaterThanOrEqualTo(0);
                    assertThat(index).isLessThan(3);
                }
            }
        }

        @Test
        void fallsBackToOneSampleWhenTheConstantPolicyDropsThemAll() {
            List<Contribution> taps = buildContributions(1, 3, BOX, EdgeMode.CONSTANT);
            for (Contribution tap : taps) {
                assertThat(tap.indices()).hasSize(1);
                assertThat(tap.weights()).containsExactly(1.0);
            }
        }

        @Test
        void refusesAnExtentThatIsNotAWholeNumberAboveZero() {
            assertThatThrownBy(() -> buildContributions(0, 4, BOX))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("sourceExtent");
            // A fractional extent cannot be written against an int parameter; a negative one is the
            // nearest bad extent Java can pass, and it is refused by the same rule.
            assertThatThrownBy(() -> buildContributions(4, -1, BOX))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("targetExtent");
        }
    }

    @Nested
    class HaloFor {
        @Test
        void isThePlainSupportWhenEnlarging() {
            assertThat(haloFor(CATMULL_ROM, 2)).isEqualTo(2);
            assertThat(haloFor(BOX, 4)).isEqualTo(1);
        }

        @Test
        void growsWithTheReductionFactor() {
            assertThat(haloFor(CATMULL_ROM, 0.5)).isEqualTo(4);
            assertThat(haloFor(CATMULL_ROM, 0.25)).isEqualTo(8);
        }

        @Test
        void refusesAScaleOfZero() {
            assertThatThrownBy(() -> haloFor(BOX, 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("scale must be greater than zero");
        }
    }
}
