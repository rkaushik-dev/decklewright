package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.codec.ImageFormat;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One file of a derived set, named and ready to be packed into a delivery and written.
 *
 * <p>The bytes are held as given, not copied, because a master is a few hundred megabytes. Two files
 * are equal when every part is, the bytes compared by content.
 *
 * @param name the derivative's name in the profile, such as {@code master} or {@code thumb}
 * @param fileName what it is written as, extension included
 * @param bytes the whole file
 * @param format what it was written as
 * @param sampling the frequency of its pixels, which its sidecar entry records
 * @param notes what an operator should know about the file, such as metadata it could not carry;
 *     copied
 * @see Derivatives#deriveFromPlan
 */
public record DerivedFile(
        String name, String fileName, byte[] bytes, ImageFormat format, Sampling sampling, List<String> notes) {

    /** A file of the given parts; the notes are copied and the bytes are not. */
    public DerivedFile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(sampling, "sampling");
        notes = List.copyOf(notes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DerivedFile that
                && name.equals(that.name)
                && fileName.equals(that.fileName)
                && Arrays.equals(bytes, that.bytes)
                && format == that.format
                && sampling.equals(that.sampling)
                && notes.equals(that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fileName, Arrays.hashCode(bytes), format, sampling, notes);
    }

    /** The name, file name and size, such as {@code DerivedFile[master, g4181_master.png, 4096 bytes]}. */
    @Override
    public String toString() {
        return "DerivedFile[" + name + ", " + fileName + ", " + bytes.length + " bytes]";
    }
}
