package io.github.rkaushikdev.decklewright.store;

import static io.github.rkaushikdev.decklewright.store.StoreFailures.detail;
import static io.github.rkaushikdev.decklewright.store.StoreFailures.failure;

import io.github.rkaushikdev.decklewright.StoreError;
import io.github.rkaushikdev.decklewright.manifest.PayloadEntry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writing without leaving half a file behind.
 *
 * <p>A capture run takes minutes and produces files that are hundreds of megabytes. Somebody will
 * close the laptop, the machine will lose power, and the share will go away in the middle of a
 * write, and all three have to leave the folder in a state a later run can understand.
 *
 * <p>The rule is that a file appears at its final name only when it is complete. Everything is
 * written to a name ending in {@code .part} and moved into place with an atomic move, and a move
 * within a file system is the one operation this whole tree can rely on to either happen or not.
 * Text is written as UTF-8, an unpaired surrogate as U+FFFD.
 */
public final class AtomicFiles {

    /** U+FFFD in UTF-8, written in place of an unpaired surrogate. */
    private static final byte[] REPLACEMENT = {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD};

    private AtomicFiles() {}

    /**
     * Make a directory and its parents, and be content with one that is already there.
     *
     * @throws StoreError when it could not be made, such as when a file is in the way
     */
    public static void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException cause) {
            throw failure("that directory could not be made", detail("path", path.toString()), cause);
        }
    }

    /**
     * Write bytes so that the file is either the old one or the new one, replacing any that is
     * there.
     *
     * @see #writeAtomic(Path, byte[], boolean)
     */
    public static WrittenFile writeAtomic(Path path, byte[] content) {
        return writeAtomic(path, content, true);
    }

    /**
     * Write text as UTF-8 so that the file is either the old one or the new one, replacing any that
     * is there.
     *
     * @see #writeAtomic(Path, byte[], boolean)
     */
    public static WrittenFile writeAtomic(Path path, String content) {
        return writeAtomic(path, utf8(content), true);
    }

    /**
     * Write text as UTF-8 so that the file is either the old one or the new one.
     *
     * @see #writeAtomic(Path, byte[], boolean)
     */
    public static WrittenFile writeAtomic(Path path, String content, boolean overwrite) {
        return writeAtomic(path, utf8(content), overwrite);
    }

    /**
     * Write bytes so that the file is either the old one or the new one: to a temporary name beside
     * it, then moved into place. The folder is made on the way.
     *
     * @param overwrite false refuses a file that is already there rather than replacing it
     * @return the path as given, and the size now on disk
     * @throws StoreError when there is already a file there and it may not be replaced, or the file
     *     could not be written; a failed write leaves no temporary file behind
     */
    public static WrittenFile writeAtomic(Path path, byte[] content, boolean overwrite) {
        if (!overwrite && Files.exists(path)) {
            throw new StoreError("there is already a file there", detail("path", path.toString()));
        }
        ensureParent(path);
        Path temporary = StorePaths.temporaryFor(path);
        try {
            Files.write(temporary, content);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException cause) {
            StoreError failed = failure("that file could not be written", detail("path", path.toString()), cause);
            removeStaged(List.of(temporary), failed);
            throw failed;
        }
        try {
            return new WrittenFile(path, Files.size(path));
        } catch (IOException cause) {
            throw failure("that file could not be written", detail("path", path.toString()), cause);
        }
    }

    /**
     * Write a whole set of files, replacing any that are there.
     *
     * @see #writeSetAtomic(Path, List, boolean)
     */
    public static WrittenSet writeSetAtomic(Path directory, List<PayloadEntry> entries) {
        return writeSetAtomic(directory, entries, true);
    }

    /**
     * Write a whole set of files into one directory, and leave nothing behind if any of them fails.
     *
     * <p>Every file is written to its temporary name first and they are moved into place only once
     * all of them are on disk. A folder with eight of nine derivatives in it looks finished to
     * everything downstream, which is the whole reason this is not a loop over {@link
     * #writeAtomic}. A name may hold a subfolder, which is made on the way.
     *
     * @param overwrite false refuses the set if any file of it is already there
     * @return the directory, every file's final path, and the size of the set
     * @throws StoreError when there is nothing to write, a file is already there and may not be
     *     replaced, the set could not be staged, or it was only partly moved into place; the details
     *     of the last say how many of how many were moved
     */
    public static WrittenSet writeSetAtomic(Path directory, List<PayloadEntry> entries, boolean overwrite) {
        if (entries.isEmpty()) {
            throw new StoreError("there is nothing to write", detail("directory", directory.toString()));
        }
        ensureDirectory(directory);

        List<Path> temporaries = new ArrayList<>();
        List<Path> targets = new ArrayList<>();
        try {
            for (PayloadEntry entry : entries) {
                Path target = join(directory, entry.name());
                if (!overwrite && Files.exists(target)) {
                    throw new StoreError("there is already a file there", detail("path", target.toString()));
                }
                ensureParent(target);
                Path temporary = StorePaths.temporaryFor(target);
                Files.write(temporary, entry.bytes());
                temporaries.add(temporary);
                targets.add(target);
            }
        } catch (StoreError refused) {
            removeStaged(temporaries, refused);
            throw refused;
        } catch (IOException | InvalidPathException cause) {
            StoreError failed =
                    failure("that set could not be staged", detail("directory", directory.toString()), cause);
            removeStaged(temporaries, failed);
            throw failed;
        }

        List<Path> written = new ArrayList<>();
        try {
            for (int i = 0; i < temporaries.size(); i += 1) {
                Files.move(temporaries.get(i), targets.get(i), StandardCopyOption.ATOMIC_MOVE);
                written.add(targets.get(i));
            }
        } catch (IOException cause) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("directory", directory.toString());
            details.put("moved", written.size());
            details.put("of", temporaries.size());
            throw failure("that set was half moved into place", details, cause);
        }

        long bytes = entries.stream().mapToLong(entry -> entry.bytes().length).sum();
        return new WrittenSet(directory, written, bytes);
    }

    /**
     * Append a line to a file, making it if it is not there.
     *
     * <p>This is the one write that is not atomic and cannot be. A ledger is appended to by every run
     * and rewriting it whole would lose the entries another run added in between. A short append is
     * atomic enough on every file system this runs on, and a torn line at the end of a ledger is
     * recoverable by reading the lines that parse. A line feed inside the line becomes a space, so a
     * line never becomes two, and one line feed ends it.
     *
     * @throws StoreError when the folder could not be made or the line could not be appended
     */
    public static void appendLine(Path path, String line) {
        ensureParent(path);
        byte[] text = utf8(line.replace('\n', ' ') + "\n");
        try {
            Files.write(path, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException cause) {
            throw failure("that line could not be appended", detail("path", path.toString()), cause);
        }
    }

    /**
     * The whole of a file.
     *
     * @throws StoreError when it could not be read, saying which
     */
    public static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException cause) {
            throw failure("that file could not be read", detail("path", path.toString()), cause);
        }
    }

    /**
     * The whole of a file as UTF-8 text, a malformed sequence read as U+FFFD and a byte order mark
     * kept as the character it is.
     *
     * @throws StoreError when it could not be read, saying which
     */
    public static String readText(Path path) {
        return new String(readBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * Everything in a directory, without the leftovers of a crashed run.
     *
     * @see #listNames(Path, boolean)
     */
    public static List<String> listNames(Path directory) {
        return listNames(directory, false);
    }

    /**
     * The names of everything in a directory, files and folders alike, sorted in UTF-16 code unit
     * order.
     *
     * @param includeTemporary true to keep the {@code .part} files a crashed run left
     * @throws StoreError when the directory could not be listed, saying which
     */
    public static List<String> listNames(Path directory, boolean includeTemporary) {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> listing = Files.newDirectoryStream(directory)) {
            for (Path one : listing) {
                String name = one.getFileName().toString();
                if (includeTemporary || !StorePaths.isTemporary(name)) {
                    names.add(name);
                }
            }
        } catch (IOException | DirectoryIteratorException cause) {
            throw failure("that directory could not be listed", detail("directory", directory.toString()), cause);
        }
        names.sort(null);
        return List.copyOf(names);
    }

    /**
     * Sweep up what a crashed run left. It only removes names this class would have made, so a
     * directory somebody else keeps other files in is safe.
     *
     * @return the names removed, sorted
     * @throws StoreError when the directory could not be listed or a leftover could not be removed
     */
    public static List<String> sweepTemporary(Path directory) {
        List<String> leftovers = listNames(directory, true).stream()
                .filter(StorePaths::isTemporary)
                .toList();
        for (String name : leftovers) {
            Path path = directory.resolve(name);
            try {
                Files.deleteIfExists(path);
            } catch (IOException cause) {
                throw failure("that file could not be removed", detail("path", path.toString()), cause);
            }
        }
        return leftovers;
    }

    /**
     * Remove a file or a whole folder, without following links out of it, and be content when it is
     * already gone.
     */
    static void removeTree(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path folder, IOException failed) throws IOException {
                    if (failed != null) {
                        throw failed;
                    }
                    Files.delete(folder);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException cause) {
            throw failure("that file could not be removed", detail("path", path.toString()), cause);
        }
    }

    /** A name inside a directory, the whole normalised, as a path built from text is. */
    private static Path join(Path directory, String name) {
        return directory.getFileSystem().getPath(directory.toString(), name).normalize();
    }

    private static void ensureParent(Path path) {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            ensureDirectory(parent);
        }
    }

    /**
     * Remove what was staged before a failure, keeping anything that could not be removed on the
     * failure, so the reason for the leftover travels with the reason for the failure.
     */
    private static void removeStaged(List<Path> temporaries, StoreError failure) {
        for (Path temporary : temporaries) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException left) {
                failure.addSuppressed(left);
            }
        }
    }

    private static byte[] utf8(String text) {
        CharsetEncoder encoder = StandardCharsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(REPLACEMENT);
        try {
            ByteBuffer encoded = encoder.encode(CharBuffer.wrap(text));
            byte[] out = new byte[encoded.remaining()];
            encoded.get(out);
            return out;
        } catch (CharacterCodingException cause) {
            // Every coding error is replaced rather than reported, so the encoder never throws this.
            throw new IllegalStateException("UTF-8 refused to encode text", cause);
        }
    }
}
