package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.Backgrounds.objectMask;
import static io.github.rkaushikdev.decklewright.detect.Targets.endPatches;
import static io.github.rkaushikdev.decklewright.detect.Targets.locateTarget;
import static io.github.rkaushikdev.decklewright.detect.Targets.patchRange;
import static io.github.rkaushikdev.decklewright.detect.Targets.readPatches;
import static io.github.rkaushikdev.decklewright.detect.Targets.stepsMonotonically;
import static io.github.rkaushikdev.decklewright.testing.Bench.bench;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.Bench;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TargetTest {

    private static final Rect WEDGE = Rect.of(12, 12, 80, 16);
    private static final TargetOptions DEFAULTS = TargetOptions.defaults();

    /** A bed with a sheet on it and an eight step wedge beside it, rising left to right. */
    static Surface benchWithTarget() {
        return benchWithTarget(false);
    }

    /**
     * A bed with a sheet on it and an eight step wedge beside it.
     *
     * @param scrambled put the patches in a jumbled order rather than rising
     */
    static Surface benchWithTarget(boolean scrambled) {
        int steps = 8;
        Surface surface = bench(
                Bench.Options.defaults().withHalfWidth(50).withHalfHeight(34).withCentre(120, 100));
        int[] order = scrambled ? new int[] {3, 7, 1, 5, 0, 6, 2, 4} : new int[] {0, 1, 2, 3, 4, 5, 6, 7};
        double step = WEDGE.width() / steps;
        for (int i = 0; i < steps; i += 1) {
            double tone = 20 + Num.roundHalfUp((order[i % order.length] / (double) (steps - 1)) * 210);
            paint(surface, Rect.of(WEDGE.left() + i * step, WEDGE.top(), step, WEDGE.height()), tone);
        }
        return surface;
    }

    /** A patch read as one channel of the given mean. */
    static Patch patchOf(double mean) {
        return new Patch(Rect.empty(), List.of(mean), List.of(0.0));
    }

    /** A patch that could not be read at all. */
    static Patch unreadable() {
        return new Patch(Rect.empty(), List.of(), List.of());
    }

    static List<Patch> patchesOf(double... means) {
        return Arrays.stream(means).mapToObj(TargetTest::patchOf).toList();
    }

    @Nested
    class ReadPatches {
        @Test
        void dividesAWideRectAlongItsWidth() {
            Surface surface = Surface.create(40, 10, Layout.GRAY, 8, 0);
            paint(surface, Rect.of(0, 0, 20, 10), 100);
            paint(surface, Rect.of(20, 0, 20, 10), 200);
            List<Patch> patches = readPatches(surface, Rect.of(0, 0, 40, 10), 2);
            assertThat(patches).hasSize(2);
            assertThat(patches.get(0).mean().get(0)).isCloseTo(100, byLessThan(0.5e-6));
            assertThat(patches.get(1).mean().get(0)).isCloseTo(200, byLessThan(0.5e-6));
        }

        @Test
        void dividesATallRectAlongItsHeight() {
            Surface surface = Surface.create(10, 40, Layout.GRAY, 8, 0);
            paint(surface, Rect.of(0, 20, 10, 20), 200);
            List<Patch> patches = readPatches(surface, Rect.of(0, 0, 10, 40), 2);
            assertThat(patches.get(0).mean().get(0)).isCloseTo(0, byLessThan(0.5e-6));
            assertThat(patches.get(1).mean().get(0)).isCloseTo(200, byLessThan(0.5e-6));
        }

        @Test
        void ignoresTheEdgesOfEachCell() {
            Surface surface = Surface.create(40, 10, Layout.GRAY, 8, 100);
            paint(surface, Rect.of(19, 0, 2, 10), 0);
            List<Patch> patches = readPatches(surface, Rect.of(0, 0, 40, 10), 2);
            assertThat(patches.get(0).mean().get(0)).isCloseTo(100, byLessThan(0.5e-6));
            assertThat(patches.get(1).mean().get(0)).isCloseTo(100, byLessThan(0.5e-6));
        }

        @Test
        void readsEveryChannel() {
            Surface surface = Surface.create(20, 10, Layout.RGB);
            paint(surface, surface.bounds(), 10, 20, 30);
            List<Patch> patches = readPatches(surface, surface.bounds(), 1);
            assertThat(patches.get(0).mean().stream().map(Num::roundHalfUp).toList())
                    .isEqualTo(List.of(10.0, 20.0, 30.0));
        }

        @Test
        void reportsAnEmptyPatchForACellThatFellOffTheCapture() {
            Surface surface = Surface.create(20, 10, Layout.GRAY);
            List<Patch> patches = readPatches(surface, Rect.of(100, 0, 40, 10), 2);
            assertThat(patches.get(0).mean()).isEqualTo(List.of());
        }

        @Test
        void refusesACountThatIsNotAWholeNumberAboveZero() {
            Surface surface = Surface.create(20, 10, Layout.GRAY);
            assertThatThrownBy(() -> readPatches(surface, surface.bounds(), 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("above zero");
        }
    }

    @Nested
    class StepsMonotonically {
        private final List<Patch> rising = patchesOf(0, 40, 80, 120);

        @Test
        void acceptsARisingAndAFallingRun() {
            assertThat(stepsMonotonically(rising)).isTrue();
            assertThat(stepsMonotonically(rising.reversed())).isTrue();
        }

        @Test
        void toleratesOneStepTheWrongWay() {
            List<Patch> wobbly = patchesOf(0, 40, 38, 120);
            assertThat(stepsMonotonically(wobbly)).isTrue();
            assertThat(stepsMonotonically(wobbly, 0, 0)).isFalse();
        }

        @Test
        void rejectsAScrambledRun() {
            assertThat(stepsMonotonically(patchesOf(0, 120, 40, 80, 20))).isFalse();
        }

        @Test
        void rejectsARunWithAnUnreadablePatch() {
            assertThat(stepsMonotonically(List.of(unreadable()))).isFalse();
        }
    }

    @Nested
    class PatchRange {
        @Test
        void isTheSpreadBetweenTheEnds() {
            assertThat(patchRange(patchesOf(10, 200, 90))).isEqualTo(190.0);
        }

        @Test
        void isZeroWhenNothingCouldBeRead() {
            assertThat(patchRange(List.of(unreadable()))).isEqualTo(0.0);
        }
    }

    @Nested
    class LocateTarget {
        @Test
        void findsTheWedgeAndNotTheSheet() {
            Surface surface = benchWithTarget();
            Mask mask = objectMask(surface, 4).mask();
            Optional<TargetFinding> found = locateTarget(surface, mask);
            assertThat(found).isPresent();
            assertThat(found.orElseThrow().rect().left()).isGreaterThanOrEqualTo(WEDGE.left() - 2);
            assertThat(found.orElseThrow().rect().right()).isLessThanOrEqualTo(WEDGE.right() + 2);
            assertThat(found.orElseThrow().monotone()).isTrue();
            assertThat(found.orElseThrow().patches()).hasSize(8);
        }

        @Test
        void reportsTheRangeAcrossTheWedge() {
            Surface surface = benchWithTarget();
            Mask mask = objectMask(surface, 4).mask();
            assertThat(locateTarget(surface, mask).orElseThrow().range()).isGreaterThan(150);
        }

        @Test
        void findsNothingWhenThePatchesAreScrambled() {
            Surface surface = benchWithTarget(true);
            Mask mask = objectMask(surface, 4).mask();
            assertThat(locateTarget(surface, mask)).isEmpty();
        }

        @Test
        void findsNothingOnABenchWithNoWedge() {
            Surface surface = bench();
            Mask mask = objectMask(surface, 4).mask();
            assertThat(locateTarget(surface, mask)).isEmpty();
        }

        @Test
        void honoursAMinimumArea() {
            Surface surface = benchWithTarget();
            Mask mask = objectMask(surface, 4).mask();
            assertThat(locateTarget(surface, mask, DEFAULTS.withMinimumArea(100000)))
                    .isEmpty();
        }

        @Test
        void honoursAMinimumRange() {
            Surface surface = benchWithTarget();
            Mask mask = objectMask(surface, 4).mask();
            assertThat(locateTarget(surface, mask, DEFAULTS.withMinimumRange(250)))
                    .isEmpty();
        }
    }

    @Nested
    class EndPatchesBehaviour {
        private final List<Patch> patches = patchesOf(2, 40, 120, 250);

        @Test
        void skipsTheClippedEnds() {
            EndPatches ends = endPatches(patches, 255);
            assertThat(ends.dark().orElseThrow().mean().get(0)).isEqualTo(40.0);
            assertThat(ends.light().orElseThrow().mean().get(0)).isEqualTo(120.0);
        }

        @Test
        void givesNothingBackWhenEveryPatchIsClipped() {
            List<Patch> clipped = patchesOf(0, 255);
            assertThat(endPatches(clipped, 255)).isEqualTo(new EndPatches(Optional.empty(), Optional.empty()));
        }
    }
}
