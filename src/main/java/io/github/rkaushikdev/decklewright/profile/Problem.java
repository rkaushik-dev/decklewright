package io.github.rkaushikdev.decklewright.profile;

import java.util.Objects;

/**
 * Something wrong with a profile that only shows when two or more settings are read together.
 *
 * @param path the setting the problem is reported against, such as {@code crop.anchor}
 * @param message what is wrong, in words a curator can act on
 * @param severity whether a run stops for it
 * @see Validation#inspectProfile(Profile)
 */
public record Problem(String path, String message, Severity severity) {

    /** A problem of the given path, message and severity. */
    public Problem {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(severity, "severity");
    }
}
