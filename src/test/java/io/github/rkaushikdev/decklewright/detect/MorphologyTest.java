package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.MaskDrawing.draw;
import static io.github.rkaushikdev.decklewright.detect.MaskDrawing.maskOf;
import static io.github.rkaushikdev.decklewright.detect.Morphology.boundary;
import static io.github.rkaushikdev.decklewright.detect.Morphology.close;
import static io.github.rkaushikdev.decklewright.detect.Morphology.dilate;
import static io.github.rkaushikdev.decklewright.detect.Morphology.erode;
import static io.github.rkaushikdev.decklewright.detect.Morphology.fillHoles;
import static io.github.rkaushikdev.decklewright.detect.Morphology.open;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MorphologyTest {

    @Nested
    class Erode {
        @Test
        void keepsOnlyPixelsWhoseWholeNeighbourhoodIsSet() {
            Mask mask = maskOf(".....", ".###.", ".###.", ".###.", ".....");
            assertThat(draw(erode(mask, 1))).containsExactly(".....", ".....", "..#..", ".....", ".....");
        }

        @Test
        void eatsIntoABlockThatTouchesTheBorder() {
            Mask mask = maskOf("###", "###", "###");
            assertThat(erode(mask, 1).count()).isEqualTo(1);
        }

        @Test
        void isACopyAtRadiusZero() {
            Mask mask = maskOf("##", "..");
            Mask out = erode(mask, 0);
            assertThat(draw(out)).isEqualTo(draw(mask));
            assertThat(out).isNotSameAs(mask);
        }

        @Test
        void removesASinglePixelEntirely() {
            assertThat(erode(maskOf("...", ".#.", "..."), 1).count()).isEqualTo(0);
        }

        @Test
        void refusesANegativeRadius() {
            assertThatThrownBy(() -> erode(maskOf("#"), -1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("radius must not be negative");
        }
    }

    @Nested
    class Dilate {
        @Test
        void growsASinglePixelIntoASquare() {
            assertThat(draw(dilate(maskOf(".....", ".....", "..#..", ".....", "....."), 1)))
                    .containsExactly(".....", ".###.", ".###.", ".###.", ".....");
        }

        @Test
        void clipsAtTheBorder() {
            assertThat(dilate(maskOf("#..", "...", "..."), 1).count()).isEqualTo(4);
        }

        @Test
        void leavesAnEmptyMaskEmpty() {
            assertThat(dilate(new Mask(4, 4), 2).count()).isEqualTo(0);
        }

        @Test
        void growsByTheRadiusItIsGiven() {
            Mask mask = new Mask(11, 11);
            mask.set(5, 5, 1);
            assertThat(dilate(mask, 2).count()).isEqualTo(25);
            assertThat(dilate(mask, 3).count()).isEqualTo(49);
        }
    }

    @Nested
    class Open {
        @Test
        void removesSpeckleAndLeavesTheBlock() {
            Mask mask = maskOf(".......", ".#####.", ".#####.", ".#####.", ".#####.", ".......", "#......");
            Mask out = open(mask, 1);
            assertThat(out.get(0, 6)).isEqualTo(0);
            assertThat(out.get(3, 3)).isEqualTo(1);
        }

        @Test
        void neverAddsAPixelTheOriginalDidNotHaveNearby() {
            Mask mask = maskOf(".....", ".###.", ".###.", ".###.", ".....");
            Mask out = open(mask, 1);
            assertThat(out.count()).isLessThanOrEqualTo(mask.count());
        }
    }

    @Nested
    class Close {
        @Test
        void fillsANotchOnTheEdgeOfABlock() {
            Mask mask = maskOf(".......", ".#####.", ".##.##.", ".#####.", ".#####.", ".......", ".......");
            assertThat(close(mask, 1).get(3, 2)).isEqualTo(1);
        }

        @Test
        void leavesASolidBlockAloneInTheMiddle() {
            Mask mask = maskOf(".....", ".###.", ".###.", ".###.", ".....");
            assertThat(close(mask, 1).get(2, 2)).isEqualTo(1);
        }

        @Test
        void doesNotShaveTheBorderOffABlockThatReachesIt() {
            Mask mask = maskOf("####", "####", "####", "####");
            assertThat(close(mask, 1).count()).isEqualTo(16);
            assertThat(close(mask, 2).count()).isEqualTo(16);
        }

        @Test
        void stillLetsErodeEatTheBorderWhenItIsAskedPlainly() {
            Mask mask = maskOf("####", "####", "####", "####");
            assertThat(erode(mask, 1).count()).isEqualTo(4);
            assertThat(erode(mask, 1, true).count()).isEqualTo(16);
        }
    }

    @Nested
    class Boundary {
        @Test
        void keepsTheOutlineAndDropsTheInterior() {
            Mask mask = maskOf(".....", ".###.", ".###.", ".###.", ".....");
            assertThat(draw(boundary(mask))).containsExactly(".....", ".###.", ".#.#.", ".###.", ".....");
        }

        @Test
        void treatsAPixelOnTheMaskEdgeAsBoundary() {
            assertThat(boundary(maskOf("##", "##")).count()).isEqualTo(4);
        }

        @Test
        void leavesAnEmptyMaskEmpty() {
            assertThat(boundary(new Mask(3, 3)).count()).isEqualTo(0);
        }
    }

    @Nested
    class FillHoles {
        @Test
        void fillsAnEnclosedGap() {
            Mask mask = maskOf(".....", ".###.", ".#.#.", ".###.", ".....");
            assertThat(draw(fillHoles(mask))).containsExactly(".....", ".###.", ".###.", ".###.", ".....");
        }

        @Test
        void leavesAGapThatReachesTheBorderAlone() {
            Mask mask = maskOf(".###.", ".#.#.", ".#.#.", ".#.#.", ".....");
            assertThat(fillHoles(mask).get(2, 1)).isEqualTo(0);
        }

        @Test
        void leavesASolidMaskAlone() {
            Mask mask = maskOf("###", "###", "###");
            assertThat(fillHoles(mask).count()).isEqualTo(9);
        }

        @Test
        void fillsALargeHoleWithoutRunningOutOfStack() {
            Mask mask = new Mask(120, 120);
            for (int y = 5; y < 115; y += 1) {
                for (int x = 5; x < 115; x += 1) {
                    boolean onRing = y < 10 || y >= 110 || x < 10 || x >= 110;
                    mask.set(x, y, onRing ? 1 : 0);
                }
            }
            assertThat(fillHoles(mask).count()).isEqualTo(110 * 110);
        }
    }
}
