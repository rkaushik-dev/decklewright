package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.codec.ImageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One rendered derivative written out as bytes, kept beside what it was written from.
 *
 * <p>The bytes are held as given, not copied, because a master is a few hundred megabytes. Two
 * encodings are equal when every part is, the bytes compared by content.
 *
 * @param name the derivative's name in the profile
 * @param rendered what was written
 * @param bytes the whole file
 * @param format what it was written as
 * @param extension the extension that format takes, including the leading dot
 * @param notes what an operator should know about the file, such as metadata it could not carry;
 *     copied
 * @see Recipes#encodeRendered(Rendered, EncodeOptions)
 */
public record Encoded(
        String name, Rendered rendered, byte[] bytes, ImageFormat format, String extension, List<String> notes)
        implements Nameable {

    /** An encoding of the given parts; the notes are copied and the bytes are not. */
    public Encoded {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(rendered, "rendered");
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(extension, "extension");
        notes = List.copyOf(notes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Encoded that
                && name.equals(that.name)
                && rendered.equals(that.rendered)
                && Arrays.equals(bytes, that.bytes)
                && format == that.format
                && extension.equals(that.extension)
                && notes.equals(that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, rendered, Arrays.hashCode(bytes), format, extension, notes);
    }

    /** The name, format and size, such as {@code Encoded[master, png, 4096 bytes]}. */
    @Override
    public String toString() {
        return "Encoded[" + name + ", " + format + ", " + bytes.length + " bytes]";
    }
}
