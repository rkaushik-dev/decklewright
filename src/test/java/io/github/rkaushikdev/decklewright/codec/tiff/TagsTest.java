package io.github.rkaushikdev.decklewright.codec.tiff;

import static io.github.rkaushikdev.decklewright.codec.tiff.Tags.asBag;
import static io.github.rkaushikdev.decklewright.codec.tiff.Tags.list;
import static io.github.rkaushikdev.decklewright.codec.tiff.Tags.looksLikeTiff;
import static io.github.rkaushikdev.decklewright.codec.tiff.Tags.readIfd;
import static io.github.rkaushikdev.decklewright.codec.tiff.Tags.readTiffHeader;
import static io.github.rkaushikdev.decklewright.codec.tiff.Tags.scalar;
import static io.github.rkaushikdev.decklewright.codec.tiff.Tags.tagName;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.u8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.CodecError;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TagsTest {

    /** One entry to lay out: its value is a {@code List<Integer>} of numbers or a {@code String}. */
    private record Spec(int tag, int type, int count, Object value) {}

    private static byte[] makeTiff(List<Spec> entries) {
        return makeTiff(true, entries, 64);
    }

    private static byte[] makeTiff(boolean little, List<Spec> entries) {
        return makeTiff(little, entries, 64);
    }

    /**
     * Build a tiny tiff header and one directory so the reader is measured against the layout rather
     * than against a fixture nobody can check.
     *
     * @param trailing extra bytes for out of line values
     */
    private static byte[] makeTiff(boolean little, List<Spec> entries, int trailing) {
        byte[] bytes = new byte[8 + 2 + entries.size() * 12 + 4 + trailing];
        ByteBuffer view = ByteBuffer.wrap(bytes).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        bytes[0] = (byte) (little ? 0x49 : 0x4d);
        bytes[1] = (byte) (little ? 0x49 : 0x4d);
        view.putShort(2, (short) 42);
        view.putInt(4, 8);
        view.putShort(8, (short) entries.size());

        int heap = 8 + 2 + entries.size() * 12 + 4;
        for (int i = 0; i < entries.size(); i += 1) {
            Spec entry = entries.get(i);
            int at = 10 + i * 12;
            view.putShort(at, (short) entry.tag());
            view.putShort(at + 2, (short) entry.type());
            view.putInt(at + 4, entry.count());

            int size =
                    switch (entry.type()) {
                        case 3 -> 2;
                        case 4 -> 4;
                        case 5 -> 8;
                        default -> 1;
                    };
            int total = size * entry.count();
            int target = total <= 4 ? at + 8 : heap;
            if (total > 4) {
                view.putInt(at + 8, heap);
                heap += total;
            }
            if (entry.value() instanceof String text) {
                for (int k = 0; k < entry.count(); k += 1) {
                    bytes[target + k] = (byte) (k < text.length() ? text.charAt(k) : 0);
                }
            } else if (entry.value() instanceof List<?> values) {
                for (int k = 0; k < values.size(); k += 1) {
                    int value = (Integer) values.get(k);
                    switch (entry.type()) {
                        case 3 -> view.putShort(target + k * 2, (short) value);
                        case 4 -> view.putInt(target + k * 4, value);
                        case 5 -> {
                            view.putInt(target + k * 8, value);
                            view.putInt(target + k * 8 + 4, 1);
                        }
                        default -> bytes[target + k] = (byte) value;
                    }
                }
            }
        }
        view.putInt(10 + entries.size() * 12, 0);
        return bytes;
    }

    @Nested
    class ReadTiffHeader {
        @Test
        void readsALittleEndianHeader() {
            assertThat(readTiffHeader(makeTiff(List.of()))).isEqualTo(new TiffHeader(true, 8));
        }

        @Test
        void readsABigEndianHeader() {
            assertThat(readTiffHeader(makeTiff(false, List.of())).littleEndian())
                    .isFalse();
        }

        @Test
        void refusesAWrongByteOrderMarkOrVersion() {
            assertThatThrownBy(() -> readTiffHeader(u8(1, 2, 3, 4, 5, 6, 7, 8)))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("byte order mark is wrong");
            byte[] wrongVersion = makeTiff(List.of());
            wrongVersion[2] = 43;
            assertThatThrownBy(() -> readTiffHeader(wrongVersion))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("version word is wrong");
        }

        @Test
        void refusesAFileThatIsTooShort() {
            assertThatThrownBy(() -> readTiffHeader(u8(0x49, 0x49)))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("too short to be a tiff");
        }
    }

    @Nested
    class LooksLikeTiff {
        @Test
        void acceptsBothByteOrdersAndRejectsEverythingElse() {
            assertThat(looksLikeTiff(makeTiff(List.of()))).isTrue();
            assertThat(looksLikeTiff(makeTiff(false, List.of()))).isTrue();
            assertThat(looksLikeTiff(u8(137, 80, 78, 71, 13, 10, 26, 10))).isFalse();
            assertThat(looksLikeTiff(u8(0x49, 0x49))).isFalse();
        }
    }

    @Nested
    class ReadIfd {
        private final List<Spec> entries = List.of(
                new Spec(256, 3, 1, List.of(640)),
                new Spec(257, 3, 1, List.of(480)),
                new Spec(258, 3, 3, List.of(8, 8, 8)),
                new Spec(259, 3, 1, List.of(Compression.NONE.code())),
                new Spec(262, 3, 1, List.of(Photometric.RGB.code())),
                new Spec(282, 5, 1, List.of(400)),
                new Spec(296, 3, 1, List.of(ResolutionUnit.INCH.code())),
                new Spec(305, 2, 8, "benchtop"),
                new Spec(61000, 3, 1, List.of(7)));

        private Object valueNamed(List<Entry> read, String name) {
            return read.stream()
                    .filter(entry -> entry.name().equals(name))
                    .findFirst()
                    .map(Entry::value)
                    .orElseThrow();
        }

        @Test
        void readsEveryEntryWithItsName() {
            byte[] tiff = makeTiff(entries);
            Directory directory = readIfd(tiff, 8, true);
            List<Entry> read = directory.entries();
            assertThat(read).hasSize(entries.size());
            assertThat(directory.next()).isEqualTo(0);
            assertThat(read.get(0).name()).isEqualTo("imageWidth");
            assertThat(read.get(0).value()).isEqualTo(List.of(640.0));
        }

        @Test
        void readsAValueThatLivesOutOfLine() {
            List<Entry> read = readIfd(makeTiff(entries), 8, true).entries();
            assertThat(valueNamed(read, "bitsPerSample")).isEqualTo(List.of(8.0, 8.0, 8.0));
            assertThat(valueNamed(read, "software")).isEqualTo("benchtop");
        }

        @Test
        void dividesARational() {
            List<Entry> read = readIfd(makeTiff(entries), 8, true).entries();
            assertThat(valueNamed(read, "xResolution")).isEqualTo(List.of(400.0));
        }

        @Test
        void keepsATagItDoesNotKnowUnderAMadeUpName() {
            List<Entry> read = readIfd(makeTiff(entries), 8, true).entries();
            assertThat(read.stream()
                            .filter(entry -> entry.tag() == 61000)
                            .findFirst()
                            .map(Entry::name))
                    .contains("tag61000");
        }

        @Test
        void readsTheSameValuesInBigEndian() {
            byte[] tiff = makeTiff(false, entries);
            List<Entry> read = readIfd(tiff, 8, false).entries();
            assertThat(read.get(0).value()).isEqualTo(List.of(640.0));
            assertThat(valueNamed(read, "software")).isEqualTo("benchtop");
        }

        @Test
        void refusesADirectoryOffsetPastTheEnd() {
            assertThatThrownBy(() -> readIfd(makeTiff(entries), 9000, true))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("points past the end");
        }

        @Test
        void refusesAnEntryThatPointsPastTheEnd() {
            byte[] tiff = makeTiff(entries);
            ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN).putInt(10 + 2 * 12 + 8, 9000);
            assertThatThrownBy(() -> readIfd(tiff, 8, true))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("points past the end");
        }

        @Test
        void refusesAFieldTypeItDoesNotKnow() {
            byte[] tiff = makeTiff(List.of(new Spec(256, 3, 1, List.of(1))));
            ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN).putShort(12, (short) 99);
            assertThatThrownBy(() -> readIfd(tiff, 8, true))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("unknown tiff field type 99");
        }
    }

    @Nested
    class TheLookupHelpers {
        private final List<Entry> entries = List.of(
                new Entry(256, "imageWidth", 3, 1, List.of(640)),
                new Entry(258, "bitsPerSample", 3, 3, List.of(8, 8, 8)),
                new Entry(305, "software", 2, 3, "abc"));

        @Test
        void foldsTheEntriesIntoABag() {
            assertThat(asBag(entries))
                    .isEqualTo(Map.of(
                            "imageWidth", List.of(640.0), "bitsPerSample", List.of(8.0, 8.0, 8.0), "software", "abc"));
        }

        @Test
        void readsAScalarWithAFallback() {
            assertThat(scalar(entries, "imageWidth")).isEqualTo(640);
            assertThat(scalar(entries, "orientation", 1)).isEqualTo(1);
            assertThatThrownBy(() -> scalar(entries, "orientation"))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("the orientation tag is missing");
            assertThat(scalar(entries, "software", 5)).isEqualTo(5);
        }

        @Test
        void readsAListAndGivesAnEmptyOneWhenTheTagIsAbsent() {
            assertThat(list(entries, "bitsPerSample")).isEqualTo(List.of(8.0, 8.0, 8.0));
            assertThat(list(entries, "stripOffsets")).isEqualTo(List.of());
            assertThat(list(entries, "software")).isEqualTo(List.of());
        }
    }

    @Nested
    class TheConstantTables {
        @Test
        void namesATagItKnowsAndOneItDoesNot() {
            assertThat(tagName(256)).isEqualTo("imageWidth");
            assertThat(tagName(61000)).isEqualTo("tag61000");
        }

        @Test
        void isFrozen() {
            // The original froze three objects of codes. They are enums here, which is frozen by
            // construction: every field of each is final, so nothing can be reassigned.
            for (Class<?> table : List.of(Compression.class, Photometric.class, ResolutionUnit.class)) {
                assertThat(table.isEnum()).as(table.getSimpleName()).isTrue();
                for (Field field : table.getDeclaredFields()) {
                    assertThat(Modifier.isFinal(field.getModifiers()))
                            .as(table.getSimpleName() + "." + field.getName())
                            .isTrue();
                }
            }
        }
    }
}
