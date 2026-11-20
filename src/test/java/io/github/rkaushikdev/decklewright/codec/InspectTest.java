package io.github.rkaushikdev.decklewright.codec;

import static io.github.rkaushikdev.decklewright.codec.Inspections.classify;
import static io.github.rkaushikdev.decklewright.codec.Inspections.describe;
import static io.github.rkaushikdev.decklewright.codec.Inspections.inspect;
import static io.github.rkaushikdev.decklewright.codec.Inspections.inspectDeep;
import static io.github.rkaushikdev.decklewright.codec.Inspections.isReadable;
import static io.github.rkaushikdev.decklewright.codec.Inspections.triage;
import static io.github.rkaushikdev.decklewright.codec.Pnm.writePnm;
import static io.github.rkaushikdev.decklewright.codec.png.Encoding.encodePng;
import static io.github.rkaushikdev.decklewright.testing.MakeTiff.makeTiff;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.u8;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.MakeTiff;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InspectTest {

    private static Surface sample() {
        return Surface.create(6, 4, Layout.RGB, 8, 90);
    }

    @Nested
    class Inspect {
        @Test
        void reportsAGoodPng() {
            Inspection out = inspect(encodePng(sample()), "q17.png");
            assertThat(out.ok()).isTrue();
            assertThat(out.format()).contains(ImageFormat.PNG);
            assertThat(out.shape()).contains(new ImageShape(6, 4, Layout.RGB, 8));
            assertThat(out.problems()).isEqualTo(List.of());
        }

        @Test
        void reportsAGoodTiff() {
            byte[] tiff = makeTiff(MakeTiff.Options.of(4, 2, 1, 2, 3, 4, 5, 6, 7, 8));
            Inspection out = inspect(tiff, "q17.tif");
            assertThat(out.ok()).isTrue();
            assertThat(out.format()).contains(ImageFormat.TIFF);
            assertThat(out.shape().map(ImageShape::layout)).contains(Layout.GRAY);
        }

        @Test
        void notesAWrongExtensionWithoutFailingTheFile() {
            Inspection out = inspect(writePnm(sample()), "q17.png");
            assertThat(out.ok()).isTrue();
            assertThat(out.format()).contains(ImageFormat.PNM);
            assertThat(out.problems())
                    .isEqualTo(List.of(new Problem(ProblemKind.WRONG_EXTENSION, "named png but the bytes say pnm")));
        }

        @Test
        void saysNothingAboutTheExtensionWhenThereIsNoPath() {
            assertThat(inspect(writePnm(sample())).problems()).isEqualTo(List.of());
        }

        @Test
        void reportsAnEmptyFile() {
            Inspection out = inspect(new byte[0], "q17.png");
            assertThat(out.ok()).isFalse();
            assertThat(out.problems().get(0).kind()).isEqualTo(ProblemKind.EMPTY);
        }

        @Test
        void reportsBytesNothingRecognises() {
            Inspection out = inspect(u8(1, 2, 3, 4, 5, 6, 7, 8, 9));
            assertThat(out.ok()).isFalse();
            assertThat(out.format()).isEmpty();
            assertThat(out.problems().get(0).kind()).isEqualTo(ProblemKind.UNREADABLE);
        }

        @Test
        void reportsATruncatedPngAsTruncated() {
            byte[] png = encodePng(sample());
            Inspection out = inspect(Arrays.copyOf(png, 20), "q17.png");
            assertThat(out.ok()).isFalse();
            assertThat(out.problems().get(0).kind()).isEqualTo(ProblemKind.TRUNCATED);
        }

        @Test
        void reportsAPalettePngAsUnsupported() {
            byte[] png = encodePng(sample());
            byte[] cut = png.clone();
            cut[8 + 8 + 9] = 3;
            Inspection out = inspect(cut);
            assertThat(out.problems().get(0).kind()).isEqualTo(ProblemKind.UNSUPPORTED);
        }
    }

    @Nested
    class InspectDeep {
        @Test
        void agreesWithTheShallowLookOnAGoodFile() {
            Inspection out = inspectDeep(encodePng(sample()), "q17.png");
            assertThat(out.ok()).isTrue();
        }

        @Test
        void catchesAStripThatDecodesShort() {
            byte[] tiff = makeTiff(MakeTiff.Options.of(4, 2, 1, 2, 3, 4, 5, 6, 7, 8));
            ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN).putInt(10 + 8 * 12 + 8, 3);
            assertThat(inspect(tiff).ok()).isTrue();
            Inspection deep = inspectDeep(tiff);
            assertThat(deep.ok()).isFalse();
            assertThat(deep.problems().get(0).kind()).isEqualTo(ProblemKind.TRUNCATED);
        }

        @Test
        void doesNotReadAFileTheShallowLookAlreadyRejected() {
            assertThat(inspectDeep(u8(9, 9)).ok()).isFalse();
        }
    }

    @Nested
    class Classify {
        @Test
        void separatesTheThreeKinds() {
            assertThat(classify(new UnsupportedError("no")).kind()).isEqualTo(ProblemKind.UNSUPPORTED);
            assertThat(classify(new Exception("the file stops early")).kind()).isEqualTo(ProblemKind.TRUNCATED);
            assertThat(classify(new Exception("something else entirely")).kind())
                    .isEqualTo(ProblemKind.UNREADABLE);
            assertThat(classify("a bare string").detail()).isEqualTo("a bare string");
        }
    }

    @Nested
    class Describe {
        @Test
        void writesOneLineForAGoodFile() {
            String out = describe(inspect(encodePng(sample()), "q17.png"));
            assertThat(out).isEqualTo("q17.png: png 6x4 rgb8");
        }

        @Test
        void mentionsANoteOnAnOtherwiseGoodFile() {
            String out = describe(inspect(writePnm(sample()), "q17.png"));
            assertThat(out).contains("named png but the bytes say pnm");
        }

        @Test
        void writesTheProblemForABadOne() {
            assertThat(describe(inspect(new byte[0], "q17.png"))).isEqualTo("q17.png: empty, the file holds no bytes");
        }

        @Test
        void copesWithNoPath() {
            assertThat(describe(inspect(u8(1, 2)))).contains("(no name)");
        }
    }

    @Nested
    class TriageBehaviour {
        @Test
        void splitsAFolderIntoWhatCanBeWorkedAndWhatCannot() {
            List<IntakeFile> files = List.of(
                    IntakeFile.of(encodePng(sample()), "a.png"),
                    IntakeFile.of(new byte[0], "b.png"),
                    IntakeFile.of(writePnm(sample()), "c.ppm"),
                    IntakeFile.of(u8(1, 2, 3), "d.png"));
            Triage result = triage(files);
            assertThat(result.good().stream().map(Inspection::path).toList())
                    .containsExactly(Optional.of("a.png"), Optional.of("c.ppm"));
            assertThat(result.bad().stream().map(Inspection::path).toList())
                    .containsExactly(Optional.of("b.png"), Optional.of("d.png"));
        }
    }

    @Nested
    class IsReadable {
        @Test
        void isAQuickYesOrNo() {
            assertThat(isReadable(encodePng(sample()))).isTrue();
            assertThat(isReadable(u8(1, 2, 3))).isFalse();
        }
    }
}
