package io.github.rkaushikdev.decklewright.manifest;

import static io.github.rkaushikdev.decklewright.manifest.Checksums.ALGORITHMS;
import static io.github.rkaushikdev.decklewright.manifest.Checksums.DEFAULT_ALGORITHM;
import static io.github.rkaushikdev.decklewright.manifest.Checksums.digest;
import static io.github.rkaushikdev.decklewright.manifest.Checksums.digestOfSet;
import static io.github.rkaushikdev.decklewright.manifest.Checksums.digests;
import static io.github.rkaushikdev.decklewright.manifest.Checksums.fixityOf;
import static io.github.rkaushikdev.decklewright.manifest.Checksums.fromSumFile;
import static io.github.rkaushikdev.decklewright.manifest.Checksums.sameDigest;
import static io.github.rkaushikdev.decklewright.manifest.Checksums.toSumFile;
import static io.github.rkaushikdev.decklewright.manifest.Checksums.verifyAgainst;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Fixity: digests of bytes and of sets of files, the fixity record, and the sum file. */
class ChecksumTest {

    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Nested
    class Digest {
        @Test
        void agreesWithThePublishedAnswerForNothingAtAll() {
            assertThat(digest(new byte[0])).isEqualTo(EMPTY_SHA256);
        }

        @Test
        void agreesWithThePublishedAnswerForAbc() {
            assertThat(digest(bytes("abc")))
                    .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
            assertThat(digest(bytes("abc"), DigestAlgorithm.MD5)).isEqualTo("900150983cd24fb0d6963f7d28e17f72");
        }

        @Test
        void givesTheSameAnswerTwice() {
            assertThat(digest(bytes("plate 12"))).isEqualTo(digest(bytes("plate 12")));
        }

        @Test
        void givesADifferentAnswerForOneByteDifferent() {
            assertThat(digest(bytes("plate 12"))).isNotEqualTo(digest(bytes("plate 13")));
        }

        @Test
        void refusesAnAlgorithmNoManifestHereKeeps() {
            assertThatThrownBy(() -> digest(bytes("a"), DigestAlgorithm.parse("sha1")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no digest is kept");
        }

        @Test
        void defaultsToTheOneAnybodyWouldChooseNow() {
            assertThat(DEFAULT_ALGORITHM.label()).isEqualTo("sha256");
            assertThat(ALGORITHMS.get(0)).isEqualTo(DEFAULT_ALGORITHM);
        }
    }

    @Nested
    class Digests {
        @Test
        void writesEveryAlgorithmAManifestCarries() {
            assertThat(digests(bytes("a")).keySet()).containsExactly("sha256", "md5");
        }

        @Test
        void writesOnlyWhatItWasAskedFor() {
            assertThat(digests(bytes("a"), List.of(DigestAlgorithm.MD5)).keySet())
                    .containsExactly("md5");
        }
    }

    @Nested
    class FixityOf {
        @Test
        void keepsTheSizeBesideTheDigests() {
            Fixity record = fixityOf(bytes("plate 12"));
            assertThat(record.bytes()).isEqualTo(8);
            assertThat(record.digests().get("sha256")).hasSize(64);
        }
    }

    @Nested
    class VerifyAgainst {
        @Test
        void passesBytesThatHaveNotChanged() {
            FixityCheck out = verifyAgainst(bytes("abc"), fixityOf(bytes("abc")));
            assertThat(out.ok()).isTrue();
            assertThat(out.checked()).isEqualTo(List.of("sha256", "md5"));
        }

        @Test
        void failsBytesThatHave() {
            FixityCheck out = verifyAgainst(bytes("abd"), fixityOf(bytes("abc")));
            assertThat(out.ok()).isFalse();
            assertThat(out.failed()).isEqualTo(List.of("sha256", "md5"));
        }

        @Test
        void catchesAWrongSizeBeforeItHashesAnything() {
            FixityCheck out = verifyAgainst(bytes("abcd"), fixityOf(bytes("abc")));
            assertThat(out.ok()).isFalse();
            assertThat(out.checked()).isEqualTo(List.of());
            assertThat(out.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).containsPattern("4 bytes and the record says 3"));
        }

        @Test
        void doesNotPassARecordWithNoDigestItCanCompute() {
            FixityCheck out = verifyAgainst(bytes("abc"), Map.of("digests", Map.of("sha1", "whatever")));
            assertThat(out.ok()).isFalse();
            assertThat(out.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).containsPattern("no digest we can compute"));
        }

        @Test
        void doesNotPassARecordWithNoDigestsAtAll() {
            assertThat(verifyAgainst(bytes("abc"), Map.of()).ok()).isFalse();
        }

