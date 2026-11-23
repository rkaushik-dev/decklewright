package io.github.rkaushikdev.decklewright.cli.commands;

import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Objects;

/**
 * A capture off a disk, decoded and read the way its profile says: what every command that works on
 * one capture starts from.
 *
 * @param file the file as typed, with its accession and bytes
 * @param surface the decoded capture
 * @param reading what the detector read off it
 * @see Common#readCaptureAt(String,
 *     io.github.rkaushikdev.decklewright.profile.Profile)
 */
public record ReadCapture(CaptureFile file, Surface surface, CaptureReading reading) {
    /** A read capture of the given parts. */
    public ReadCapture {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(reading, "reading");
    }
}
