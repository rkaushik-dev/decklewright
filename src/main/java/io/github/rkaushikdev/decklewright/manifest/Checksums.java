package io.github.rkaushikdev.decklewright.manifest;

import io.github.rkaushikdev.decklewright.util.Whitespace;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fixity.
 *
 * <p>A repository keeps a digest of every file it holds so that in fifteen years somebody can prove
 * the file has not rotted. What matters is that the digest is written down beside the file, that
 * the same bytes always give the same digest, and that comparing two of them is a comparison of
 * strings and not of pictures.
 *
 * <p>Two algorithms are supported and both are written; see {@link DigestAlgorithm}. Digests are
 * lowercase hex.
 */
public final class Checksums {

    /** The algorithms a manifest may name, best first. */
    public static final List<DigestAlgorithm> ALGORITHMS = List.of(DigestAlgorithm.values());

    /** What is written when the profile does not say. */
    public static final DigestAlgorithm DEFAULT_ALGORITHM = DigestAlgorithm.SHA256;

    /**
     * One line of a sum file: a digest of 32 to 128 hex digits, a space, a space or a star, and a
     * name running to the end of the line. A name may hold any character but a line break, which
     * here means a line feed, a carriage return, U+2028 or U+2029.
     */
    private static final Pattern SUM_LINE = Pattern.compile("([0-9a-fA-F]{32,128}) [ *]([^\\n\\r\\u2028\\u2029]+)");

    private static final Pattern LINE_BREAK = Pattern.compile("\\r?\\n");

    private Checksums() {}

    /** The sha256 digest of some bytes, lowercase hex. */
    public static String digest(byte[] bytes) {
        return digest(bytes, DEFAULT_ALGORITHM);
    }

    /** The digest of some bytes under the given algorithm, lowercase hex. */
    public static String digest(byte[] bytes, DigestAlgorithm algorithm) {
        return HexFormat.of().formatHex(algorithm.newDigest().digest(bytes));
    }

    /** Every digest a manifest carries for one file, sha256 first. */
    public static Map<String, String> digests(byte[] bytes) {
        return digests(bytes, ALGORITHMS);
    }

    /**
     * The digests of one file under the given algorithms.
     *
     * @return each digest under its algorithm's label, in the order asked for
     */
    public static Map<String, String> digests(byte[] bytes, List<DigestAlgorithm> algorithms) {
        Map<String, String> out = new LinkedHashMap<>();
        for (DigestAlgorithm algorithm : algorithms) {
            out.put(algorithm.label(), digest(bytes, algorithm));
        }
        return Collections.unmodifiableMap(out);
    }

    /** A fixity record carrying every digest a manifest carries. */
    public static Fixity fixityOf(byte[] bytes) {
        return fixityOf(bytes, ALGORITHMS);
    }

    /** A fixity record carrying the digests under the given algorithms. */
    public static Fixity fixityOf(byte[] bytes, List<DigestAlgorithm> algorithms) {
        return new Fixity(bytes.length, digests(bytes, algorithms));
    }

    /**
     * Whether some bytes still match a fixity record.
     *
     * @see #verifyAgainst(byte[], Map)
     */
    public static FixityCheck verifyAgainst(byte[] bytes, Fixity record) {
        return verifyAgainst(bytes, record.toJson());
    }

