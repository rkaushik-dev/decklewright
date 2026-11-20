package io.github.rkaushikdev.decklewright.store;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.StoreError;
import io.github.rkaushikdev.decklewright.derive.FileNames;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Where things go.
 *
 * <p>A workspace is four folders under one root, and the only reason that is worth a class is that
 * every path in it has to be built the same way twice: once when the run writes a delivery and once
 * when somebody comes back a year later to look for it. A path built by string concatenation in two
 * places is a path that will differ in one of them.
 *
 * <p>Nothing here touches the file system. These are names.
 */
public final class StorePaths {

    /** The ledger lives at the root, because it is about the workspace itself. */
    public static final String LEDGER_NAME = "ledger.jsonl";

    /** What every temporary file's name ends with. */
    private static final String TEMPORARY_SUFFIX = ".part";

    /** Both separators, whatever this platform uses, for the check on a name from outside. */
    private static final Pattern EITHER_SEPARATOR = Pattern.compile("[\\\\/]");

    private StorePaths() {}

    /** Every path of the workspace at a root, which is made absolute and normalised first. */
    public static WorkspacePaths workspacePaths(Path root) {
        Path base = root.toAbsolutePath().normalize();
        return new WorkspacePaths(
                base,
                base.resolve(Folder.INCOMING.label()),
                base.resolve(Folder.WORKING.label()),
                base.resolve(Folder.DELIVERED.label()),
                base.resolve(Folder.REJECTED.label()),
                base.resolve(LEDGER_NAME));
    }

    /**
     * Where one accession's delivery goes, under delivered.
     *
     * @see #deliveryPath(WorkspacePaths, String, Folder)
     */
    public static Path deliveryPath(WorkspacePaths workspace, String accession) {
        return deliveryPath(workspace, accession, Folder.DELIVERED);
    }

    /**
     * Where one accession's delivery goes.
     *
     * <p>The accession is put through the same cleaner the file names use, {@link
     * FileNames#safeStem(String)}, so a folder and the files inside it cannot disagree about what
     * the accession is called.
     *
     * @throws BadArgumentError when nothing of the accession survives the cleaning
     */
    public static Path deliveryPath(WorkspacePaths workspace, String accession, Folder folder) {
        return workspace.folder(folder).resolve(FileNames.safeStem(accession));
    }

    /**
     * The path a named file inside a delivery gets.
     *
     * <p>The name is normalised first. One that is absolute or rooted, or that still has a {@code ..}
     * segment, is refused; a segment here ends at either a slash or a backslash, whatever this
     * platform separates paths with, so a name cannot climb out by using the separator of another.
     *
     * @throws StoreError when the name would write outside the delivery
     * @throws InvalidPathException when the name cannot be a path on this platform at all
     */
    public static Path filePath(Path directory, String name) {
        Path cleaned = directory.getFileSystem().getPath(name).normalize();
        List<String> segments = Arrays.asList(EITHER_SEPARATOR.split(cleaned.toString(), -1));
        // A root covers an absolute name and, on a platform with drives, one rooted without a drive.
        if (cleaned.getRoot() != null || segments.contains("..")) {
            throw new StoreError("that file name would write outside the delivery", StoreFailures.detail("name", name));
        }
        return directory.resolve(cleaned).normalize();
    }

    /**
     * Whether a path is inside a directory, which is the only question worth asking before writing
     * anything a name came from outside.
     *
     * <p>Both are made absolute and normalised. A path equal to the directory is not inside it. That
     * distinction matters: the case it rules out is a delivery whose name resolved to the empty
     * string and would have been written over the folder holding every other delivery. Neither is a
     * path whose way from the directory starts with {@code ..}, nor one on another root.
     */
    public static boolean isInside(Path directory, Path candidate) {
        Path from = directory.toAbsolutePath().normalize();
        Path to = candidate.toAbsolutePath().normalize();
        if (from.equals(to)) {
            return false;
        }
        String between;
        try {
            between = from.relativize(to).toString();
        } catch (IllegalArgumentException onAnotherRoot) {
            return false;
        }
        return !between.isEmpty() && !between.startsWith("..");
    }

    /**
     * Refuse a path that is not where it says it is.
     *
     * @return the candidate, absolute and normalised
     * @throws StoreError when it is not inside the directory; the details give both, resolved
     */
    public static Path requireInside(Path directory, Path candidate) {
        if (!isInside(directory, candidate)) {
            throw new StoreError(
                    "that path is outside the workspace",
                    StoreFailures.details(
                            "directory",
                            directory.toAbsolutePath().normalize().toString(),
                            "path",
                            candidate.toAbsolutePath().normalize().toString()));
        }
        return candidate.toAbsolutePath().normalize();
    }

    /**
     * The name a temporary file is written under before it is moved into place, made unique by this
     * process and the time.
     *
     * @see #temporaryFor(Path, String)
     */
    public static Path temporaryFor(Path target) {
        return temporaryFor(
                target, ProcessHandle.current().pid() + "." + Long.toString(System.currentTimeMillis(), 36));
    }

    /**
     * The name a temporary file is written under: the target's own name, a dot, the token, and
     * {@code .part}. It carries the target name so that a crashed run leaves something a person can
     * identify rather than a folder full of hex.
     *
     * @param token what makes it unique, chosen by the caller
     */
    public static Path temporaryFor(Path target, String token) {
        return target.getFileSystem().getPath(target + "." + token + TEMPORARY_SUFFIX);
    }

    /** Whether a name looks like one of ours left behind by a crash: it ends in {@code .part}. */
    public static boolean isTemporary(String name) {
        return name.endsWith(TEMPORARY_SUFFIX);
    }

    /**
     * The last three segments of a path, for a log line that has to fit on one line.
     *
     * @see #shortPath(Path, int)
     */
    public static String shortPath(Path path) {
        return shortPath(path, 3);
    }

    /**
     * The trailing segments of a path, for a log line that has to fit on one line: the path itself
     * when it is that short, else an ellipsis and the segments, joined by this platform's separator.
     *
     * @param keep how many trailing segments; zero or less counts from the front instead, so zero
     *     keeps them all behind the ellipsis
     */
    public static String shortPath(Path path, int keep) {
        String separator = path.getFileSystem().getSeparator();
        List<String> parts = Arrays.stream(path.normalize().toString().split(Pattern.quote(separator), -1))
                .filter(part -> !part.isEmpty())
                .toList();
        if (parts.size() <= keep) {
            return String.join(separator, parts);
        }
        int from = keep > 0 ? parts.size() - keep : Math.min(-keep, parts.size());
        return "..." + separator + String.join(separator, parts.subList(from, parts.size()));
    }
}
