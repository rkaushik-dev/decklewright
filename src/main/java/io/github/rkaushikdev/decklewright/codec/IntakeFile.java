package io.github.rkaushikdev.decklewright.codec;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * One file as it sits in the intake folder: its bytes and, usually, its name.
 *
 * <p>The bytes are held as given, not copied, because a master is a few hundred megabytes. Two files
 * are equal when their bytes and their names are.
 *
 * @param bytes the whole file
 * @param path the name it came in under, when known
 * @see Inspections#triage(java.util.List)
 */
public record IntakeFile(byte[] bytes, Optional<String> path) {
    /** A file of the given parts. */
    public IntakeFile {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(path, "path");
    }

    /** A file and the name it came in under. */
    public static IntakeFile of(byte[] bytes, String path) {
        return new IntakeFile(bytes, Optional.of(path));
    }

    /** A file whose name is not known. */
    public static IntakeFile of(byte[] bytes) {
        return new IntakeFile(bytes, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IntakeFile that && Arrays.equals(bytes, that.bytes) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bytes) + path.hashCode();
    }

    /** The name and size, such as {@code IntakeFile[q17.png, 4096 bytes]}. */
    @Override
    public String toString() {
        return "IntakeFile[" + path.orElse("(no name)") + ", " + bytes.length + " bytes]";
    }
}