        @Test
        void checksWhatItCanWhenTheRecordCarriesOneOfTwo() {
            FixityCheck out = verifyAgainst(
                    bytes("abc"), Map.of("digests", Map.of("md5", digest(bytes("abc"), DigestAlgorithm.MD5))));
            assertThat(out.ok()).isTrue();
            assertThat(out.checked()).isEqualTo(List.of("md5"));
        }

        @Test
        void namesWhichAlgorithmDisagreed() {
            Fixity record = fixityOf(bytes("abc"));
            Map<String, String> changed = new LinkedHashMap<>(record.digests());
            changed.put("md5", "x".repeat(32));
            assertThat(verifyAgainst(bytes("abc"), new Fixity(record.bytes(), changed))
                            .reason())
                    .hasValue("md5 do not match");
        }
    }

    @Nested
    class SameDigest {
        @Test
        void ignoresCaseAndSurroundingSpace() {
            assertThat(sameDigest(" ABC123 ", "abc123")).isTrue();
        }

        @Test
        void isFalseForAnythingThatIsNotTwoStrings() {
            assertThat(sameDigest(null, "abc")).isFalse();
            assertThat(sameDigest("abc", null)).isFalse();
        }
    }

    @Nested
    class DigestOfSet {
        private final List<PayloadEntry> files =
                List.of(new PayloadEntry("a_master.png", bytes("one")), new PayloadEntry("a_thumb.png", bytes("two")));

        @Test
        void givesAFolderOneNumber() {
            assertThat(digestOfSet(files)).hasSize(64);
        }

        @Test
        void doesNotDependOnTheOrderTheFilesCameIn() {
            List<PayloadEntry> reversed = new ArrayList<>(files);
            Collections.reverse(reversed);
            assertThat(digestOfSet(reversed)).isEqualTo(digestOfSet(files));
        }

        @Test
        void changesWhenAFileChanges() {
            List<PayloadEntry> other = List.of(files.get(0), new PayloadEntry("a_thumb.png", bytes("three")));
            assertThat(digestOfSet(other)).isNotEqualTo(digestOfSet(files));
        }

        @Test
        void changesWhenAFileIsRenamed() {
            List<PayloadEntry> other = List.of(new PayloadEntry("a_service.png", bytes("one")), files.get(1));
            assertThat(digestOfSet(other)).isNotEqualTo(digestOfSet(files));
        }

        @Test
        void changesWhenAFileIsDropped() {
            assertThat(digestOfSet(List.of(files.get(0)))).isNotEqualTo(digestOfSet(files));
        }
    }

    @Nested
    class ToSumFileAndFromSumFile {
        private final List<SumEntry> entries = List.of(
                new SumEntry("a_master.png", digests(bytes("one"))),
                new SumEntry("a_thumb.png", digests(bytes("two"))));

        @Test
        void writesTheFormatSha256sumReads() {
            String text = toSumFile(entries);
            assertThat(text.split("\n", -1)[0]).isEqualTo(digest(bytes("one")) + "  a_master.png");
        }

        @Test
        void writesTheAlgorithmItWasAskedFor() {
            assertThat(toSumFile(entries, DigestAlgorithm.MD5)
                            .split("\n", -1)[0]
                            .split("  ", -1)[0])
                    .hasSize(32);
        }

        @Test
        void leavesOutAnEntryThatHasNoDigestUnderThatAlgorithm() {
            List<SumEntry> thin = List.of(new SumEntry("a.png", Map.of("md5", "x".repeat(32))));
            assertThat(toSumFile(thin, DigestAlgorithm.SHA256)).isEqualTo("");
        }

        @Test
        void readsBackWhatItWrote() {
            List<SumLine> read = fromSumFile(toSumFile(entries));
            assertThat(read).extracting(SumLine::name).containsExactly("a_master.png", "a_thumb.png");
            assertThat(read.get(0).digest()).isEqualTo(digest(bytes("one")));
        }

        @Test
        void readsTheBinaryStarFormOtherToolsWrite() {
            List<SumLine> read = fromSumFile(digest(bytes("one")) + " *a_master.png");
            assertThat(read.get(0).name()).isEqualTo("a_master.png");
        }

        @Test
        void skipsALineNobodyCanParseRatherThanGuessing() {
            assertThat(fromSumFile("this is not a checksum line\n\n")).isEqualTo(List.of());
        }

        @Test
        void lowercasesWhatItReadsBecauseHalfTheWorldWritesUppercase() {
            List<SumLine> read = fromSumFile(digest(bytes("one")).toUpperCase(Locale.ROOT) + "  a.png");
            assertThat(read.get(0).digest()).isEqualTo(digest(bytes("one")));
        }
    }
}
