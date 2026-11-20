package io.github.rkaushikdev.decklewright.store;

import static io.github.rkaushikdev.decklewright.store.AtomicTest.namesIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.decklewright.StoreError;
import io.github.rkaushikdev.decklewright.manifest.PayloadEntry;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * When the file system says no, the store says in words what could not be done and where, keeps
 * the file system's own failure as the cause and as the {@code cause} detail, and leaves no
 * temporary file behind it.
 */
class FileErrorsTest {

    private static final byte[] BYTES = {1, 2, 3, 4};

    /** A directory with a file in it, which nothing can be renamed over. */
    private static Path occupiedDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        Files.writeString(path.resolve("keep.txt"), "occupied");
        return path;
    }

    @Test
    void saysWhichDirectoryCouldNotBeMadeWhenAFileIsInTheWay(@TempDir Path root) throws IOException {
        Path blocked = root.resolve("in-the-way");
        Files.writeString(blocked, "a file");

        StoreError error = catchThrowableOfType(StoreError.class, () -> AtomicFiles.ensureDirectory(blocked));
        assertThat(error).hasMessage("that directory could not be made");
        assertThat(error.details()).containsEntry("path", blocked.toString());
        assertThat((String) error.details().get("cause")).startsWith("FileAlreadyExistsException: ");
        assertThat(error).hasCauseInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    void leavesNoTemporaryFileWhenAWriteCannotBeMovedIntoPlace(@TempDir Path root) throws IOException {
        Path target = occupiedDirectory(root.resolve("master.png"));

        StoreError error = catchThrowableOfType(StoreError.class, () -> AtomicFiles.writeAtomic(target, BYTES));
        assertThat(error).hasMessage("that file could not be written");
        assertThat(error.details()).containsEntry("path", target.toString()).containsKey("cause");
        assertThat(namesIn(root)).isEqualTo(List.of("master.png"));
    }

    @Test
    void saysHowFarASetGotWhenALaterFileCannotBeMovedIntoPlace(@TempDir Path root) throws IOException {
        Path directory = root.resolve("a");
        occupiedDirectory(directory.resolve("b.png"));
        List<PayloadEntry> entries = List.of(new PayloadEntry("a.png", BYTES), new PayloadEntry("b.png", BYTES));

        StoreError error = catchThrowableOfType(StoreError.class, () -> AtomicFiles.writeSetAtomic(directory, entries));
        assertThat(error).hasMessage("that set was half moved into place");
        assertThat(error.details())
                .containsEntry("directory", directory.toString())
                .containsEntry("moved", 1)
                .containsEntry("of", 2)
                .containsKey("cause");
    }

    @Test
    void saysWhichFileCouldNotBeReadWhenItIsAFolder(@TempDir Path root) {
        StoreError error = catchThrowableOfType(StoreError.class, () -> AtomicFiles.readText(root));
        assertThat(error).hasMessage("that file could not be read");
        assertThat(error.details()).containsEntry("path", root.toString());
        assertThat(error.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    void saysWhichDirectoryCouldNotBeListedWhenItIsAFile(@TempDir Path root) throws IOException {
        Path file = Files.writeString(root.resolve("notes.txt"), "not a folder");

        StoreError error = catchThrowableOfType(StoreError.class, () -> AtomicFiles.listNames(file));
        assertThat(error).hasMessage("that directory could not be listed");
        assertThat(error.details()).containsEntry("directory", file.toString());
        assertThat(error).hasCauseInstanceOf(NotDirectoryException.class);
    }

    @Test
    void saysWhichFileALineCouldNotBeAppendedTo(@TempDir Path root) {
        StoreError error = catchThrowableOfType(StoreError.class, () -> AtomicFiles.appendLine(root, "one"));
        assertThat(error).hasMessage("that line could not be appended");
        assertThat(error.details()).containsEntry("path", root.toString()).containsKey("cause");
    }

    @Test
    void saysWhichCaptureCouldNotBeLookedAt(@TempDir Path root) throws IOException {
        Workspace workspace = Workspace.open(root);
        Path dangling = workspace.paths().incoming().resolve("g4181.png");
        Files.createSymbolicLink(dangling, root.resolve("gone.png"));

        StoreError error = catchThrowableOfType(StoreError.class, workspace::incoming);
        assertThat(error).hasMessage("that file could not be read");
        assertThat(error.details()).containsEntry("path", dangling.toString());
        assertThat(error).hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void clearsFoldersOutOfTheWorkingFolderAsWellAsFiles(@TempDir Path root) throws IOException {
        Workspace workspace = Workspace.open(root);
        Path run = Files.createDirectories(
                workspace.paths().working().resolve("run-1").resolve("scans"));
        Files.write(run.resolve("x.png"), BYTES);
        Files.write(workspace.paths().working().resolve("half.png"), BYTES);

        assertThat(workspace.tidy()).isEmpty();
        assertThat(namesIn(workspace.paths().working())).isEmpty();
    }
}
