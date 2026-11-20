package io.github.rkaushikdev.decklewright.store;

import static io.github.rkaushikdev.decklewright.store.StoreFailures.detail;
import static io.github.rkaushikdev.decklewright.store.StoreFailures.details;
import static io.github.rkaushikdev.decklewright.store.StoreFailures.failure;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.ManifestError;
import io.github.rkaushikdev.decklewright.StoreError;
import io.github.rkaushikdev.decklewright.codec.Codecs;
import io.github.rkaushikdev.decklewright.manifest.Checksums;
import io.github.rkaushikdev.decklewright.manifest.Payload;
import io.github.rkaushikdev.decklewright.manifest.PayloadEntry;
import io.github.rkaushikdev.decklewright.manifest.Payloads;
import io.github.rkaushikdev.decklewright.manifest.Sidecars;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The four folders a shift moves material through.
 *
 * <p>Captures land in incoming. A run picks one up, works in working, and the finished delivery
 * goes to delivered or, if it did not grade well enough, to rejected. Nothing is ever deleted by
 * this class: a rejected accession is moved, not removed, because the reason it was rejected is
 * usually something about the capture that somebody has to see.
 *
 * <p>The class holds paths and does io. It holds no state about a run, which is what lets two runs
 * share one workspace without arranging anything. A delivery is looked for under delivered unless
 * another folder is named.
 */
public final class Workspace {

    private final WorkspacePaths paths;

    private Workspace(Path root) {
        this.paths = StorePaths.workspacePaths(root);
    }

    /**
     * Open a workspace, making the folders if they are not there. Safe to call from two runs at
     * once, which is why it makes each folder rather than checking first and then making it.
     *
     * @throws StoreError when a folder could not be made
     */
    public static Workspace open(Path root) {
        Workspace workspace = new Workspace(root);
        for (Folder folder : Folder.values()) {
            AtomicFiles.ensureDirectory(workspace.paths.folder(folder));
        }
        return workspace;
    }

    /**
     * Open one that is expected to exist already, so a typed path that is not a workspace fails here
     * rather than by quietly making one.
     *
     * @throws StoreError when there is no incoming folder under the root
     */
    public static Workspace at(Path root) {
        Workspace workspace = new Workspace(root);
        if (!Files.exists(workspace.paths.incoming())) {
            throw new StoreError(
                    "that is not a workspace", detail("root", workspace.root().toString()));
        }
        return workspace;
    }

    /** Every path of this workspace. */
    public WorkspacePaths paths() {
        return paths;
    }

    /** The workspace itself, absolute and normalised. */
    public Path root() {
        return paths.root();
    }

    /**
     * The captures waiting to be worked, in name order.
     *
     * <p>Anything the codecs do not recognise by extension is left out rather than reported, and so
     * is anything that is not a regular file. An incoming folder always has a spreadsheet and
     * somebody's notes in it, and treating those as failed captures makes every run look bad.
     *
     * @throws StoreError when the folder could not be listed or a capture could not be looked at
     */
    public List<IncomingCapture> incoming() {
        List<IncomingCapture> out = new ArrayList<>();
        for (String name : AtomicFiles.listNames(paths.incoming())) {
            if (Codecs.codecForPath(name).isEmpty()) {
                continue;
            }
            Path path = paths.incoming().resolve(name);
            BasicFileAttributes info;
            try {
                info = Files.readAttributes(path, BasicFileAttributes.class);
            } catch (IOException cause) {
                throw failure("that file could not be read", detail("path", path.toString()), cause);
            }
            if (info.isRegularFile()) {
                out.add(new IncomingCapture(name, path, stemOf(name), info.size()));
            }
        }
        return List.copyOf(out);
    }

    /**
     * Where an accession's delivery goes under delivered.
     *
     * @see #pathFor(String, Folder)
     */
    public Path pathFor(String accession) {
        return pathFor(accession, Folder.DELIVERED);
    }

