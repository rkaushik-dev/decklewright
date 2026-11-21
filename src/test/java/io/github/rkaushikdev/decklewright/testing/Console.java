package io.github.rkaushikdev.decklewright.testing;

import io.github.rkaushikdev.decklewright.cli.Args;
import io.github.rkaushikdev.decklewright.cli.commands.Command;
import io.github.rkaushikdev.decklewright.cli.commands.Io;
import io.github.rkaushikdev.decklewright.codec.png.Encoding;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * What the command line tests share: an io that collects the lines a command writes, a run of one
 * command, the standard capture as a png file, and a directory listing in name order.
 */
public final class Console {
    private final List<String> out = new ArrayList<>();
    private final List<String> err = new ArrayList<>();

    /** A console with nothing written to it yet. */
    public Console() {}

    /** An io writing into this console, in the working directory, on the clock. */
    public Io io() {
        return Io.of(out::add, err::add);
    }

    /** What was written as output, in order. */
    public List<String> out() {
        return out;
    }

    /** What was written as errors, in order. */
    public List<String> err() {
        return err;
    }

    /**
     * Read a command line against one command's options and run it, with its lines collected and the
     * io as the change makes it.
     */
    public static Ran invoke(Command command, List<String> argv, UnaryOperator<Io> change) {
        Console console = new Console();
        int code = command.run(Args.parseArgs(argv, command.options()), change.apply(console.io()));
        return new Ran(code, console.out(), console.err());
    }

    /** The same, at a fixed time. */
    public static Ran invoke(Command command, List<String> argv, Instant at) {
        return invoke(command, argv, io -> io.withNow(() -> at));
    }

    /** The standard capture off the stand, turned by the given angle, as the bytes of a png file. */
    public static byte[] capturePng(double degrees) {
        return Encoding.encodePng(Accession.capture(degrees));
    }

    /** The standard capture, square on the bed, as the bytes of a png file. */
    public static byte[] capturePng() {
        return capturePng(0);
    }

    /** Every name in a directory, in name order. */
    public static List<String> namesIn(Path directory) {
        try (Stream<Path> listed = Files.list(directory)) {
            return listed.map(path -> path.getFileName().toString()).sorted().toList();
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    /**
     * One command run.
     *
     * @param code its exit code
     * @param out its output lines
     * @param err its error lines
     */
    public record Ran(int code, List<String> out, List<String> err) {

        /** A run of the given parts; the lines are copied. */
        public Ran {
            out = List.copyOf(out);
            err = List.copyOf(err);
        }

        /** The output lines, joined by newlines. */
        public String outText() {
            return String.join("\n", out);
        }

        /** The error lines, joined by newlines. */
        public String errText() {
            return String.join("\n", err);
        }

        /** The last output line. */
        public String lastOut() {
            return out.get(out.size() - 1);
        }
    }
}
