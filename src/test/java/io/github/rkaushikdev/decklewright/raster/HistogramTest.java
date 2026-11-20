package io.github.rkaushikdev.decklewright.raster;

import static io.github.rkaushikdev.decklewright.raster.Histogram.histogramOf;
import static io.github.rkaushikdev.decklewright.raster.Histogram.histogramsOf;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.gray;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HistogramTest {

    static Surface strip(int... values) {
        return gray(values);
    }

    static Surface strip16(int... values) {
        return gray(16, values);
    }

    @Nested
    class HistogramOf {
        @Test
        void countsOneBinPerSampleValueByDefault() {
            Histogram h = histogramOf(strip(0, 0, 5, 255));
            assertThat(h.bins()).isEqualTo(256);
            assertThat(h.total()).isEqualTo(4);
            assertThat(h.count(0)).isEqualTo(2);
            assertThat(h.count(5)).isEqualTo(1);
            assertThat(h.count(255)).isEqualTo(1);
        }

        @Test
        void foldsIntoASmallerNumberOfBins() {
            Histogram h = histogramOf(strip(0, 1, 128, 255), 0, 2);
            assertThat(h.bins()).isEqualTo(2);
            assertThat(h.counts()).containsExactly(2, 2);
        }

        @Test
        void coversTheFullRangeOfASixteenBitCapture() {
            Histogram h = histogramOf(strip16(0, 65535), 0, 4);
            assertThat(h.counts()).containsExactly(1, 0, 0, 1);
            assertThat(h.maxValue()).isEqualTo(65535);
        }

        @Test
        void readsOneChannelOfAColourCapture() {
            Surface s = Surface.create(2, 1, Layout.RGB);
            s.setPixel(0, 0, 10, 20, 30);
            s.setPixel(1, 0, 10, 40, 30);
            assertThat(histogramOf(s, 1).count(20)).isEqualTo(1);
            assertThat(histogramOf(s, 0).count(10)).isEqualTo(2);
        }

        @Test
        void refusesAChannelOutsideTheLayout() {
            assertThatThrownBy(() -> histogramOf(strip(1), 2))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the layout");
        }

        @Test
        void refusesABinCountThatIsNotAWholeNumberAboveZero() {
            assertThatThrownBy(() -> histogramOf(strip(1), 0, 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("above zero");
        }
    }

    @Nested
    class BinArithmetic {
        private final Histogram h = histogramOf(strip(0, 64, 128, 192, 255), 0, 4);

        @Test
        void mapsAValueToItsBin() {
            assertThat(h.binFor(0)).isEqualTo(0);
            assertThat(h.binFor(64)).isEqualTo(1);
            assertThat(h.binFor(255)).isEqualTo(3);
        }

        @Test
        void reportsTheValueAtTheCentreOfABin() {
            assertThat(h.valueAt(0)).isEqualTo(32);
            assertThat(h.valueAt(3)).isEqualTo(224);
        }

        @Test
        void roundTripsAValueThroughItsBin() {
            for (int value : new int[] {0, 30, 100, 200, 255}) {
                assertThat(h.binFor(h.valueAt(h.binFor(value)))).isEqualTo(h.binFor(value));
            }
        }
    }

    @Nested
    class Summaries {
        @Test
        void averagesUsingTheBinCentres() {
            Histogram h = histogramOf(strip(10, 10, 10, 10));
            assertThat(h.mean()).isCloseTo(10.5, byLessThan(0.5e-6));
        }

        @Test
        void findsTheBusiestBinAndBreaksATieDownward() {
            assertThat(histogramOf(strip(7, 7, 9)).modeBin()).isEqualTo(7);
            assertThat(histogramOf(strip(7, 9)).modeBin()).isEqualTo(7);
        }

        @Test
        void findsTheFirstAndLastOccupiedBins() {
            Histogram h = histogramOf(strip(40, 100, 200));
            assertThat(h.minBin()).isEqualTo(40);
            assertThat(h.maxBin()).isEqualTo(200);
        }

        @Test
        void reportsMinusOneForAnEmptyDistribution() {
            Histogram empty = new Histogram(new long[8], 255);
            assertThat(empty.minBin()).isEqualTo(-1);
            assertThat(empty.maxBin()).isEqualTo(-1);
            assertThat(empty.mean()).isNaN();
            assertThat(empty.percentile(0.5)).isNaN();
            assertThat(empty.otsu()).isNaN();
            assertThat(empty.clipping()).isEqualTo(new Histogram.Clipping(0, 0));
        }
    }

    @Nested
    class PercentileAndClipping {
        @Test
        void walksTheDistribution() {
            Histogram h = histogramOf(strip(0, 10, 20, 30, 250));
            assertThat(h.percentile(0)).isCloseTo(0.5, byLessThan(0.5e-6));
            assertThat(h.percentile(1)).isCloseTo(250.5, byLessThan(0.5e-6));
            assertThat(h.percentile(0.5)).isCloseTo(20.5, byLessThan(0.5e-6));
        }

        @Test
        void measuresTheShareAtEachEnd() {
            Histogram h = histogramOf(strip(0, 0, 128, 255));
            assertThat(h.clipping()).isEqualTo(new Histogram.Clipping(0.5, 0.25));
        }

        @Test
        void buildsARunningTotal() {
            Histogram h = histogramOf(strip(0, 1, 1), 0, 4);
            assertThat(h.cumulative()).containsExactly(3, 3, 3, 3);
        }
    }

    @Nested
    class Otsu {
        @Test
        void splitsATwoLobedDistributionBetweenTheLobes() {
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < 50; i += 1) {
                values.add(20 + (i % 5));
            }
            for (int i = 0; i < 50; i += 1) {
                values.add(200 + (i % 5));
            }
            double threshold = histogramOf(
                            strip(values.stream().mapToInt(Integer::intValue).toArray()))
                    .otsu();
            assertThat(values.stream().filter(v -> v <= threshold)).hasSize(50);
            assertThat(values.stream().filter(v -> v > threshold)).hasSize(50);
        }

        @Test
        void doesNotCrashOnASingleValuedDistribution() {
            double threshold = histogramOf(strip(100, 100, 100)).otsu();
            assertThat(Double.isFinite(threshold)).isTrue();
        }

        @Test
        void findsAThresholdThatSeparatesABedFromASheet() {
            Surface s = Surface.create(20, 20, Layout.GRAY, 8, 30);
            for (int y = 5; y < 15; y += 1) {
                for (int x = 5; x < 15; x += 1) {
                    s.set(x, y, 0, 210);
                }
            }
            double threshold = histogramOf(s).otsu();
            assertThat(threshold).isGreaterThan(30);
            assertThat(threshold).isLessThan(210);
        }
    }

    @Nested
    class HistogramsOf {
        @Test
        void givesOnePerChannel() {
            Surface s = Surface.create(1, 1, Layout.RGB);
            s.setPixel(0, 0, 1, 2, 3);
            List<Histogram> all = histogramsOf(s);
            assertThat(all).hasSize(3);
            assertThat(all.get(0).count(1)).isEqualTo(1);
            assertThat(all.get(2).count(3)).isEqualTo(1);
        }

        @Test
        void passesTheBinCountThrough() {
            Surface s = Surface.create(1, 1, Layout.RGB);
            assertThat(histogramsOf(s, 8).get(0).bins()).isEqualTo(8);
        }
    }

    @Nested
    class Serialisation {
        @Test
        void reportsItsShapeAndTheCounts() {
            Map<String, Object> json = histogramOf(strip(0, 1), 0, 2).toJson();
            assertThat(json.get("bins")).isEqualTo(2.0);
            assertThat(json.get("total")).isEqualTo(2.0);
            assertThat(json.get("counts")).isEqualTo(List.of(2.0, 0.0));
        }
    }
}
