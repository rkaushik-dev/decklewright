package io.github.rkaushikdev.decklewright.codec;

import static io.github.rkaushikdev.decklewright.codec.Pnm.readPnm;
import static io.github.rkaushikdev.decklewright.codec.Pnm.readPnmHeader;
import static io.github.rkaushikdev.decklewright.codec.Pnm.writePnm;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.text;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.unsigned;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.gray;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PnmWriteTest {

    @Nested
    class WritePnmInBinary {
        @Test
        void writesAGrayscaleHeaderAndTheSamples() {
            byte[] out = writePnm(gray(row(0, 255)));
            assertThat(text(Arrays.copyOf(out, 11))).isEqualTo("P5\n2 1\n255\n");
            assertThat(unsigned(Arrays.copyOfRange(out, 11, out.length))).containsExactly(0, 255);
        }

        @Test
        void writesAColourHeader() {
            Surface s = Surface.create(1, 1, Layout.RGB);
            s.setPixel(0, 0, 1, 2, 3);
            byte[] out = writePnm(s);
            assertThat(text(out).startsWith("P6\n1 1\n255\n")).isTrue();
        }

        @Test
        void writesSixteenBitSamplesBigEndian() {
            byte[] out = writePnm(gray(16, row(1, 65535)));
            byte[] body = Arrays.copyOfRange(out, out.length - 4, out.length);
            assertThat(unsigned(body)).containsExactly(0x00, 0x01, 0xff, 0xff);
            assertThat(text(out).startsWith("P5\n2 1\n65535\n")).isTrue();
        }

        @Test
        void putsACommentInTheHeader() {
            byte[] out = writePnm(gray(row(0)), PnmOptions.defaults().withComment("stand two, plate 14"));
            assertThat(text(out).startsWith("P5\n# stand two, plate 14\n1 1\n255\n"))
                    .isTrue();
            assertThat(readPnmHeader(out).width()).isEqualTo(1);
        }

        @Test
        void refusesACommentThatWouldBreakTheHeader() {
            assertThatThrownBy(
                            () -> writePnm(gray(row(0)), PnmOptions.defaults().withComment("two\nlines")))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("cannot span lines");
        }

        @Test
        void refusesALayoutNetpbmCannotCarry() {
            Surface s = Surface.create(1, 1, Layout.RGBA);
            assertThatThrownBy(() -> writePnm(s))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("gray or rgb and nothing else");
        }
    }

    @Nested
    class WritePnmInAscii {
        @Test
        void writesOneLinePerRow() {
            String out = text(
                    writePnm(gray(row(0, 1), row(2, 3)), PnmOptions.defaults().withAscii(true)));
            assertThat(out).isEqualTo("P2\n2 2\n255\n0 1\n2 3\n");
        }

        @Test
        void writesColourSamplesInOrder() {
            Surface s = Surface.create(2, 1, Layout.RGB);
            s.setPixel(0, 0, 1, 2, 3);
            s.setPixel(1, 0, 4, 5, 6);
            assertThat(text(writePnm(s, PnmOptions.defaults().withAscii(true))))
                    .isEqualTo("P3\n2 1\n255\n1 2 3 4 5 6\n");
        }
    }

    @Nested
    class RoundTrips {
        private void roundTrip(Surface original) {
            roundTrip(original, PnmOptions.defaults());
        }

        private void roundTrip(Surface original, PnmOptions options) {
            Surface back = readPnm(writePnm(original, options));
            assertThat(back.toJson()).isEqualTo(original.toJson());
            assertThat(back.data().toArray()).containsExactly(original.data().toArray());
        }

        @Test
        void survivesAGrayscaleFileBothWays() {
            Surface s = gray(row(0, 1, 254), row(255, 128, 7));
            roundTrip(s);
            roundTrip(s, PnmOptions.defaults().withAscii(true));
        }

        @Test
        void survivesAColourFileBothWays() {
            Surface s = Surface.create(3, 2, Layout.RGB);
            for (int y = 0; y < 2; y += 1) {
                for (int x = 0; x < 3; x += 1) {
                    s.setPixel(x, y, x * 10, y * 20, 255 - x);
                }
            }
            roundTrip(s);
            roundTrip(s, PnmOptions.defaults().withAscii(true));
        }

        @Test
        void survivesSixteenBits() {
            roundTrip(gray(16, row(0, 1, 32768, 65535)));
            roundTrip(gray(16, row(0, 1, 32768, 65535)), PnmOptions.defaults().withAscii(true));
        }

        @Test
        void survivesACommentInTheHeader() {
            roundTrip(gray(row(9, 9)), PnmOptions.defaults().withComment("plate 14"));
        }

        @Test
        void survivesALargeFlatCapture() {
            Surface s = Surface.create(64, 48, Layout.RGB, 8, 77);
            roundTrip(s);
        }
    }
}
