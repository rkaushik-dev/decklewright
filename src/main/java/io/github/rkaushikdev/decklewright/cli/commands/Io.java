package io.github.rkaushikdev.decklewright.cli.commands;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Everything a command reaches outside itself for: where its lines go, the directory it works
 * relative to, and the time it is.
 *
 * <p>A command is a function of its arguments and one of these, and the io is passed in rather
 * than reached for. That is the whole reason the commands are testable: a test hands in writers
 * that collect lines, and nothing anywhere prints to a terminal that a test cannot see.
 */
public final class Io {
    private final Consumer<String> out;
    private final Consumer<String> err;
    private final Path cwd;
    private final Supplier<Instant> now;

    private Io(Consumer<String> out, Consumer<String> err, Path cwd, Supplier<Instant> now) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        this.now = Objects.requireNonNull(now, "now");
    }

    /** The terminal: standard output and standard error, the working directory, and the clock. */
    public static Io system() {
        return of(System.out::println, System.err::println);
    }

    /** Lines to the given writers, in the working directory, on the clock. */
    public static Io of(Consumer<String> out, Consumer<String> err) {
        return new Io(out, err, Path.of("").toAbsolutePath(), Instant::now);
    }

    /** This io, working relative to another directory. */
    public Io withCwd(Path other) {
        return new Io(out, err, other, now);
    }

    /** This io, telling the time from another clock. */
    public Io withNow(Supplier<Instant> other) {
        return new Io(out, err, cwd, other);
    }

    /** Write one line of what the command was asked for. */
    public void out(String line) {
        out.accept(line);
    }

    /** Write one line about what went wrong, or what to look at. */
    public void err(String line) {
        err.accept(line);
    }

    /** The directory relative paths the command writes to are taken from. */
    public Path cwd() {
        return cwd;
    }

    /** The time it is, as far as the command is concerned. */
    public Instant now() {
        return now.get();
    }
}
