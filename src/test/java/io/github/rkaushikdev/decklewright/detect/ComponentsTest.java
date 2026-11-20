package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.Components.dropSmaller;
import static io.github.rkaushikdev.decklewright.detect.Components.fillRatio;
import static io.github.rkaushikdev.decklewright.detect.Components.keepLargest;
import static io.github.rkaushikdev.decklewright.detect.Components.labelComponents;
import static io.github.rkaushikdev.decklewright.detect.Components.largestComponent;
import static io.github.rkaushikdev.decklewright.detect.Components.maskOfComponent;
import static io.github.rkaushikdev.decklewright.detect.MaskDrawing.maskOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ComponentsTest {

    private static final Mask TWO_BLOBS = maskOf("##...", "##...", ".....", "...#.", "...##");

    @Nested
    class LabelComponents {
        @Test
        void findsEachBlobAndNumbersThemFromOne() {
            List<Component> components = labelComponents(TWO_BLOBS).components();
            assertThat(components.stream().map(Component::id).toList()).isEqualTo(List.of(1, 2));
            assertThat(components.stream().map(Component::area).toList()).isEqualTo(List.of(4, 3));
        }

        @Test
        void boundsEachBlob() {
            List<Component> components = labelComponents(TWO_BLOBS).components();
            assertThat(components.get(0).bounds().toJson())
                    .isEqualTo(Map.of("x", 0.0, "y", 0.0, "width", 2.0, "height", 2.0));
            assertThat(components.get(1).bounds().toJson())
                    .isEqualTo(Map.of("x", 3.0, "y", 3.0, "width", 2.0, "height", 2.0));
        }

        @Test
        void centresEachBlob() {
            List<Component> components = labelComponents(TWO_BLOBS).components();
            assertThat(components.get(0).centroid().toJson()).isEqualTo(Map.of("x", 0.5, "y", 0.5));
        }

        @Test
        void saysWhichBlobsTouchTheEdgeOfTheCapture() {
            List<Component> components = labelComponents(TWO_BLOBS).components();
            assertThat(components.get(0).touchesEdge()).isTrue();
            assertThat(components.get(1).touchesEdge()).isTrue();

            Mask inner = maskOf(".....", ".###.", ".###.", ".###.", ".....");
            assertThat(labelComponents(inner).components().get(0).touchesEdge()).isFalse();
        }

        @Test
        void labelsTheBackgroundAsZero() {
            Labelling labels = labelComponents(TWO_BLOBS);
            assertThat(labels.label(2)).isEqualTo(0);
            assertThat(labels.label(0)).isEqualTo(1);
        }

        @Test
        void findsNothingInAnEmptyMask() {
            assertThat(labelComponents(new Mask(4, 4)).components()).isEqualTo(List.of());
        }

        @Test
        void joinsDiagonalNeighboursUnderEightConnectivityAndNotUnderFour() {
            Mask diagonal = maskOf("#..", ".#.", "..#");
            assertThat(labelComponents(diagonal).components()).hasSize(1);
            assertThat(labelComponents(diagonal, Connectivity.FOUR).components())
                    .hasSize(3);
        }

        @Test
        void refusesAConnectivityItDoesNotKnow() {
            // Connectivity is an enum, so the unknown count is refused where it is turned into one.
            assertThatThrownBy(() -> labelComponents(TWO_BLOBS, Connectivity.of(6)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("connectivity must be one of 4, 8");
        }

        @Test
        void walksALongSnakeWithoutRunningOutOfStack() {
            Mask mask = new Mask(200, 200);
            for (int y = 0; y < 200; y += 2) {
                for (int x = 0; x < 200; x += 1) {
                    mask.set(x, y, 1);
                }
                int joinAt = (y / 2) % 2 == 0 ? 199 : 0;
                mask.set(joinAt, y + 1, 1);
            }
            assertThat(labelComponents(mask).components()).hasSize(1);
        }
    }

    @Nested
    class MaskOfComponent {
        @Test
        void keepsOnlyTheLabelledPixels() {
            Labelling labels = labelComponents(TWO_BLOBS);
            Mask first = maskOfComponent(TWO_BLOBS, labels, 1);
            assertThat(first.count()).isEqualTo(4);
            assertThat(first.get(3, 3)).isEqualTo(0);
        }

        @Test
        void givesAnEmptyMaskForALabelNothingCarries() {
            Labelling labels = labelComponents(TWO_BLOBS);
            assertThat(maskOfComponent(TWO_BLOBS, labels, 99).count()).isEqualTo(0);
        }
    }

    @Nested
    class LargestComponent {
        @Test
        void picksTheBiggestBlobAndReportsIt() {
            ComponentMask found = largestComponent(TWO_BLOBS).orElseThrow();
            assertThat(found.component().area()).isEqualTo(4);
            assertThat(found.mask().count()).isEqualTo(4);
        }

        @Test
        void givesNothingBackForAnEmptyMask() {
            assertThat(largestComponent(new Mask(3, 3))).isEmpty();
        }
    }

    @Nested
    class KeepLargest {
        @Test
        void keepsTheCountItIsGiven() {
            Mask three = maskOf("##...", "##..#", ".....", "###..", "###..");
            assertThat(keepLargest(three, 1).count()).isEqualTo(6);
            assertThat(keepLargest(three, 2).count()).isEqualTo(10);
            assertThat(keepLargest(three, 9).count()).isEqualTo(three.count());
        }

        @Test
        void refusesACountThatIsNotAWholeNumberAboveZero() {
            assertThatThrownBy(() -> keepLargest(TWO_BLOBS, 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("above zero");
        }
    }

    @Nested
    class DropSmaller {
        @Test
        void removesSpeckleBelowTheArea() {
            Mask speckled = maskOf("####.", "####.", ".....", "....#", ".....");
            assertThat(dropSmaller(speckled, 4).count()).isEqualTo(8);
            assertThat(dropSmaller(speckled, 1).count()).isEqualTo(9);
        }

        @Test
        void canRemoveEverything() {
            assertThat(dropSmaller(TWO_BLOBS, 100).count()).isEqualTo(0);
        }
    }

    @Nested
    class FillRatio {
        @Test
        void isOneForASolidRectangle() {
            List<Component> components =
                    labelComponents(maskOf(".....", ".###.", ".###.", ".....")).components();
            assertThat(fillRatio(components.get(0))).isEqualTo(1.0);
        }

        @Test
        void isLowForAThinDiagonal() {
            List<Component> components =
                    labelComponents(maskOf("#...", ".#..", "..#.", "...#")).components();
            assertThat(fillRatio(components.get(0))).isEqualTo(0.25);
        }
    }
}
