package io.github.rkaushikdev.decklewright.raster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.raster.ops.PadMode;
import io.github.rkaushikdev.decklewright.util.Json;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The small value types of the raster layer: fills, tap lists, kernels made by hand and the named
 * modes.
 */
class ValuesTest {

    @Nested
    class Fills {
        @Test
        void repeatAUniformValueForEveryChannel() {
            assertThat(Fill.uniform(7).forChannels(3)).containsExactly(7, 7, 7);
            assertThat(Fill.uniform(7).requireChannels(1)).containsExactly(7);
        }

        @Test
        void keepPerChannelValuesAsGivenWhateverTheirNumber() {
            Fill fill = Fill.perChannel(1, 2, 3);
            assertThat(fill.forChannels(4)).containsExactly(1, 2, 3);
            assertThat(fill.requireChannels(3)).containsExactly(1, 2, 3);
            assertThat(fill).isEqualTo(Fill.perChannel(1, 2, 3)).isNotEqualTo(Fill.uniform(1));
        }

        @Test
        void refuseAPerChannelFillOfTheWrongLength() {
            assertThatThrownBy(() -> Fill.perChannel(1, 2).requireChannels(3))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessage("the fill colour has the wrong number of channels")
                    .extracting(error -> ((BadArgumentError) error).details())
                    .isEqualTo(Map.of("given", 2, "expected", 3));
        }

        @Test
        void neverShareTheirValuesWithTheCaller() {
            double[] values = {1, 2};
            Fill fill = Fill.perChannel(values);
            values[0] = 9;
            fill.forChannels(2)[1] = 9;
            assertThat(fill.forChannels(2)).containsExactly(1, 2);
        }
    }

    @Nested
    class TapLists {
        @Test
        void compareAndPrintByTheirTaps() {
            List<Contribution> a = Kernels.buildContributions(4, 2, Kernels.BOX);
            List<Contribution> b = Kernels.buildContributions(4, 2, Kernels.BOX);
            assertThat(a).isEqualTo(b);
            assertThat(a.get(0).hashCode()).isEqualTo(b.get(0).hashCode());
            assertThat(a.get(0)).isNotEqualTo(a.get(1)).isNotEqualTo("from 0");
            assertThat(a.get(0)).hasToString("from 0: 0 x 0.5, 1 x 0.5");
            assertThat(a.get(0).start()).isEqualTo(0);
            assertThat(a.get(0).size()).isEqualTo(2);
            assertThat(a.get(0).index(1)).isEqualTo(1);
            assertThat(a.get(0).weight(1)).isEqualTo(0.5);
        }

        @Test
        void handOutCopiesOfTheirArrays() {
            Contribution tap = Kernels.buildContributions(4, 2, Kernels.BOX).get(0);
            tap.indices()[0] = 9;
            tap.weights()[0] = 9;
            assertThat(tap.indices()).containsExactly(0, 1);
            assertThat(tap.weights()).containsExactly(0.5, 0.5);
            assertThatThrownBy(
                            () -> Kernels.buildContributions(4, 2, Kernels.BOX).add(tap))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class HandMadeKernels {
        @Test
        void areTakenAsReadilyAsNamedOnes() {
            Kernel nearest = Kernel.of("nearest", 0.5, t -> t > -0.5 && t <= 0.5 ? 1 : 0);
            assertThat(Kernels.kernelFor(nearest)).isSameAs(nearest);
            assertThat(nearest).hasToString("nearest");
            assertThat(nearest.support()).isEqualTo(0.5);
            assertThat(Kernels.buildContributions(2, 4, nearest).get(1).indices())
                    .containsExactly(0);
            assertThat(Kernels.CATMULL_ROM).hasToString("catmullRom");
        }

        @Test
        void areRefusedByNameWhenTheNameIsEmpty() {
            assertThatThrownBy(() -> Kernels.kernelFor(""))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("kernel must be a non empty string");
        }
    }

    @Nested
    class NamedModes {
        static Stream<Arguments> everyMode() {
            return Stream.of(
                    Arguments.of(List.of(Layout.values()), (Function<String, Object>) Layout::parse),
                    Arguments.of(List.of(EdgeMode.values()), (Function<String, Object>) EdgeMode::parse),
                    Arguments.of(List.of(SampleMode.values()), (Function<String, Object>) SampleMode::parse),
                    Arguments.of(List.of(Along.values()), (Function<String, Object>) Along::parse),
                    Arguments.of(List.of(PadMode.values()), (Function<String, Object>) PadMode::parse));
        }

        @ParameterizedTest
        @MethodSource("everyMode")
        void readAndWriteTheirLabels(List<? extends JsonValue> modes, Function<String, Object> parse) {
            for (JsonValue mode : modes) {
                String label = String.valueOf(mode);
                assertThat(parse.apply(label)).isSameAs(mode);
                assertThat(mode.toJson()).isEqualTo(label);
                assertThat(Json.stringify(mode)).isEqualTo("\"" + label + "\"");
            }
            assertThatThrownBy(() -> parse.apply("nonsense")).isInstanceOf(BadArgumentError.class);
        }

        @Test
        void listEveryModeInTheOrderTheLabListsThem() {
            assertThat(Sampler.EDGE_MODES).containsExactly(EdgeMode.CLAMP, EdgeMode.MIRROR, EdgeMode.CONSTANT);
            assertThat(Sampler.SAMPLE_MODES).containsExactly(SampleMode.NEAREST, SampleMode.BILINEAR);
            assertThat(EdgeMode.MIRROR.label()).isEqualTo("mirror");
            assertThat(Along.COLUMN.label()).isEqualTo("column");
            assertThat(SampleMode.NEAREST.label()).isEqualTo("nearest");
        }
    }
}
