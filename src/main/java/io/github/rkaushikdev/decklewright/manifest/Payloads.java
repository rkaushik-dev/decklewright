package io.github.rkaushikdev.decklewright.manifest;

import io.github.rkaushikdev.decklewright.ManifestError;
import io.github.rkaushikdev.decklewright.derive.DerivedFile;
import io.github.rkaushikdev.decklewright.util.Json;
import io.github.rkaushikdev.decklewright.util.JsonSyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A delivery assembled from its parts, and checked against itself before anything is written.
 *
 * <p>Everything that goes into one folder and travels together: the derivative files, the sidecar
 * that describes them, the provenance chain, and a plain checksum file that a tape robot can read
 * without knowing anything about any of the rest of it.
 */
public final class Payloads {

    /** What the sidecar is called inside a delivery. */
    public static final String SIDECAR_NAME = "sidecar.json";

    /** What the provenance chain is called inside a delivery. */
    public static final String PROVENANCE_NAME = "provenance.json";

    /** What the checksum file is called inside a delivery. */
    public static final String SUMS_NAME = "sha256sums.txt";

    private Payloads() {}

    /**
     * A delivery with no provenance chain in it.
     *
     * @see #payloadFor(String, List, Map, List)
     */
    public static Payload payloadFor(String accession, List<DerivedFile> files, Map<String, ?> sidecar) {
        return assemble(accession, files, sidecar, Optional.empty());
    }

    /**
     * Assemble a delivery: the files under their file names, then the sidecar, the provenance chain
     * and the checksum file, each written as text in UTF-8.
     *
     * <p>The checksum file lists the image files and not itself, which is the only arrangement that
     * terminates. It also does not list the sidecar, because the sidecar carries its own digests for
     * the images and a second copy of them in a third place is a third thing to keep in step. The
     * chain is written as json indented by two spaces with a trailing newline, and the checksum file
     * ends with one too.
     *
     * @param sidecar the document, written by {@link Sidecars#writeSidecar(Map)}
     * @param provenance the chain, written even when it is empty
     * @throws ManifestError when there are no files
     */
    public static Payload payloadFor(
            String accession, List<DerivedFile> files, Map<String, ?> sidecar, List<ProvenanceEvent> provenance) {
        return assemble(accession, files, sidecar, Optional.of(provenance));
    }

    /** The bytes of the first entry of that name; empty when the delivery has none. */
    public static Optional<byte[]> entryNamed(Payload payload, String name) {
        return payload.entries().stream()
                .filter(entry -> entry.name().equals(name))
                .map(PayloadEntry::bytes)
                .findFirst();
    }

    /** How large the whole delivery is. */
    public static long payloadSize(Payload payload) {
        return payload.entries().stream()
                .mapToLong(entry -> entry.bytes().length)
                .sum();
    }

