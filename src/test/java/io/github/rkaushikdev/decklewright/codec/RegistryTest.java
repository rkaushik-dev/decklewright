package io.github.rkaushikdev.decklewright.codec;

import static io.github.rkaushikdev.decklewright.codec.Codecs.CODEC_NAMES;
import static io.github.rkaushikdev.decklewright.codec.Codecs.codecForBytes;
import static io.github.rkaushikdev.decklewright.codec.Codecs.codecForPath;
import static io.github.rkaushikdev.decklewright.codec.Codecs.codecNamed;
import static io.github.rkaushikdev.decklewright.codec.Codecs.extensionFor;
import static io.github.rkaushikdev.decklewright.codec.Codecs.formatOf;
import static io.github.rkaushikdev.decklewright.codec.Codecs.probeImage;
import static io.github.rkaushikdev.decklewright.codec.Codecs.readImage;
import static io.github.rkaushikdev.decklewright.codec.Codecs.writeImage;
import static io.github.rkaushikdev.decklewright.codec.Pnm.writePnm;
import static io.github.rkaushikdev.decklewright.codec.png.Encoding.encodePng;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.u8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegistryTest {

    private static Surface sample() {
        Surface s = Surface.create(4, 3, Layout.RGB);
        for (int y = 0; y < 3; y += 1) {
            for (int x = 0; x < 4; x += 1) {
                s.setPixel(x, y, x * 20, y * 30, 100);
            }
        }
        return s;
    }

    @Nested
    class TheCodecList {
        @Test
        void namesTheThreeFormatsTheLabHandles() {
            assertThat(CODEC_NAMES).containsExactly("png", "tiff", "pnm");
        }

        @Test
        void findsACodecByNameCaseInsensitively() {
            assertThat(codecNamed("png").label()).isEqualTo("png");
            assertThat(codecNamed("PNG").label()).isEqualTo("png");
        }

        @Test
        void listsWhatItKnowsWhenTheNameIsWrong() {
            assertThatThrownBy(() -> codecNamed("jpeg")).isInstanceOfSatisfying(UnsupportedError.class, err -> {
                assertThat(err.getMessage()).isEqualTo("no codec called jpeg");
                assertThat(err.details().get("known")).isEqualTo(List.of("png", "tiff", "pnm"));
            });
        }
    }

    @Nested
    class CodecForPath {
        @Test
        void matchesOnTheExtension() {
            assertThat(codecForPath("/vault/q17.png").map(ImageFormat::label)).contains("png");
            assertThat(codecForPath("/vault/q17.PGM").map(ImageFormat::label)).contains("pnm");
            assertThat(codecForPath("/vault/q17.ppm").map(ImageFormat::label)).contains("pnm");
        }

        @Test
        void matchesTheTwoTiffExtensions() {
            assertThat(codecForPath("/vault/q17.tif").map(ImageFormat::label)).contains("tiff");
            assertThat(codecForPath("/vault/q17.tiff").map(ImageFormat::label)).contains("tiff");
        }

        @Test
        void givesNothingForAnUnknownOrMissingExtension() {
            assertThat(codecForPath("/vault/q17.jpg")).isEmpty();
            assertThat(codecForPath("/vault/q17")).isEmpty();
        }
    }

    @Nested
    class CodecForBytes {
        @Test
        void sniffsBothFormats() {
            assertThat(codecForBytes(encodePng(sample())).map(ImageFormat::label))
                    .contains("png");
            assertThat(codecForBytes(writePnm(sample())).map(ImageFormat::label))
                    .contains("pnm");
        }

        @Test
        void givesNothingForBytesItDoesNotKnow() {
            assertThat(codecForBytes(u8(0xff, 0xd8, 0xff, 0xe0))).isEmpty();
            assertThat(codecForBytes(new byte[0])).isEmpty();
        }

        @Test
        void reportsTheFormatName() {
            assertThat(formatOf(writePnm(sample())).map(ImageFormat::label)).contains("pnm");
            assertThat(formatOf(u8(1, 2, 3))).isEmpty();
        }
    }

    @Nested
    class ReadImage {
        @Test
        void readsEitherFormatFromItsBytes() {
            for (byte[] bytes : List.of(encodePng(sample()), writePnm(sample()))) {
                Surface surface = readImage(bytes);
                assertThat(surface.toJson()).isEqualTo(sample().toJson());
                assertThat(surface.data().toArray())
                        .containsExactly(sample().data().toArray());
            }
        }

        @Test
        void letsTheBytesWinOverTheName() {
            Surface surface = readImage(writePnm(sample()), "q17.png");
            assertThat(surface.width()).isEqualTo(4);
        }

        @Test
        void refusesBytesItDoesNotRecogniseAndQuotesTheHead() {
            assertThatThrownBy(() -> readImage(u8(1, 2, 3, 4, 5, 6, 7, 8, 9), "q17.jpg"))
                    .isInstanceOfSatisfying(CodecError.class, err -> {
                        assertThat(err.getMessage()).isEqualTo("nothing here recognises those bytes");
                        assertThat(err.details().get("path")).isEqualTo("q17.jpg");
                        assertThat(err.details().get("head")).isEqualTo(List.of(1, 2, 3, 4, 5, 6, 7, 8));
                    });
        }
    }

    @Nested
    class ProbeImage {
        @Test
        void reportsTheShapeWithoutDecoding() {
            assertThat(probeImage(encodePng(sample()))).isEqualTo(new ImageShape(4, 3, Layout.RGB, 8));
            assertThat(probeImage(writePnm(sample())).layout()).isEqualTo(Layout.RGB);
        }

        @Test
        void refusesBytesItDoesNotRecognise() {
            assertThatThrownBy(() -> probeImage(u8(9, 9)))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("recognises those bytes");
        }
    }

    @Nested
    class WriteImage {
        @Test
        void writesThroughTheNamedCodec() {
            assertThat(formatOf(writeImage(sample(), "png"))).contains(ImageFormat.PNG);
            assertThat(formatOf(writeImage(sample(), "pnm"))).contains(ImageFormat.PNM);
        }

        @Test
        void passesOptionsThroughToTheCodec() {
            byte[] ascii = writeImage(sample(), "pnm", WriteOptions.defaults().withAscii(true));
            assertThat("" + (char) ascii[0] + (char) ascii[1]).isEqualTo("P3");
        }

        @Test
        void roundTripsThroughTheFormatsThatCanBeWritten() {
            for (String format : List.of("png", "pnm")) {
                Surface back = readImage(writeImage(sample(), format));
                assertThat(back.data().toArray())
                        .containsExactly(sample().data().toArray());
            }
        }

        @Test
        void refusesToWriteAFormatThatIsReadOnly() {
            assertThatThrownBy(() -> writeImage(sample(), "tiff"))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("tiff is read only here");
        }

        @Test
        void refusesAFormatItDoesNotKnow() {
            assertThatThrownBy(() -> writeImage(sample(), "jpeg"))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("no codec called jpeg");
        }
    }

    @Nested
    class ExtensionFor {
        @Test
        void givesTheFirstExtensionOfTheCodec() {
            assertThat(extensionFor("png")).isEqualTo(".png");
            assertThat(extensionFor("pnm")).isEqualTo(".pnm");
        }
    }
}
