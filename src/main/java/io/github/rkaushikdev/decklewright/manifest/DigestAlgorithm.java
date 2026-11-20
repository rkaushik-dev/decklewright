package io.github.rkaushikdev.decklewright.manifest;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A digest a manifest may keep for a file, best first.
 *
 * <p>Which algorithm hardly matters for fixity; what matters is that the digest is written down
 * beside the file and that the same bytes always give the same digest. sha256 is what anybody
 * would choose now. md5 is here because the tape robot the state uses to move material offsite
 * reports md5 and nothing else, and a fixity check that has to be done by hand is a fixity check
 * that does not get done.
 *
 * <p>Each is written under its label, {@code sha256} or {@code md5}, which is the key it has in a
 * sidecar's digests.
 */
public enum DigestAlgorithm implements JsonValue {
    /** SHA-256 (FIPS 180-4), written when a profile does not say otherwise. */
    SHA256("sha256", "SHA-256"),
    /** MD5 (RFC 1321), for the tape robot. */
    MD5("md5", "MD5");

    private final String label;
    private final String standardName;

    DigestAlgorithm(String label, String standardName) {
        this.label = label;
        this.standardName = standardName;
    }

    /** The name a manifest keeps this digest under, such as {@code sha256}. */
    public String label() {
        return label;
    }

    /**
     * The algorithm a manifest names.
     *
     * @throws BadArgumentError when no digest is kept under that name; the details name it and list
     *     every known one
     */
    public static DigestAlgorithm parse(String name) {
        return named(name).orElseThrow(() -> {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("algorithm", name);
            details.put("known", known());
            return new BadArgumentError("no digest is kept under that name", details);
        });
    }

    /** The algorithm a manifest names, matched exactly; empty for any other name. */
    public static Optional<DigestAlgorithm> named(String name) {
        return Arrays.stream(values())
                .filter(algorithm -> algorithm.label.equals(name))
                .findFirst();
    }

    /** A fresh digest for this algorithm. */
    MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(standardName);
        } catch (NoSuchAlgorithmException cause) {
            // Every Java platform is required to provide both, so this cannot happen on one.
            throw new IllegalStateException("this platform provides no " + standardName + " digest", cause);
        }
    }

    private static String known() {
        return Arrays.stream(values()).map(DigestAlgorithm::label).collect(Collectors.joining(", "));
    }

    /** The label, which is how the algorithm is written in a manifest. */
    @Override
    public String toJson() {
        return label;
    }

    /** The label, such as {@code sha256}. */
    @Override
    public String toString() {
        return label;
    }
}