    /**
     * Check a delivery against itself, before anything is written.
     *
     * <p>Three things have to agree and they are written by three different pieces of code: the
     * files that are actually here, the files the sidecar says are here, and the files the checksum
     * file lists. Any two of them agreeing is not enough, which is why this compares all three rather
     * than the pair that is convenient.
     *
     * @return every objection, in words; empty when the delivery is consistent
     */
    public static List<String> objectionsToPayload(Payload payload) {
        List<String> out = new ArrayList<>();

        Set<String> present = new HashSet<>();
        payload.entries().forEach(entry -> present.add(entry.name()));
        for (String name : List.of(SIDECAR_NAME, SUMS_NAME)) {
            if (!present.contains(name)) {
                out.add("there is no " + name);
            }
        }

        Optional<byte[]> sidecarBytes = entryNamed(payload, SIDECAR_NAME);
        if (sidecarBytes.isEmpty()) {
            return out;
        }

        Map<String, Object> sidecar;
        try {
            sidecar = Sidecars.parseSidecar(sidecarBytes.get());
        } catch (ManifestError cause) {
            out.add("the sidecar will not read back: " + cause.getMessage());
            return out;
        }

        if (!sidecar.get("accession").equals(payload.accession())) {
            out.add("the sidecar is for " + sidecar.get("accession") + " and this delivery is " + payload.accession());
        }

        List<?> derivatives = Documents.listOf(sidecar.get("derivatives"));
        for (Object listed : derivatives) {
            Object file = Documents.field(listed, "file");
            Optional<byte[]> bytes = entryFor(payload, file);
            if (bytes.isEmpty()) {
                out.add("the sidecar lists " + Documents.text(file) + " and it is not here");
                continue;
            }
            Object said = Documents.field(listed, "bytes");
            if (!(said instanceof Number number && number.doubleValue() == bytes.get().length)) {
                out.add(Documents.text(file) + " is " + bytes.get().length + " bytes and the sidecar says "
                        + Documents.text(said));
            }
            Object held = Documents.field(Documents.field(listed, "digests"), "sha256");
            if (!Checksums.sameDigest(Checksums.digest(bytes.get()), held)) {
                out.add(Documents.text(file) + " does not match the digest in the sidecar");
            }
        }

        Optional<byte[]> sumsBytes = entryNamed(payload, SUMS_NAME);
        if (sumsBytes.isPresent()) {
            List<SumLine> listed = Checksums.fromSumFile(Documents.decode(sumsBytes.get()));
            Set<String> named = new HashSet<>();
            listed.forEach(line -> named.add(line.name()));
            for (Object item : derivatives) {
                Object file = Documents.field(item, "file");
                if (!(file instanceof String name && named.contains(name))) {
                    out.add(Documents.text(file) + " is missing from " + SUMS_NAME);
                }
            }
            for (SumLine line : listed) {
                Optional<byte[]> bytes = entryNamed(payload, line.name());
                if (bytes.isEmpty()) {
                    out.add(SUMS_NAME + " lists " + line.name() + " and it is not here");
                } else if (!Checksums.sameDigest(Checksums.digest(bytes.get()), line.digest())) {
                    out.add(line.name() + " does not match the digest in " + SUMS_NAME);
                }
            }
        }

        return out;
    }

    /**
     * What a delivery looks like to a person, without unpacking it: the accession, how many files
     * and how many bytes, then what the sidecar says and the events of the chain, indented. A part
     * that will not read back is said so rather than thrown.
     */
    public static List<String> describePayload(Payload payload) {
        List<String> lines = new ArrayList<>();
        lines.add(payload.accession() + ", " + payload.entries().size() + " files, " + payloadSize(payload) + " bytes");

        entryNamed(payload, SIDECAR_NAME).ifPresent(bytes -> {
            try {
                lines.addAll(Sidecars.describeSidecar(Sidecars.parseSidecar(bytes)));
            } catch (ManifestError cause) {
                lines.add("the sidecar will not read back");
            }
        });
        entryNamed(payload, PROVENANCE_NAME).ifPresent(bytes -> {
            Optional<List<?>> chain = chainIn(bytes);
            if (chain.isEmpty()) {
                lines.add("the provenance chain will not read back");
                return;
            }
            List<ProvenanceEvent> events =
                    chain.get().stream().map(Provenance::eventOf).toList();
            Provenance.describeChain(events).forEach(line -> lines.add("  " + line));
        });
        return lines;
    }

    private static Payload assemble(
            String accession, List<DerivedFile> files, Map<String, ?> sidecar, Optional<List<ProvenanceEvent>> chain) {
        if (files.isEmpty()) {
            throw new ManifestError("a delivery with no files in it", Map.of("accession", accession));
        }

        List<PayloadEntry> entries = new ArrayList<>();
        List<SumEntry> sums = new ArrayList<>();
        for (DerivedFile file : files) {
            entries.add(new PayloadEntry(file.fileName(), file.bytes()));
            sums.add(new SumEntry(
                    file.fileName(), Map.of(Checksums.DEFAULT_ALGORITHM.label(), Checksums.digest(file.bytes()))));
        }

        entries.add(new PayloadEntry(SIDECAR_NAME, utf8(Sidecars.writeSidecar(sidecar))));
        chain.ifPresent(
                events -> entries.add(new PayloadEntry(PROVENANCE_NAME, utf8(Json.stringify(events, 2) + "\n"))));
        entries.add(new PayloadEntry(SUMS_NAME, utf8(Checksums.toSumFile(sums) + "\n")));

        return Payload.of(accession, entries);
    }

    /** The members of a written chain, or empty when the bytes are not a json list. */
    private static Optional<List<?>> chainIn(byte[] bytes) {
        try {
            return Json.parse(Documents.decode(bytes)) instanceof List<?> members
                    ? Optional.of(members)
                    : Optional.empty();
        } catch (JsonSyntaxException cause) {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> entryFor(Payload payload, Object file) {
        return file instanceof String name ? entryNamed(payload, name) : Optional.empty();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
