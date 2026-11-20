package io.github.rkaushikdev.decklewright.manifest;

import java.util.Arrays;
import java.util.Objects;

/**
 * One named blob of a delivery: a file as it will sit in the delivery's folder.
 *
 * <p>The bytes are held as given, not copied, because a master is a few hundred megabytes. Two
 * entries are equal when their names are and their bytes have the same content.
 *
 * @param name the file name inside the delivery, which may name a subfolder
 * @param bytes the whole file
 */
public record PayloadEntry(String name, byte[] bytes) {

    /** An entry of the given parts; the bytes are not copied. */
    public PayloadEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(bytes, "bytes");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PayloadEntry that && name.equals(that.name) && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + Arrays.hashCode(bytes);
    }

    /** The name and the size, such as {@code PayloadEntry[sidecar.json, 1890 bytes]}. */
    @Override
    public String toString() {
        return "PayloadEntry[" + name + ", " + bytes.length + " bytes]";
    }
}
