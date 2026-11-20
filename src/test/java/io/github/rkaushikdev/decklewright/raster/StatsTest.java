package io.github.rkaushikdev.decklewright.raster;

import static io.github.rkaushikdev.decklewright.raster.Stats.allChannelStats;
import static io.github.rkaushikdev.decklewright.raster.Stats.channelStats;
import static io.github.rkaushikdev.decklewright.raster.Stats.difference;
import static io.github.rkaushikdev.decklewright.raster.Stats.meanPixel;
import static io.github.rkaushikdev.decklewright.raster.Stats.meanProfile;
import static io.github.rkaushikdev.decklewright.raster.Stats.signalToNoise;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.gray;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StatsTest {

    @Nested
    class ChannelStatsBehaviour {
        @Test
        void reportsTheFourSummariesAndTheCount() {
            ChannelStats stats = channelStats(gray(row(2, 4), row(4, 6)), 0);
            assertThat(stats.min()).isEqualTo(2);
            assertThat(stats.max()).isEqualTo(6);
            assertThat(stats.mean()).isEqualTo(4);
            assertThat(stats.stdDev()).isCloseTo(Math.sqrt(2), byLessThan(0.5e-12));
            assertThat(stats.count()).isEqualTo(4);
        }

        @Test
        void reportsZeroDeviationOnAFlatPatch() {
            Surface flat = Surface.create(8, 8, Layout.GRAY, 8, 128);
            ChannelStats stats = channelStats(flat, 0);
            assertThat(stats.stdDev()).isEqualTo(0);
            assertThat(stats.mean()).isEqualTo(128);
        }

        @Test
        void agreesWithANaiveComputationOnASixteenBitPatch() {
            Surface s = Surface.create(4, 4, Layout.GRAY, 16);
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < 16; i += 1) {
                int value = 40000 + (i % 7);
                values.add(value);
                s.set(i % 4, i / 4, 0, value);
            }
            double naive = values.stream().mapToDouble(Integer::doubleValue).reduce(0, Double::sum) / values.size();
            assertThat(channelStats(s, 0).mean()).isCloseTo(naive, byLessThan(0.5e-9));
        }

        @Test
        void measuresOnlyWhatAViewCovers() {
            Surface s = gray(row(0, 0, 0), row(0, 100, 0), row(0, 0, 0));
            SurfaceView middle = new SurfaceView(s, Rect.of(1, 1, 1, 1));
            assertThat(channelStats(middle, 0).mean()).isEqualTo(100);
        }

        @Test
        void refusesAChannelOutsideTheLayout() {
            assertThatThrownBy(() -> channelStats(gray(row(1)), 3))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the layout");
        }
    }

    @Nested
    class AllChannelStatsAndMeanPixel {
        @Test
        void givesOneEntryPerChannel() {
            Surface s = Surface.create(2, 1, Layout.RGB);
            s.setPixel(0, 0, 0, 10, 20);
            s.setPixel(1, 0, 100, 30, 20);
            List<ChannelStats> all = allChannelStats(s);
            assertThat(all).hasSize(3);
            assertThat(all.get(0).mean()).isEqualTo(50);
            assertThat(meanPixel(s)).containsExactly(50, 20, 20);
        }
    }

    @Nested
    class SignalToNoise {
        @Test
        void isInfiniteOnAPerfectlyFlatPatch() {
            Surface flat = Surface.create(4, 4, Layout.GRAY, 8, 200);
            assertThat(signalToNoise(flat)).isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        void dropsAsTheNoiseGrows() {
            Surface quiet = gray(row(100, 101, 100, 99));
            Surface noisy = gray(row(100, 130, 70, 100));
            assertThat(signalToNoise(quiet)).isGreaterThan(signalToNoise(noisy));
        }

        @Test
        void matchesTheDecibelDefinition() {
            Surface s = gray(row(90, 110), row(90, 110));
            assertThat(signalToNoise(s)).isCloseTo(20 * StrictMath.log10(100.0 / 10), byLessThan(0.5e-9));
        }
    }

    @Nested
    class DifferenceBehaviour {
        @Test
        void isZeroBetweenASurfaceAndItsClone() {
            Surface s = gray(row(1, 2), row(3, 4));
            assertThat(difference(s, s.copy())).isEqualTo(new Difference(0, 0, 4));
        }

        @Test
        void reportsTheRootMeanSquareAndTheWorstSample() {
            Surface a = gray(row(0, 0));
            Surface b = gray(row(3, 4));
            Difference out = difference(a, b);
            assertThat(out.peak()).isEqualTo(4);
            assertThat(out.rms()).isCloseTo(Math.sqrt((9 + 16) / 2.0), byLessThan(0.5e-12));
        }

        @Test
        void refusesTwoRegionsOfDifferentShape() {
            assertThatThrownBy(() -> difference(gray(row(1, 2)), gray(row(1))))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("not the same shape");
        }
    }

    @Nested
    class MeanProfile {
        private final Surface s = gray(row(0, 10, 20), row(30, 40, 50));

        @Test
        void averagesAlongRowsByDefault() {
            assertThat(meanProfile(s)).containsExactly(10, 40);
        }

        @Test
        void averagesDownColumnsWhenAsked() {
            assertThat(meanProfile(s, Along.COLUMN)).containsExactly(15, 25, 35);
        }

        @Test
        void readsTheChannelItIsToldTo() {
            Surface colour = Surface.create(2, 1, Layout.RGB);
            colour.setPixel(0, 0, 0, 60, 0);
            colour.setPixel(1, 0, 0, 20, 0);
            assertThat(meanProfile(colour, Along.ROW, 1)).containsExactly(40);
        }

        @Test
        void refusesAnAxisOrChannelItDoesNotKnow() {
            // An axis is an enum, so an unknown one can only arrive by name.
            assertThatThrownBy(() -> meanProfile(s, Along.parse("diagonal")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("along must be one of");
            assertThatThrownBy(() -> meanProfile(s, Along.ROW, 4))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the layout");
        }

        @Test
        void runsOverAViewInViewLocalCoordinates() {
            SurfaceView view = new SurfaceView(s, Rect.of(1, 0, 2, 2));
            assertThat(meanProfile(view)).containsExactly(15, 45);
        }
    }
}
