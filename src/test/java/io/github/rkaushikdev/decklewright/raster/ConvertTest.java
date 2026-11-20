package io.github.rkaushikdev.decklewright.raster;

import static io.github.rkaushikdev.decklewright.raster.Conversions.LUMA_601;
import static io.github.rkaushikdev.decklewright.raster.Conversions.LUMA_709;
import static io.github.rkaushikdev.decklewright.raster.Conversions.addAlpha;
import static io.github.rkaushikdev.decklewright.raster.Conversions.byteSize;
import static io.github.rkaushikdev.decklewright.raster.Conversions.dropAlpha;
import static io.github.rkaushikdev.decklewright.raster.Conversions.scaleSample;
import static io.github.rkaushikdev.decklewright.raster.Conversions.toColour;
import static io.github.rkaushikdev.decklewright.raster.Conversions.toDepth;
import static io.github.rkaushikdev.decklewright.raster.Conversions.toGray;
import static io.github.rkaushikdev.decklewright.raster.Conversions.toLayout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConvertTest {

    @Nested
    class ScaleSample {
        @Test
        void sendsFullScaleToFullScale() {
            assertThat(scaleSample(255, 8, 16)).isEqualTo(65535);
            assertThat(scaleSample(65535, 16, 8)).isEqualTo(255);
        }

        @Test
        void keepsBlackAtBlack() {
            assertThat(scaleSample(0, 8, 16)).isEqualTo(0);
            assertThat(scaleSample(0, 16, 8)).isEqualTo(0);
        }

        @Test
        void isANoOpWhenTheDepthsMatch() {
            assertThat(scaleSample(123, 8, 8)).isEqualTo(123);
        }

        @Test
        void roundTripsEveryEightBitValue() {
            for (int v = 0; v <= 255; v += 1) {
                assertThat(scaleSample(scaleSample(v, 8, 16), 16, 8)).isEqualTo(v);
            }
        }
    }

    @Nested
    class ToDepth {
        @Test
        void promotesACaptureWithoutMovingWhite() {
            Surface s = Surface.create(2, 1, Layout.GRAY, 8, 255);
            Surface out = toDepth(s, 16);
            assertThat(out.depth()).isEqualTo(16);
            assertThat(out.get(0, 0)).isEqualTo(65535);
        }

        @Test
        void demotesAndRounds() {
            Surface s = Surface.create(1, 1, Layout.GRAY, 16);
            s.set(0, 0, 0, 32896);
            assertThat(toDepth(s, 8).get(0, 0)).isEqualTo(128);
        }

        @Test
        void clonesWhenTheDepthAlreadyMatches() {
            Surface s = Surface.create(1, 1, Layout.GRAY, 8, 9);
            Surface out = toDepth(s, 8);
            assertThat(out).isNotSameAs(s);
            assertThat(out.get(0, 0)).isEqualTo(9);
        }
    }

    @Nested
    class ToGray {
        @Test
        void usesRec709ByDefault() {
            Surface s = Surface.create(1, 1, Layout.RGB);
            s.setPixel(0, 0, 255, 0, 0);
            assertThat(toGray(s).get(0, 0)).isEqualTo((int) Num.roundHalfUp(255 * LUMA_709.get(0)));
        }

        @Test
        void takesAnotherSetOfWeights() {
            Surface s = Surface.create(1, 1, Layout.RGB);
            s.setPixel(0, 0, 255, 0, 0);
            assertThat(toGray(s, LUMA_601).get(0, 0)).isEqualTo((int) Num.roundHalfUp(255 * LUMA_601.get(0)));
        }

        @Test
        void keepsANeutralValueUnchanged() {
            Surface s = Surface.create(1, 1, Layout.RGB, 8, 120);
            assertThat(toGray(s).get(0, 0)).isEqualTo(120);
        }

        @Test
        void carriesAlphaThrough() {
            Surface s = Surface.create(1, 1, Layout.RGBA);
            s.setPixel(0, 0, 10, 10, 10, 77);
            Surface out = toGray(s);
            assertThat(out.layout()).isEqualTo(Layout.GRAYA);
            assertThat(out.getPixel(0, 0)).containsExactly(10, 77);
        }

        @Test
        void clonesACaptureThatIsAlreadyGray() {
            Surface s = Surface.create(1, 1, Layout.GRAY, 8, 5);
            assertThat(toGray(s).layout()).isEqualTo(Layout.GRAY);
        }

        @Test
        void refusesAWeightListOfTheWrongLength() {
            Surface s = Surface.create(1, 1, Layout.RGB);
            assertThatThrownBy(() -> toGray(s, List.of(1.0, 0.0)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("exactly three weights");
        }
    }

    @Nested
    class ToColour {
        @Test
        void copiesTheValueIntoAllThreeChannels() {
            Surface s = Surface.create(1, 1, Layout.GRAY, 8, 90);
            assertThat(toColour(s).getPixel(0, 0)).containsExactly(90, 90, 90);
        }

        @Test
        void keepsAlpha() {
            Surface s = Surface.create(1, 1, Layout.GRAYA);
            s.setPixel(0, 0, 90, 30);
            Surface out = toColour(s);
            assertThat(out.layout()).isEqualTo(Layout.RGBA);
            assertThat(out.getPixel(0, 0)).containsExactly(90, 90, 90, 30);
        }

        @Test
        void clonesACaptureThatIsAlreadyColour() {
            Surface s = Surface.create(1, 1, Layout.RGB, 8, 1);
            assertThat(toColour(s).layout()).isEqualTo(Layout.RGB);
        }
    }

    @Nested
    class AddAlpha {
        @Test
        void addsAFullyOpaqueChannel() {
            Surface s = Surface.create(1, 1, Layout.RGB, 8, 40);
            Surface out = addAlpha(s);
            assertThat(out.layout()).isEqualTo(Layout.RGBA);
            assertThat(out.getPixel(0, 0)).containsExactly(40, 40, 40, 255);
        }

        @Test
        void takesAnotherCoverageValue() {
            Surface s = Surface.create(1, 1, Layout.GRAY, 8, 40);
            assertThat(addAlpha(s, 0).getPixel(0, 0)).containsExactly(40, 0);
        }

        @Test
        void usesFullScaleForTheDepth() {
            Surface s = Surface.create(1, 1, Layout.GRAY, 16, 40);
            assertThat(addAlpha(s).getPixel(0, 0)).containsExactly(40, 65535);
        }
    }

    @Nested
    class DropAlpha {
        @Test
        void leavesAFullyOpaquePixelAlone() {
            Surface s = Surface.create(1, 1, Layout.RGBA);
            s.setPixel(0, 0, 10, 20, 30, 255);
            assertThat(dropAlpha(s).getPixel(0, 0)).containsExactly(10, 20, 30);
        }

        @Test
        void replacesAFullyTransparentPixelWithTheBackground() {
            Surface s = Surface.create(1, 1, Layout.RGBA);
            s.setPixel(0, 0, 10, 20, 30, 0);
            assertThat(dropAlpha(s, Fill.uniform(255)).getPixel(0, 0)).containsExactly(255, 255, 255);
        }

        @Test
        void mixesAtHalfCoverage() {
            Surface s = Surface.create(1, 1, Layout.GRAYA);
            s.setPixel(0, 0, 0, 128);
            Surface out = dropAlpha(s, Fill.uniform(200));
            assertThat(out.get(0, 0)).isGreaterThan(98);
            assertThat(out.get(0, 0)).isLessThan(102);
        }

        @Test
        void takesAPerChannelBackground() {
            Surface s = Surface.create(1, 1, Layout.RGBA);
            s.setPixel(0, 0, 0, 0, 0, 0);
            assertThat(dropAlpha(s, Fill.perChannel(1, 2, 3)).getPixel(0, 0)).containsExactly(1, 2, 3);
        }

        @Test
        void refusesABackgroundOfTheWrongLength() {
            Surface s = Surface.create(1, 1, Layout.RGBA);
            assertThatThrownBy(() -> dropAlpha(s, Fill.perChannel(1, 2)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wrong number of channels");
        }
    }

    @Nested
    class ToLayout {
        @Test
        void walksColourAndCoverageInEitherDirection() {
            Surface rgb = Surface.create(1, 1, Layout.RGB, 8, 60);
            assertThat(toLayout(rgb, Layout.GRAY).layout()).isEqualTo(Layout.GRAY);
            assertThat(toLayout(rgb, Layout.RGBA).layout()).isEqualTo(Layout.RGBA);
            assertThat(toLayout(rgb, Layout.GRAYA).layout()).isEqualTo(Layout.GRAYA);

            Surface graya = Surface.create(1, 1, Layout.GRAYA, 8, 60);
            assertThat(toLayout(graya, Layout.RGB).layout()).isEqualTo(Layout.RGB);
            assertThat(toLayout(graya, Layout.GRAY).layout()).isEqualTo(Layout.GRAY);
        }

        @Test
        void clonesWhenNothingHasToChange() {
            Surface s = Surface.create(1, 1, Layout.RGB, 8, 3);
            Surface out = toLayout(s, Layout.RGB);
            assertThat(out).isNotSameAs(s);
            assertThat(out.get(0, 0)).isEqualTo(3);
        }

        @Test
        void carriesTheBackgroundThroughToTheDrop() {
            Surface s = Surface.create(1, 1, Layout.RGBA);
            s.setPixel(0, 0, 0, 0, 0, 0);
            assertThat(toLayout(s, Layout.RGB, Fill.uniform(255)).getPixel(0, 0))
                    .containsExactly(255, 255, 255);
        }

        @Test
        void refusesALayoutItDoesNotKnow() {
            Surface s = Surface.create(1, 1, Layout.RGB);
            // A layout is an enum, so an unknown one can only arrive by name.
            assertThatThrownBy(() -> toLayout(s, Layout.parse("cmyk")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("unknown layout");
        }
    }

    @Nested
    class ByteSize {
        @Test
        void multipliesOutTheGeometry() {
            assertThat(byteSize(100, 100, Layout.RGB, 8)).isEqualTo(30000);
            assertThat(byteSize(100, 100, Layout.RGBA, 16)).isEqualTo(80000);
        }
    }
}