    /**
     * Where an accession's delivery goes in a folder, checked to be inside the workspace.
     *
     * @throws io.github.rkaushikdev.decklewright.BadArgumentError when nothing of the accession
     *     survives cleaning
     * @throws StoreError when the path would be outside the workspace
     * @see StorePaths#deliveryPath(WorkspacePaths, String, Folder)
     */
    public Path pathFor(String accession, Folder folder) {
        return StorePaths.requireInside(root(), StorePaths.deliveryPath(paths, accession, folder));
    }

    /**
     * Whether an accession has already been delivered.
     *
     * @see #has(String, Folder)
     */
    public boolean has(String accession) {
        return has(accession, Folder.DELIVERED);
    }

    /**
     * Whether an accession is in a folder. A run asks this before it does the work, because redoing
     * a delivery costs minutes and overwriting one costs a curator their afternoon.
     */
    public boolean has(String accession, Folder folder) {
        return Files.exists(pathFor(accession, folder));
    }

    /**
     * Write a delivery under delivered, refusing one that is already there.
     *
     * @see #put(Payload, Folder, boolean)
     */
    public WrittenSet put(Payload payload) {
        return put(payload, Folder.DELIVERED, false);
    }

    /**
     * Write a delivery into a folder, refusing one that is already there.
     *
     * @see #put(Payload, Folder, boolean)
     */
    public WrittenSet put(Payload payload, Folder folder) {
        return put(payload, folder, false);
    }

    /**
     * Write a delivery.
     *
     * <p>The payload is checked against itself again here, even though whatever built it checked it
     * too. It costs a hash of a few megabytes and it is the last moment at which a wrong delivery is
     * still only in memory. The files are written as one set: all of them or none.
     *
     * @param overwrite true replaces a delivery already under that accession
     * @throws StoreError when the delivery does not agree with itself (the details carry the first
     *     objection and how many there were), when it has already been delivered and may not be
     *     replaced, or when it could not be written
     */
    public WrittenSet put(Payload payload, Folder folder, boolean overwrite) {
        List<String> objections = Payloads.objectionsToPayload(payload);
        if (!objections.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("accession", payload.accession());
            details.put("first", objections.get(0));
            details.put("objections", objections.size());
            throw new StoreError("that delivery does not agree with itself", details);
        }

        Path directory = pathFor(payload.accession(), folder);
        if (!overwrite && Files.exists(directory)) {
            throw new StoreError(
                    "that accession has already been delivered",
                    details("accession", payload.accession(), "directory", directory.toString()));
        }
        return AtomicFiles.writeSetAtomic(directory, payload.entries(), true);
    }

    /**
     * Read a delivery back off the disk from delivered.
     *
     * @see #get(String, Folder)
     */
    public Payload get(String accession) {
        return get(accession, Folder.DELIVERED);
    }

    /**
     * Read a delivery back off the disk as the same shape {@link #put} took: every file in its
     * folder, by name, with the digest of the set worked out again.
     *
     * @throws StoreError when there is no delivery under that accession, or a file could not be read
     */
    public Payload get(String accession, Folder folder) {
        Path directory = pathFor(accession, folder);
        if (!Files.exists(directory)) {
            throw new StoreError(
                    "there is no delivery under that accession",
                    details("accession", accession, "directory", directory.toString()));
        }
        List<PayloadEntry> entries = new ArrayList<>();
        for (String name : AtomicFiles.listNames(directory)) {
            entries.add(new PayloadEntry(name, AtomicFiles.readBytes(directory.resolve(name))));
        }
        return Payload.of(accession, entries);
    }

    /**
     * Check a delivery under delivered against its sidecar.
     *
     * @see #verify(String, Folder)
     */
    public DeliveryCheck verify(String accession) {
        return verify(accession, Folder.DELIVERED);
    }

