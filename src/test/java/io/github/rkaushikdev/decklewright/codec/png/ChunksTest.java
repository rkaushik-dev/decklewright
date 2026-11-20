package io.github.rkaushikdev.decklewright.codec.png;

import static io.github.rkaushikdev.decklewright.codec.png.Chunks.PNG_SIGNATURE;
import static io.github.rkaushikdev.decklewright.codec.png.Chunks.assemble;
import static io.github.rkaushikdev.decklewright.codec.png.Chunks.buildChunk;
import static io.github.rkaushikdev.decklewright.codec.png.Chunks.chunkFlags;
import static io.github.rkaushikdev.decklewright.codec.png.Chunks.chunksOfType;
import static io.github.rkaushikdev.decklewright.codec.png.Chunks.findChunk;
import static io.github.rkaushikdev.decklewright.codec.png.Chunks.looksLikePng;
import static io.github.rkaushikdev.decklewright.codec.png.Chunks.splitChunks;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.ascii;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.text;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.u8;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.unsigned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.CodecError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChunksTest {

    private static final byte[] HEADER = u8(0, 0, 0, 2, 0, 0, 0, 3, 8, 0, 0, 0, 0);

    private static byte[] tinyPng() {
        return assemble(List.of(
                Chunk.of("IHDR", HEADER),
                Chunk.of("tEXt", ascii("Title" + (char) 0 + "plate 14")),
                Chunk.of("IDAT", u8(1, 2, 3)),
                Chunk.of("IEND", new byte[0])));
    }

    @Nested
    class LooksLikePng {
        @Test
        void checksTheEightSignatureBytes() {
            int[] signature = PNG_SIGNATURE.stream().mapToInt(Integer::intValue).toArray();
            assertThat(looksLikePng(u8(signature))).isTrue();
            assertThat(looksLikePng(u8(137, 80, 78, 71, 13, 10, 26, 9))).isFalse();
            assertThat(looksLikePng(u8(137, 80))).isFalse();
        }
    }

    @Nested
    class BuildChunk {
        @Test
        void laysOutLengthTypeDataAndCheck() {
            byte[] chunk = buildChunk("IEND", new byte[0]);
            assertThat(chunk).hasSize(12);
            assertThat(unsigned(Arrays.copyOfRange(chunk, 0, 4))).containsExactly(0, 0, 0, 0);
            assertThat(text(Arrays.copyOfRange(chunk, 4, 8))).isEqualTo("IEND");
            assertThat(unsigned(Arrays.copyOfRange(chunk, 8, chunk.length))).containsExactly(0xae, 0x42, 0x60, 0x82);
        }

        @Test
        void refusesATypeThatIsNotFourCharacters() {
            assertThatThrownBy(() -> buildChunk("IEN", new byte[0]))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("four characters");
        }
    }

    @Nested
    class SplitChunks {
        @Test
        void walksAWholeFile() {
            List<Chunk> chunks = splitChunks(tinyPng());
            assertThat(chunks.stream().map(Chunk::type).toList()).containsExactly("IHDR", "tEXt", "IDAT", "IEND");
            assertThat(unsigned(chunks.get(2).data())).containsExactly(1, 2, 3);
        }

        @Test
        void recordsWhereEachChunkStarted() {
            List<Chunk> chunks = splitChunks(tinyPng());
            assertThat(chunks.get(0).offset()).hasValue(8);
            assertThat(chunks.get(1).offset()).hasValue(8 + 12 + HEADER.length);
        }

        @Test
        void refusesAFileThatIsNotAPng() {
            assertThatThrownBy(() -> splitChunks(ascii("not a png at all")))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("the signature is wrong");
        }

        @Test
        void refusesATruncatedHeader() {
            byte[] file = tinyPng();
            assertThatThrownBy(() -> splitChunks(Arrays.copyOf(file, 12)))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("stops inside a chunk header");
        }

        @Test
        void refusesATruncatedBody() {
            byte[] file = tinyPng();
            assertThatThrownBy(() -> splitChunks(Arrays.copyOf(file, 18)))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("stops inside a chunk body");
        }

        @Test
        void catchesACorruptedChunk() {
            byte[] file = tinyPng();
            file[20] ^= (byte) 0xff;
            assertThatThrownBy(() -> splitChunks(file))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("check value on the IHDR chunk");
        }

        @Test
        void canBeToldToSkipTheCheck() {
            byte[] file = tinyPng();
            file[20] ^= (byte) 0xff;
            assertThat(splitChunks(file, false)).hasSize(4);
        }

        @Test
        void insistsOnIhdrFirstAndIendLast() {
            byte[] wrongOrder = assemble(
                    List.of(Chunk.of("tEXt", ascii("a")), Chunk.of("IHDR", HEADER), Chunk.of("IEND", new byte[0])));
            assertThatThrownBy(() -> splitChunks(wrongOrder))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("open with an IHDR chunk");

            byte[] noEnd = assemble(List.of(Chunk.of("IHDR", HEADER), Chunk.of("IDAT", u8(1))));
            assertThatThrownBy(() -> splitChunks(noEnd))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("close with an IEND chunk");
        }

        @Test
        void stopsAtIendAndIgnoresAnythingAfterIt() {
            byte[] file = tinyPng();
            byte[] padded = Arrays.copyOf(file, file.length + 5);
            assertThat(splitChunks(padded)).hasSize(4);
        }
    }

    @Nested
    class Assemble {
        @Test
        void roundTripsThroughASplit() {
            List<Chunk> chunks = splitChunks(tinyPng());
            byte[] rebuilt = assemble(chunks.stream()
                    .map(chunk -> Chunk.of(chunk.type(), chunk.data()))
                    .toList());
            assertThat(rebuilt).containsExactly(tinyPng());
        }

        @Test
        void dropsAChunkWhenItIsLeftOut() {
            List<Chunk> chunks = splitChunks(tinyPng()).stream()
                    .filter(chunk -> !chunk.type().equals("tEXt"))
                    .toList();
            List<Chunk> stripped = splitChunks(assemble(chunks));
            assertThat(stripped.stream().map(Chunk::type).toList()).containsExactly("IHDR", "IDAT", "IEND");
        }
    }

    @Nested
    class Lookups {
        @Test
        void findsTheFirstOfATypeAndAllOfAType() {
            List<Chunk> chunks = splitChunks(tinyPng());
            assertThat(findChunk(chunks, "IDAT").map(Chunk::data))
                    .hasValueSatisfying(data -> assertThat(data).hasSize(3));
            assertThat(findChunk(chunks, "gAMA")).isEmpty();
            assertThat(chunksOfType(chunks, "IEND")).hasSize(1);
            assertThat(chunksOfType(chunks, "sRGB")).hasSize(0);
        }
    }

    @Nested
    class ChunkFlagsBehaviour {
        @Test
        void readsTheCaseBits() {
            assertThat(chunkFlags("IHDR")).isEqualTo(new ChunkFlags(true, true, false));
            assertThat(chunkFlags("tEXt")).isEqualTo(new ChunkFlags(false, true, true));
            assertThat(chunkFlags("prVt")).isEqualTo(new ChunkFlags(false, false, true));
        }
    }
}
