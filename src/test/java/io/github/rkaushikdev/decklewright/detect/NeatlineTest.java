package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.Neatlines.collarWidths;
import static io.github.rkaushikdev.decklewright.detect.Neatlines.darkness;
import static io.github.rkaushikdev.decklewright.detect.Neatlines.findNeatline;
import static io.github.rkaushikdev.decklewright.detect.Neatlines.looksLikeQuadrangle;
import static io.github.rkaushikdev.decklewright.detect.Neatlines.strongestLine;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Along;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NeatlineTest {

    private static final Rect SHEET = Rect.of(20, 20, 160, 120);

    /** The default map sheet: a collar of twelve on the top, left and right, thirty at the bottom. */
    static Surface mapSheet() {
        return mapSheet(12, 30, 30, Set.of());
    }

    /**
     * A sheet of paper with a ruled border drawn on it, and a wider collar at the bottom the way a
     * quadrangle carries its legend.
     *
     * @param inset collar on the top, left and right
     * @param bottomInset collar at the bottom
     * @param ink how dark the ruled line is
     * @param omit sides to leave undrawn
     */
    static Surface mapSheet(double inset, double bottomInset, double ink, Set<Side> omit) {
        Surface surface = Surface.create(200, 160, Layout.GRAY, 8, 40);
        paint(surface, SHEET, 226);

        Rect line = Rect.fromEdges(
                SHEET.left() + inset, SHEET.top() + inset, SHEET.right() - inset, SHEET.bottom() - bottomInset);
        if (!omit.contains(Side.TOP)) {
            paint(surface, Rect.of(line.left(), line.top(), line.width(), 2), ink);
        }
        if (!omit.contains(Side.BOTTOM)) {
            paint(surface, Rect.of(line.left(), line.bottom() - 2, line.width(), 2), ink);
        }
        if (!omit.contains(Side.LEFT)) {
            paint(surface, Rect.of(line.left(), line.top(), 2, line.height()), ink);
        }
        if (!omit.contains(Side.RIGHT)) {
            paint(surface, Rect.of(line.right() - 2, line.top(), 2, line.height()), ink);
        }
        return surface;
    }

    @Nested
    class Darkness {
        @Test
        void isHighWhereThePaperIsDark() {
            Surface surface = Surface.create(10, 4, Layout.GRAY, 8, 200);
            paint(surface, Rect.of(0, 1, 10, 1), 20);
            double[] profile = darkness(surface, surface.bounds(), Along.ROW);
            assertThat(profile[1]).isGreaterThan(profile[0]);
            assertThat(profile[0]).isCloseTo(55, byLessThan(0.5e-6));
        }

        @Test
        void readsOnlyTheRegionItIsGiven() {
            Surface surface = Surface.create(10, 4, Layout.GRAY, 8, 200);
            paint(surface, Rect.of(0, 0, 10, 1), 0);
            double[] profile = darkness(surface, Rect.of(0, 1, 10, 3), Along.ROW);
            assertThat(profile).hasSize(3);
            assertThat(Arrays.stream(profile).max().orElseThrow()).isCloseTo(55, byLessThan(0.5e-6));
        }
    }

    @Nested
    class StrongestLine {
        private final double[] profile = {0, 0, 40, 0, 0};

        @Test
        void findsTheTroughAndReportsItsContrast() {
            assertThat(strongestLine(profile, 0, 5)).isEqualTo(new Trough(2, 40));
        }

        @Test
        void looksOnlyInsideTheWindow() {
            assertThat(strongestLine(profile, 3, 5).at()).isEqualTo(-1);
        }

        @Test
        void takesTheWindowEitherWayRound() {
            assertThat(strongestLine(profile, 5, 0).at()).isEqualTo(2);
        }

        @Test
        void reportsNothingForAWindowWithNoRoom() {
            assertThat(strongestLine(new double[] {1, 2, 3}, 1, 1)).isEqualTo(new Trough(-1, 0));
        }

        @Test
        void reportsNoContrastOnAFlatRun() {
            assertThat(strongestLine(new double[] {5, 5, 5, 5}, 0, 4).contrast())
                    .isEqualTo(0.0);
        }
    }

    @Nested
    class FindNeatline {
        @Test
        void landsOnTheRuledBorder() {
            Neatline found = findNeatline(mapSheet(), SHEET);
            assertThat(found.complete()).isTrue();
            assertThat(found.rect().left()).isEqualTo(32.0);
            assertThat(found.rect().top()).isEqualTo(32.0);
            assertThat(Math.abs(found.rect().right() - 168)).isLessThanOrEqualTo(2);
            assertThat(Math.abs(found.rect().bottom() - 110)).isLessThanOrEqualTo(2);
        }

        @Test
        void reportsTheContrastItFoundOnEachSide() {
            Neatline found = findNeatline(mapSheet(), SHEET);
            for (Side side : List.of(Side.TOP, Side.RIGHT, Side.BOTTOM, Side.LEFT)) {
                assertThat(found.contrast().get(side)).isGreaterThan(12);
            }
        }

        @Test
        void namesTheSidesItCouldNotFind() {
            Neatline found = findNeatline(mapSheet(12, 30, 30, Set.of(Side.RIGHT)), SHEET);
            assertThat(found.complete()).isFalse();
            assertThat(found.missing()).isEqualTo(List.of(Side.RIGHT));
            assertThat(found.rect().right()).isEqualTo(SHEET.right());
        }

        @Test
        void fallsBackToThePaperEdgeOnEveryMissingSide() {
            Surface plain = Surface.create(200, 160, Layout.GRAY, 8, 40);
            paint(plain, SHEET, 226);
            Neatline found = findNeatline(plain, SHEET);
            assertThat(found.missing()).hasSize(4);
            assertThat(found.rect().approxEquals(SHEET)).isTrue();
        }

        @Test
        void canBeToldToDemandMoreContrast() {
            Surface faint = mapSheet(12, 30, 200, Set.of());
            assertThat(findNeatline(faint, SHEET, Neatlines.DEFAULT_BAND, 5).complete())
                    .isTrue();
            assertThat(findNeatline(faint, SHEET, Neatlines.DEFAULT_BAND, 40).complete())
                    .isFalse();
        }

        @Test
        void doesNotLookFurtherInThanTheBandAllows() {
            Neatline found =
                    findNeatline(mapSheet(50, 50, 30, Set.of()), SHEET, 0.1, Neatlines.DEFAULT_MINIMUM_CONTRAST);
            assertThat(found.missing().size()).isGreaterThan(0);
        }

        @Test
        void refusesABandOutsideTheSensibleRange() {
            assertThatThrownBy(() -> findNeatline(mapSheet(), SHEET, 0.6, Neatlines.DEFAULT_MINIMUM_CONTRAST))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("band must be between");
        }
    }

    @Nested
    class CollarWidths {
        @Test
        void measuresEachSide() {
            Insets widths = collarWidths(Rect.of(0, 0, 100, 100), Rect.of(10, 5, 80, 70));
            assertThat(widths).isEqualTo(new Insets(5, 10, 25, 10));
        }

        @Test
        void isZeroAllRoundWhenTheNeatlineIsThePaperEdge() {
            Rect sheet = Rect.of(0, 0, 100, 100);
            assertThat(collarWidths(sheet, sheet)).isEqualTo(new Insets(0, 0, 0, 0));
        }
    }

    @Nested
    class LooksLikeQuadrangle {
        @Test
        void acceptsAWideBottomCollar() {
            assertThat(looksLikeQuadrangle(new Insets(10, 10, 30, 10))).isTrue();
        }

        @Test
        void rejectsACollarThatIsWidestSomewhereElse() {
            assertThat(looksLikeQuadrangle(new Insets(30, 10, 10, 10))).isFalse();
            assertThat(looksLikeQuadrangle(new Insets(0, 0, 0, 0))).isFalse();
        }

        @Test
        void agreesWithWhatTheDetectorFoundOnARealLookingSheet() {
            Neatline found = findNeatline(mapSheet(), SHEET);
            assertThat(looksLikeQuadrangle(collarWidths(SHEET, found.rect()))).isTrue();
        }
    }
}
