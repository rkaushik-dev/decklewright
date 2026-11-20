package io.github.rkaushikdev.decklewright.raster;

import static io.github.rkaushikdev.decklewright.raster.Layouts.DEPTHS;
import static io.github.rkaushikdev.decklewright.raster.Layouts.LAYOUTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LayoutTest {

    @Nested
    class TheTables {
        @Test
        void listsTheFourLayoutsAndTwoDepths() {
            assertThat(LAYOUTS.stream().map(Layout::label)).containsExactly("gray", "graya", "rgb", "rgba");
            assertThat(DEPTHS).containsExactly(8, 16);
            assertThatThrownBy(() -> LAYOUTS.add(Layout.RGB)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> DEPTHS.add(12)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void acceptsEveryListedNameAndRejectsOthers() {
            for (Layout layout : LAYOUTS) {
                assertThat(Layout.parse(layout.label())).isSameAs(layout);
            }
            assertThatThrownBy(() -> Layout.parse("cmyk"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("unknown layout cmyk");
            assertThatThrownBy(() -> Layout.parse(""))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("layout must be a non empty string");
        }

        @Test
        void acceptsOnlyEightAndSixteenBits() {
            assertThat(Layouts.requireDepth(8)).isEqualTo(8);
            assertThat(Layouts.requireDepth(16)).isEqualTo(16);
            assertThatThrownBy(() -> Layouts.requireDepth(12))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("depth must be one of 8, 16");
        }
    }

    @Nested
    class ChannelArithmetic {
        @Test
        void countsChannels() {
            assertThat(Layout.GRAY.channelCount()).isEqualTo(1);
            assertThat(Layout.GRAYA.channelCount()).isEqualTo(2);
            assertThat(Layout.RGB.channelCount()).isEqualTo(3);
            assertThat(Layout.RGBA.channelCount()).isEqualTo(4);
        }

        @Test
        void separatesCoverageFromColour() {
            assertThat(Layout.RGBA.colourCount()).isEqualTo(3);
            assertThat(Layout.GRAYA.colourCount()).isEqualTo(1);
            assertThat(Layout.RGB.colourCount()).isEqualTo(3);
        }

        @Test
        void saysWhichLayoutsCarryAlphaAndWhereItSits() {
            assertThat(Layout.RGBA.hasAlpha()).isTrue();
            assertThat(Layout.RGB.hasAlpha()).isFalse();
            assertThat(Layout.RGBA.alphaIndex()).isEqualTo(3);
            assertThat(Layout.GRAYA.alphaIndex()).isEqualTo(1);
            assertThat(Layout.GRAY.alphaIndex()).isEqualTo(-1);
        }

        @Test
        void saysWhichLayoutsAreColour() {
            assertThat(Layout.RGB.isColour()).isTrue();
            assertThat(Layout.RGBA.isColour()).isTrue();
            assertThat(Layout.GRAY.isColour()).isFalse();
        }

        @Test
        void namesTheChannelsAndHandsBackACopy() {
            List<String> names = Layout.RGBA.channelNames();
            assertThat(names).containsExactly("red", "green", "blue", "alpha");
            // The list cannot be changed at all, so no caller can change it for the next one.
            assertThatThrownBy(() -> names.add("extra")).isInstanceOf(UnsupportedOperationException.class);
            assertThat(Layout.RGBA.channelNames()).hasSize(4);
        }
    }

    @Nested
    class AddingAndRemovingAlpha {
        @Test
        void promotesAndDemotes() {
            assertThat(Layout.GRAY.withAlpha()).isEqualTo(Layout.GRAYA);
            assertThat(Layout.RGB.withAlpha()).isEqualTo(Layout.RGBA);
            assertThat(Layout.GRAYA.withoutAlpha()).isEqualTo(Layout.GRAY);
            assertThat(Layout.RGBA.withoutAlpha()).isEqualTo(Layout.RGB);
        }

        @Test
        void isANoOpWhenTheLayoutIsAlreadyRight() {
            assertThat(Layout.RGBA.withAlpha()).isEqualTo(Layout.RGBA);
            assertThat(Layout.RGB.withoutAlpha()).isEqualTo(Layout.RGB);
        }

        @Test
        void roundTripsThroughBothDirections() {
            for (Layout layout : LAYOUTS) {
                assertThat(layout.withAlpha().withoutAlpha()).isEqualTo(layout.withoutAlpha());
            }
        }
    }

    @Nested
    class DepthHelpers {
        @Test
        void reportsTheTopOfTheRange() {
            assertThat(Layouts.maxValue(8)).isEqualTo(255);
            assertThat(Layouts.maxValue(16)).isEqualTo(65535);
        }

        @Test
        void picksTheMatchingTypedArray() {
            assertThat(Layouts.arrayTypeFor(8)).isEqualTo(Uint8Samples.class);
            assertThat(Layouts.arrayTypeFor(16)).isEqualTo(Uint16Samples.class);
        }

        @Test
        void sizesARowInBytes() {
            assertThat(Layouts.rowBytes(100, Layout.GRAY, 8)).isEqualTo(100);
            assertThat(Layouts.rowBytes(100, Layout.RGB, 8)).isEqualTo(300);
            assertThat(Layouts.rowBytes(100, Layout.RGB, 16)).isEqualTo(600);
            assertThat(Layouts.rowBytes(100, Layout.RGBA, 16)).isEqualTo(800);
        }
    }
}
