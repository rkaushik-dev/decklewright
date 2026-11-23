package io.github.rkaushikdev.decklewright.cli.commands;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.cli.Args;
import io.github.rkaushikdev.decklewright.cli.OptionSpec;
import io.github.rkaushikdev.decklewright.codec.Codecs;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.pipeline.StandardStages;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileLoader;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.store.AtomicFiles;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Json;
import io.github.rkaushikdev.decklewright.util.JsonSyntaxException;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/** The parts every command needs: the shared options, finding a profile and a capture, and saying what went wrong. */
public final class Common {

    /** The options every command that touches a capture understands. */
    public static final Map<String, OptionSpec> CAPTURE_OPTIONS = Args.spec(
            Map.entry(
                    "profile",
                    OptionSpec.string("a built in profile name, or a path to one")
                            .withLetter('p')
                            .withFallback("house")),
            Map.entry(
                    "ppi",
                    OptionSpec.number("the sampling frequency of the capture").withLetter('r')));

    private static final long KILO = 1024;

    private Common() {}

    /**
     * A profile by name, or read off a disk if it looks like a path.
     *
     * <p>The two are told apart by whether the name ends in {@code .json}, not by trying one and
     * falling back to the other. A fallback would turn a typed path into the house profile and quietly
     * crop four hundred sheets to the wrong rules.
     *
     * @throws BadArgumentError when no shipped profile has the name, with every known name in the
     *     details, or the file will not read as a json object
     * @throws io.github.rkaushikdev.decklewright.ProfileError when the profile in the file cannot be
     *     used
     */
    public static Profile profileNamed(String name) {
        ProfileRegistry registry = new ProfileRegistry();
        if (!name.endsWith(".json")) {
            if (!registry.has(name)) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("profile", name);
                details.put("known", String.join(", ", registry.names()));
                throw new BadArgumentError("there is no profile by that name", details);
            }
            return registry.get(name);
        }

        Object document;
        try {
            document = Json.parse(AtomicFiles.readText(Path.of(name)));
        } catch (DeckleError | JsonSyntaxException | InvalidPathException cause) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("path", name);
            details.put("cause", cause);
            throw new BadArgumentError("that profile will not read as json", details, cause);
        }
        return ProfileLoader.loadProfile(Guards.requirePlainObject(document, "profile"), registry);
    }

    /**
     * A capture off a disk, named for its file without the extension.
     *
     * @throws io.github.rkaushikdev.decklewright.StoreError when it could not be read
     */
    public static CaptureFile captureAt(String path) {
        Path file = Path.of(path);
        byte[] bytes = AtomicFiles.readBytes(file);
        Path name = file.getFileName();
        return new CaptureFile(path, stem(name == null ? "" : name.toString()), bytes);
    }

    /**
     * A capture off a disk, decoded and read with what the profile's capture section tells the
     * detector, which is what a batch run under the same profile would have read.
     *
     * @throws io.github.rkaushikdev.decklewright.StoreError when it could not be read
     * @throws io.github.rkaushikdev.decklewright.CodecError when it will not decode
     */
    public static ReadCapture readCaptureAt(String path, Profile profile) {
        CaptureFile file = captureAt(path);
        Surface surface = Codecs.readImage(file.bytes(), path);
        return new ReadCapture(file, surface, Captures.readCapture(surface, StandardStages.captureOptionsFor(profile)));
    }

    /**
     * The frequency a capture is planned against: the one the caller gave, otherwise the one the
     * profile says the scanner captures at, otherwise none, so the reading's own is used.
     */
    public static Optional<Sampling> samplingFrom(OptionalDouble ppi, Profile profile) {
        if (ppi.isPresent()) {
            return Optional.of(Sampling.of(ppi.getAsDouble()));
        }
        double claimed = profile.capture().claimedPpi();
        return claimed != 0 && !Double.isNaN(claimed) ? Optional.of(Sampling.of(claimed)) : Optional.empty();
    }

    /**
     * Print an error the way a terminal wants one: the sentence, then the details indented under it,
     * each as {@code key: value}, leaving out the cause. A stack trace is for a bug report and not for
     * an operator who typed a path wrong.
     *
     * @return the exit code, which is one
     */
    public static int report(Io sink, DeckleError caught) {
        sink.err(caught.getMessage() == null ? caught.toString() : caught.getMessage());
        caught.details().forEach((key, value) -> {
            if (!key.equals("cause")) {
                sink.err("  " + key + ": " + text(value));
            }
        });
        return 1;
    }

    /** A size a person reads without counting digits, such as {@code 900 B}, {@code 2.0 kB} or {@code 5.0 MB}. */
    public static String humanBytes(long value) {
        if (value < KILO) {
            return value + " B";
        }
        if (value < KILO * KILO) {
            return NumberText.fixed(value / (double) KILO, 1) + " kB";
        }
        return NumberText.fixed(value / (double) (KILO * KILO), 1) + " MB";
    }

    /**
     * A detail value as a report line shows it: a string as it is, a number as reports write one, a
     * list as its items joined by commas with a missing item left blank, and an object as compact
     * json.
     */
    static String text(Object value) {
        return switch (value) {
            case null -> "null";
            case String text -> text;
            case Number number -> NumberText.of(number);
            case Boolean flag -> flag.toString();
            case List<?> list ->
                list.stream().map(item -> item == null ? "" : text(item)).collect(Collectors.joining(","));
            case Map<?, ?> map -> Json.stringify(map);
            default -> value.toString();
        };
    }

    /** A file name without its last extension; a name whose only dot leads it has none. */
    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
