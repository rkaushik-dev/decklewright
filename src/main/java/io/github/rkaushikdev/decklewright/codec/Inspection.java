package io.github.rkaushikdev.decklewright.codec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a look at one file found.
 *
 * @param ok true when the file has a shape and nothing worse than a note about its extension
 * @param format the format the bytes carry the signature of, when any does
 * @param shape the shape the header declares, when it could be read
 * @param problems everything found wrong, most important first
 * @param path the name the file came in under, when one was given
 * @see Inspections#inspect(byte[], String)
 */
public record Inspection(
        boolean ok,
        Optional<ImageFormat> format,
        Optional<ImageShape> shape,
        List<Problem> problems,
        Optional<String> path) {
    /** An inspection of the given parts; the problems are copied. */
    public Inspection {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(shape, "shape");
        problems = List.copyOf(problems);
        Objects.requireNonNull(path, "path");
    }
}