    /**
     * Whether some bytes still match a fixity record written as plain data, such as a derivative's
     * entry in a sidecar: an optional {@code bytes} size and an optional {@code digests} object.
     *
     * <p>A record with no algorithm in common with what can be computed is not a pass. It is the case
     * fixity exists to catch, so it comes back as its own outcome rather than as a quiet true. A size
     * that disagrees fails the check before anything is hashed. Otherwise every digest the record
     * holds under a known label is compared, exactly as written.
     */
    public static FixityCheck verifyAgainst(byte[] bytes, Map<String, ?> record) {
        Object held = record.get("digests");
        Map<?, ?> digests = held instanceof Map<?, ?> map ? map : Map.of();
        List<String> usable = new ArrayList<>();
        for (Object name : digests.keySet()) {
            if (name instanceof String label && DigestAlgorithm.named(label).isPresent()) {
                usable.add(label);
            }
        }

        if (usable.isEmpty()) {
            return new FixityCheck(
                    false, List.of(), List.of(), Optional.of("the record carries no digest we can compute"));
        }
        if (record.containsKey("bytes") && !sameSize(record.get("bytes"), bytes.length)) {
            String reason =
                    "the file is " + bytes.length + " bytes and the record says " + Documents.text(record.get("bytes"));
            return new FixityCheck(false, List.of(), List.of(), Optional.of(reason));
        }

        List<String> failed = new ArrayList<>();
        for (String name : usable) {
            if (!digest(bytes, DigestAlgorithm.parse(name)).equals(digests.get(name))) {
                failed.add(name);
            }
        }
        Optional<String> reason =
                failed.isEmpty() ? Optional.empty() : Optional.of(String.join(" and ", failed) + " do not match");
        return new FixityCheck(failed.isEmpty(), usable, failed, reason);
    }

    /**
     * Compare two digests without letting the comparison depend on how they were written down. Half
     * the manifests in the world are uppercase.
     *
     * @return true when both are strings that agree once blank space at either end is ignored
     *     ({@link Whitespace#trim(String)}) and case is folded; false for anything that is not two
     *     strings, null included
     */
    public static boolean sameDigest(Object left, Object right) {
        return left instanceof String one
                && right instanceof String other
                && folded(one).equals(folded(other));
    }

    /** One sha256 digest over a set of files, so a folder has one number. */
    public static String digestOfSet(List<PayloadEntry> files) {
        return digestOfSet(files, DEFAULT_ALGORITHM);
    }

    /**
     * One digest over a set of files.
     *
     * <p>The names go into the hash along with the bytes, and they go in sorted, so two deliveries
     * with the same files under different names do not agree and the same delivery listed in a
     * different order does. Each file contributes its name, a space, its own digest and a line feed,
     * as UTF-8.
     */
    public static String digestOfSet(List<PayloadEntry> files, DigestAlgorithm algorithm) {
        MessageDigest hash = algorithm.newDigest();
        List<PayloadEntry> sorted = new ArrayList<>(files);
        // UTF-16 code unit order, which is the order the names have always been hashed in.
        sorted.sort(Comparator.comparing(PayloadEntry::name));
        for (PayloadEntry file : sorted) {
            hash.update(utf8(file.name()));
            hash.update(utf8(" "));
            hash.update(utf8(digest(file.bytes(), algorithm)));
            hash.update(utf8("\n"));
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    /** The sha256 sum file for some entries. */
    public static String toSumFile(List<SumEntry> entries) {
        return toSumFile(entries, DEFAULT_ALGORITHM);
    }

    /**
     * The line format the tape robot reads, which is the same one {@code sha256sum -c} reads: digest,
     * two spaces, name. Lines are joined by line feeds, with none after the last, and an entry with
     * no digest under the algorithm is left out.
     */
    public static String toSumFile(List<SumEntry> entries, DigestAlgorithm algorithm) {
        return entries.stream()
                .filter(entry -> Documents.given(entry.digests().get(algorithm.label())))
                .map(entry -> entry.digests().get(algorithm.label()) + "  " + entry.name())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Read a sum file back. Lines end at a line feed with or without a carriage return before it, and
     * both the text form ({@code digest  name}) and the binary star form ({@code digest *name}) are
     * read. A line nobody can parse is skipped rather than guessed at.
     *
     * @return one line per readable line, in order, each digest lowercased
     */
    public static List<SumLine> fromSumFile(String text) {
        List<SumLine> out = new ArrayList<>();
        for (String line : LINE_BREAK.split(text, -1)) {
            Matcher match = SUM_LINE.matcher(line);
            if (match.matches()) {
                out.add(new SumLine(match.group(1).toLowerCase(Locale.ROOT), match.group(2)));
            }
        }
        return List.copyOf(out);
    }

    private static boolean sameSize(Object said, int length) {
        return said instanceof Number number && number.doubleValue() == length;
    }

    private static String folded(String digest) {
        return Whitespace.trim(digest).toLowerCase(Locale.ROOT);
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