    /**
     * Check a delivery on disk against the sidecar sitting in it.
     *
     * <p>This is the call a fixity sweep makes, years later, over everything. It reports rather than
     * throws, because a sweep that stops at the first bad file tells a repository about one problem
     * when it has forty. Everything the payload check objects to is a problem, and so is every file
     * the sidecar lists whose sha256 has changed; each problem is said once.
     */
    public DeliveryCheck verify(String accession, Folder folder) {
        Payload payload;
        try {
            payload = get(accession, folder);
        } catch (DeckleError cause) {
            return new DeliveryCheck(accession, false, 0, List.of(cause.getMessage()));
        }

        List<String> problems = new ArrayList<>(Payloads.objectionsToPayload(payload));
        int checked = 0;
        for (Object listed : derivativesListedIn(payload)) {
            Object file = listed instanceof Map<?, ?> item ? item.get("file") : null;
            Optional<byte[]> bytes =
                    file instanceof String name ? Payloads.entryNamed(payload, name) : Optional.empty();
            if (bytes.isEmpty()) {
                continue;
            }
            checked += 1;
            if (!Checksums.sameDigest(Checksums.digest(bytes.get()), sha256Of(listed))) {
                problems.add(file + " has changed since it was delivered");
            }
        }
        return new DeliveryCheck(accession, problems.isEmpty(), checked, List.copyOf(new LinkedHashSet<>(problems)));
    }

    /**
     * Move a delivery from one folder to another, which is what happens when a curator overrules a
     * grading in either direction.
     *
     * @throws StoreError when there is nothing to move, there is already one where it would go, or
     *     it could not be moved
     */
    public Moved move(String accession, Folder from, Folder to) {
        Path source = pathFor(accession, from);
        Path target = pathFor(accession, to);
        if (!Files.exists(source)) {
            throw new StoreError(
                    "there is nothing to move", details("accession", accession, "from", source.toString()));
        }
        if (Files.exists(target)) {
            throw new StoreError(
                    "there is already one there", details("accession", accession, "to", target.toString()));
        }

        AtomicFiles.ensureDirectory(paths.folder(to));
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException cause) {
            throw failure("that delivery could not be moved", detail("accession", accession), cause);
        }
        return new Moved(source, target);
    }

    /**
     * Everything delivered, for a listing.
     *
     * @see #list(Folder)
     */
    public List<String> list() {
        return list(Folder.DELIVERED);
    }

    /**
     * Everything in a folder, by name, sorted, without the leftovers of a crashed run; nothing at
     * all when the folder is not there.
     */
    public List<String> list(Folder folder) {
        Path where = paths.folder(folder);
        return Files.exists(where) ? AtomicFiles.listNames(where) : List.of();
    }

    /**
     * Clear the working folder and sweep up what a crashed run left in it.
     *
     * @return the names of the leftovers that were swept
     * @throws StoreError when something in the working folder could not be removed
     */
    public List<String> tidy() {
        List<String> swept;
        try {
            swept = AtomicFiles.sweepTemporary(paths.working());
        } catch (StoreError unswept) {
            // A working folder that cannot be swept has swept nothing; what can be cleared still is.
            swept = List.of();
        }
        for (String name : list(Folder.WORKING)) {
            AtomicFiles.removeTree(paths.working().resolve(name));
        }
        return swept;
    }

    /** The workspace's root, such as {@code Workspace[/lab/oversize]}. */
    @Override
    public String toString() {
        return "Workspace[" + root() + "]";
    }

    /** The accession a capture's file name gives: the name without its extension. */
    private static String stemOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * The derivatives a payload's sidecar lists; none when there is no sidecar or it will not read,
     * which the objections to the payload already say.
     */
    private static List<?> derivativesListedIn(Payload payload) {
        Optional<byte[]> bytes = Payloads.entryNamed(payload, Payloads.SIDECAR_NAME);
        if (bytes.isEmpty()) {
            return List.of();
        }
        Map<String, Object> sidecar;
        try {
            sidecar = Sidecars.parseSidecar(bytes.get());
        } catch (ManifestError unreadable) {
            return List.of();
        }
        return sidecar.get("derivatives") instanceof List<?> listed ? listed : List.of();
    }

    private static Object sha256Of(Object listed) {
        Object digests = listed instanceof Map<?, ?> item ? item.get("digests") : null;
        return digests instanceof Map<?, ?> held ? held.get("sha256") : null;
    }
}
