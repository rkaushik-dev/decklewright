package io.github.rkaushikdev.decklewright.codec;

import static io.github.rkaushikdev.decklewright.codec.Bytes.asciiBytes;
import static io.github.rkaushikdev.decklewright.codec.Bytes.concatBytes;
import static io.github.rkaushikdev.decklewright.codec.Bytes.crc32;
import static io.github.rkaushikdev.decklewright.codec.Bytes.latin1Bytes;
import static io.github.rkaushikdev.decklewright.codec.Bytes.readAscii;
import static io.github.rkaushikdev.decklewright.codec.Bytes.readAsciiZ;
import static io.github.rkaushikdev.decklewright.codec.Bytes.readUint16BE;
import static io.github.rkaushikdev.decklewright.codec.Bytes.readUint32BE;
import static io.github.rkaushikdev.decklewright.codec.Bytes.uint32BE;
import static io.github.rkaushikdev.decklewright.codec.Bytes.writeUint32BE;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.ascii;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.u8;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.unsigned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.CodecError;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BytesTest {

    @Nested
    class Crc32 {
        @Test
        void matchesThePublishedCheckValues() {
            assertThat(crc32(ascii("123456789"))).isEqualTo(0xcbf43926L);
            assertThat(crc32(new byte[0])).isEqualTo(0);
            assertThat(crc32(ascii("a"))).isEqualTo(0xe8b7be43L);
        }

        @Test
        void canBeContinuedFromASeed() {
            long whole = crc32(ascii("abcd"));
            long part = crc32(ascii("ab"));
            assertThat(crc32(ascii("cd"), part)).isEqualTo(whole);
        }

        @Test
        void worksOnASubarrayOfALargerBuffer() {
            byte[] whole = ascii("xx123456789xx");
            assertThat(crc32(whole, 2, 9)).isEqualTo(0xcbf43926L);
        }

        @Test
        void neverComesBackNegative() {
            assertThat(crc32(ascii(String.valueOf((char) 0xff).repeat(4)))).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    class TheBigEndianReaders {
        private final byte[] bytes = u8(0x12, 0x34, 0x56, 0x78, 0xff, 0xff, 0xff, 0xff);

        @Test
        void readsTwoAndFourByteWords() {
            assertThat(readUint16BE(bytes, 0)).isEqualTo(0x1234);
            assertThat(readUint32BE(bytes, 0)).isEqualTo(0x12345678L);
        }

        @Test
        void readsTheTopOfTheRangeWithoutGoingNegative() {
            assertThat(readUint32BE(bytes, 4)).isEqualTo(4294967295L);
        }
    }

    @Nested
    class TheBigEndianWriters {
        @Test
        void writesIntoAnExistingBuffer() {
            byte[] out = new byte[6];
            writeUint32BE(out, 1, 0x12345678L);
            assertThat(unsigned(out)).containsExactly(0, 0x12, 0x34, 0x56, 0x78, 0);
        }

        @Test
        void buildsAFreshFourByteWord() {
            assertThat(unsigned(uint32BE(1))).containsExactly(0, 0, 0, 1);
            assertThat(unsigned(uint32BE(4294967295L))).containsExactly(255, 255, 255, 255);
        }

        @Test
        void roundTripsThroughTheReader() {
            for (long value : new long[] {0, 1, 255, 65535, 16777215, 4294967295L}) {
                assertThat(readUint32BE(uint32BE(value), 0)).isEqualTo(value);
            }
        }
    }

    @Nested
    class TheTextHelpers {
        @Test
        void readsAFixedRunOfCharacters() {
            assertThat(readAscii(ascii("IHDRxx"), 0, 4)).isEqualTo("IHDR");
            assertThat(readAscii(ascii("xxIEND"), 2, 4)).isEqualTo("IEND");
        }

        @Test
        void stopsAZeroTerminatedRunAtTheTerminator() {
            byte[] bytes = u8(65, 66, 0, 67);
            assertThat(readAsciiZ(bytes, 0, 4)).isEqualTo("AB");
            assertThat(readAsciiZ(bytes, 3, 1)).isEqualTo("C");
        }

        @Test
        void turnsTextIntoBytesAndBack() {
            assertThat(readAscii(asciiBytes("plate 14"), 0, 8)).isEqualTo("plate 14");
        }

        @Test
        void acceptsEveryLatinOneCodePoint() {
            assertThat(unsigned(latin1Bytes(String.valueOf((char) 0xff)))).containsExactly(255);
        }

        @Test
        void refusesAnythingWiderAndNamesWhatItWasWriting() {
            String dashed = "plate " + (char) 0x2014 + " 14";
            assertThatThrownBy(() -> latin1Bytes(dashed, "a png text key"))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("a png text key holds latin one");
        }
    }

    @Nested
    class ConcatBytes {
        @Test
        void gluesBuffersInOrder() {
            byte[] out = concatBytes(List.of(ascii("ab"), ascii("cd"), ascii("e")));
            assertThat(readAscii(out, 0, 5)).isEqualTo("abcde");
        }

        @Test
        void copesWithAnEmptyListAndEmptyMembers() {
            assertThat(concatBytes(List.of())).hasSize(0);
            assertThat(concatBytes(List.of(new byte[0], ascii("x")))).hasSize(1);
        }

        @Test
        void copiesRatherThanAliasing() {
            byte[] first = ascii("ab");
            byte[] out = concatBytes(List.of(first));
            out[0] = 0;
            assertThat(first[0]).isEqualTo((byte) 97);
        }
    }
}
