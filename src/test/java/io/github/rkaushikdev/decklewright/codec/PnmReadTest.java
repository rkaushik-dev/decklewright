package io.github.rkaushikdev.decklewright.codec;

import static io.github.rkaushikdev.decklewright.codec.Pnm.looksLikePnm;
import static io.github.rkaushikdev.decklewright.codec.Pnm.probePnm;
import static io.github.rkaushikdev.decklewright.codec.Pnm.readPnm;
import static io.github.rkaushikdev.decklewright.codec.Pnm.readPnmHeader;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.ascii;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.u8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PnmReadTest {

    /** The text as one byte a character, then the tail bytes. */
    private static byte[] bytes(String text, int... tail) {
        byte[] head = ascii(text);
        byte[] out = new byte[head.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(u8(tail), 0, out, head.length, tail.length);
        return out;
    }

    @Nested
    class ReadPnmHeader {
        @Test
        void readsTheFiveTokens() {
            PnmHeader header = readPnmHeader(bytes("P5 4 3 255 "));
            assertThat(header.magic()).isEqualTo("P5");
            assertThat(header.width()).isEqualTo(4);
            assertThat(header.height()).isEqualTo(3);
            assertThat(header.max()).isEqualTo(255);
        }

        @Test
        void skipsCommentsWhereverTheySit() {
            PnmHeader header = readPnmHeader(bytes("P6\n# from the copy stand\n4\n# even here\n3\n255\n"));
            assertThat(header.width()).isEqualTo(4);
            assertThat(header.height()).isEqualTo(3);
        }

        @Test
        void toleratesAnyRunOfWhitespace() {
            assertThat(readPnmHeader(bytes("P5\t\r\n  4\n\n3   255\n")).width()).isEqualTo(4);
        }

        @Test
        void refusesAMagicItDoesNotKnow() {
            assertThatThrownBy(() -> readPnmHeader(bytes("P7 4 3 255 ")))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("not a netpbm file, magic was P7");
            assertThatThrownBy(() -> readPnmHeader(bytes("JFIF")))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("not a netpbm file");
        }

        @Test
        void refusesAHeaderThatStopsEarly() {
            assertThatThrownBy(() -> readPnmHeader(bytes("P5 4 3")))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("the header stops early");
        }

        @Test
        void refusesADimensionThatIsNotAPositiveWholeNumber() {
            assertThatThrownBy(() -> readPnmHeader(bytes("P5 0 3 255 ")))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("width in the header");
            assertThatThrownBy(() -> readPnmHeader(bytes("P5 4 x 255 ")))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("height in the header");
        }

        @Test
        void refusesAMaximumAboveSixteenBits() {
            assertThatThrownBy(() -> readPnmHeader(bytes("P5 4 3 70000 ")))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("above 65535");
        }
    }

    @Nested
    class ReadPnmOnBinaryFiles {
        @Test
        void readsAnEightBitGrayscaleFile() {
            Surface s = readPnm(bytes("P5 2 2 255\n", 0, 64, 128, 255));
            assertThat(s.toJson()).isEqualTo(Map.of("width", 2.0, "height", 2.0, "layout", "gray", "depth", 8.0));
            assertThat(s.data().toArray()).containsExactly(0, 64, 128, 255);
        }

        @Test
        void readsAnEightBitColourFile() {
            Surface s = readPnm(bytes("P6 2 1 255\n", 1, 2, 3, 4, 5, 6));
            assertThat(s.layout()).isEqualTo(Layout.RGB);
            assertThat(s.getPixel(0, 0)).containsExactly(1, 2, 3);
            assertThat(s.getPixel(1, 0)).containsExactly(4, 5, 6);
        }

        @Test
        void readsASixteenBitFileBigEndian() {
            Surface s = readPnm(bytes("P5 2 1 65535\n", 0x00, 0x01, 0xff, 0xff));
            assertThat(s.depth()).isEqualTo(16);
            assertThat(s.data().toArray()).containsExactly(1, 65535);
        }

        @Test
        void refusesAFileThatStopsBeforeTheSamplesDo() {
            assertThatThrownBy(() -> readPnm(bytes("P5 4 4 255\n", 1, 2, 3)))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("stops before the samples");
        }

        @Test
        void ignoresTrailingBytesAfterTheSamples() {
            Surface s = readPnm(bytes("P5 1 1 255\n", 7, 99, 99));
            assertThat(s.data().toArray()).containsExactly(7);
        }
    }

    @Nested
    class ReadPnmOnAsciiFiles {
        @Test
        void readsAGrayscaleFile() {
            Surface s = readPnm(bytes("P2 2 2 255\n0 64\n128 255\n"));
            assertThat(s.data().toArray()).containsExactly(0, 64, 128, 255);
        }

        @Test
        void readsAColourFile() {
            Surface s = readPnm(bytes("P3 2 1 255\n1 2 3 4 5 6\n"));
            assertThat(s.getPixel(1, 0)).containsExactly(4, 5, 6);
        }

        @Test
        void skipsCommentsAmongTheSamples() {
            Surface s = readPnm(bytes("P2 2 1 255\n10 # the second one is dark\n20\n"));
            assertThat(s.data().toArray()).containsExactly(10, 20);
        }

        @Test
        void refusesASampleThatIsNotANumber() {
            assertThatThrownBy(() -> readPnm(bytes("P2 2 1 255\n10 fish\n")))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("not a whole number");
        }

        @Test
        void refusesAFileWithTooFewSamples() {
            assertThatThrownBy(() -> readPnm(bytes("P2 4 4 255\n1 2 3\n")))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("the header stops early");
        }
    }

    @Nested
    class ProbePnm {
        @Test
        void reportsTheShapeWithoutReadingSamples() {
            assertThat(probePnm(bytes("P6 800 600 65535\n"))).isEqualTo(new ImageShape(800, 600, Layout.RGB, 16));
        }
    }

    @Nested
    class LooksLikePnm {
        @Test
        void acceptsTheFourMagicsAndNothingElse() {
            for (String magic : new String[] {"P2", "P3", "P5", "P6"}) {
                assertThat(looksLikePnm(bytes(magic + " 1 1 1 "))).isTrue();
            }
            assertThat(looksLikePnm(bytes("P1 1 1 "))).isFalse();
            assertThat(looksLikePnm(bytes("PNG"))).isFalse();
            assertThat(looksLikePnm(u8(0x50))).isFalse();
            assertThat(looksLikePnm(new byte[0])).isFalse();
        }
    }
}
