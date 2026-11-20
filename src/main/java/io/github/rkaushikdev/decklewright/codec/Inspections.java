package io.github.rkaushikdev.decklewright.codec;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Triage for the intake folder.
 *
 * <p>Half of what lands there is not what the work order says it is: a capture that was still being
 * written when the watcher saw it, a file the operator renamed by hand, a tiff from a scanner nobody
 * told us about. Throwing on the first one stops the batch. This reports on every file instead and
 * lets the pipeline decide what to hold back, so nothing here throws for a bad file.
 */
public final class Inspections {
    /** Words the codecs use when a file stops before it should. */
    private static final List<String> TRUNCATION_HINTS = List.of(
            "stops early", "stops before", "stops inside", "shorter than", "past the end", "runs past", "do not cover");

    private Inspections() {}

    /**
     * What kind of problem a failure was: unsupported when it is one of ours that says so, truncated
     * when its message says the file stops early, and unreadable otherwise.
     *
     * @param error what was caught; usually a {@link Throwable}, but any value is described
     */
    public static Problem classify(Object error) {
        String message =
                error instanceof Throwable thrown ? Objects.toString(thrown.getMessage(), "") : String.valueOf(error);
        if (error instanceof DeckleError ours && ours.code() == ErrorCode.UNSUPPORTED) {
            return new Problem(ProblemKind.UNSUPPORTED, message);
        }
        if (TRUNCATION_HINTS.stream().anyMatch(message::contains)) {
            return new Problem(ProblemKind.TRUNCATED, message);
        }
        return new Problem(ProblemKind.UNREADABLE, message);
    }

    /**
     * Look at a file without decoding the samples.
     *
     * @see #inspect(byte[], String)
     */
    public static Inspection inspect(byte[] bytes) {
        return look(bytes, Optional.empty());
    }

    /**
     * Look at a file without decoding the samples: is it empty, does anything recognise it, does its
     * name agree with its bytes, and does its header read.
     *
     * @param path the name it came in under; an empty one is not checked against the bytes
     */
    public static Inspection inspect(byte[] bytes, String path) {
        return look(bytes, Optional.of(path));
    }

    /**
     * The same look, but reading the samples too.
     *
     * @see #inspectDeep(byte[], String)
     */
    public static Inspection inspectDeep(byte[] bytes) {
        return deepen(bytes, inspect(bytes));
    }

    /**
     * The same look, but reading the samples too, which is the only way to catch a strip that decodes
     * short. A file the shallow look already rejected is not read.
     *
     * @param path the name it came in under; an empty one is not checked against the bytes
     */
    public static Inspection inspectDeep(byte[] bytes, String path) {
        return deepen(bytes, inspect(bytes, path));
    }

    /**
     * One line for a report, such as {@code q17.png: png 6x4 rgb8}, with the first note on a good
     * file, or the first problem with a bad one.
     */
    public static String describe(Inspection inspection) {
        String name = inspection.path().orElse("(no name)");
        List<Problem> problems = inspection.problems();
        if (inspection.ok() && inspection.shape().isPresent()) {
            ImageShape shape = inspection.shape().orElseThrow();
            String format = inspection.format().map(ImageFormat::label).orElse("null");
            String note = problems.isEmpty() ? "" : " (" + problems.get(0).detail() + ")";
            return name + ": " + format + " " + shape.width() + "x" + shape.height() + " " + shape.layout()
                    + shape.depth() + note;
        }
        if (problems.isEmpty()) {
            return name + ": unreadable";
        }
        Problem first = problems.get(0);
        return name + ": " + first.kind() + ", " + first.detail();
    }

    /** Every file looked at, without decoding, and split into the ones that can be worked and the rest. */
    public static Triage triage(List<IntakeFile> files) {
        List<Inspection> good = new ArrayList<>();
        List<Inspection> bad = new ArrayList<>();
        for (IntakeFile file : files) {
            Inspection result = look(file.bytes(), file.path());
            (result.ok() ? good : bad).add(result);
        }
        return new Triage(good, bad);
    }

    /** True when some codec recognises the bytes; a quick yes or no that reads no header. */
    public static boolean isReadable(byte[] bytes) {
        return Codecs.formatOf(bytes).isPresent();
    }

    private static Inspection look(byte[] bytes, Optional<String> path) {
        if (bytes.length == 0) {
            return new Inspection(
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    List.of(new Problem(ProblemKind.EMPTY, "the file holds no bytes")),
                    path);
        }

        Optional<ImageFormat> codec = Codecs.codecForBytes(bytes);
        if (codec.isEmpty()) {
            return new Inspection(
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    List.of(new Problem(ProblemKind.UNREADABLE, "nothing here recognises those bytes")),
                    path);
        }
        ImageFormat format = codec.orElseThrow();

        List<Problem> problems = new ArrayList<>();
        Optional<ImageFormat> byName = path.filter(name -> !name.isEmpty()).flatMap(Codecs::codecForPath);
        if (byName.isPresent() && byName.orElseThrow() != format) {
            problems.add(new Problem(
                    ProblemKind.WRONG_EXTENSION, "named " + byName.orElseThrow() + " but the bytes say " + format));
        }

        Optional<ImageShape> shape = Optional.empty();
        try {
            shape = Optional.of(format.probe(bytes));
        } catch (RuntimeException error) {
            // any failure at all is a finding about the file, never a reason to stop the batch
            problems.add(classify(error));
        }

        boolean ok = problems.stream().allMatch(problem -> problem.kind() == ProblemKind.WRONG_EXTENSION)
                && shape.isPresent();
        return new Inspection(ok, codec, shape, problems, path);
    }

    private static Inspection deepen(byte[] bytes, Inspection shallow) {
        if (!shallow.ok()) {
            return shallow;
        }
        try {
            shallow.format().orElseThrow().read(bytes);
        } catch (RuntimeException error) {
            // as in the shallow look: a sample that will not decode is a finding, not a crash
            List<Problem> problems = new ArrayList<>(shallow.problems());
            problems.add(classify(error));
            return new Inspection(false, shallow.format(), shallow.shape(), problems, shallow.path());
        }
        return shallow;
    }
}
