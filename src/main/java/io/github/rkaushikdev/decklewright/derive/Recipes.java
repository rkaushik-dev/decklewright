package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.codec.Codecs;
import io.github.rkaushikdev.decklewright.codec.ImageFormat;
import io.github.rkaushikdev.decklewright.codec.WriteOptions;
import io.github.rkaushikdev.decklewright.codec.png.Decoding;
import io.github.rkaushikdev.decklewright.codec.png.Physical;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.DerivativeRecipe;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Raster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writing a rendered derivative out as bytes.
 *
 * <p>The recipe names a format and this is where that name turns into a file. It is also where the
 * lab finds out that the format it asked for cannot hold what it is being handed, and it finds out
 * here rather than three stages later when a reader opens the file and gets a sixteen bit image read
 * as eight.
 *
 * <p>Two things are carried into the file itself rather than only into the sidecar: the sampling
 * frequency and the provenance text. Everything else a repository knows about a file it can keep
 * beside the file, but a frequency that is not in the file cannot be recovered from the pixels, and
 * a scan of a thirty six inch sheet at an unknown frequency is a picture of a map rather than a
 * measurement of one.
 */
public final class Recipes {

    /**
     * What each format can hold, by the name a recipe gives it, so a recipe can be refused before it
     * is run. Read only, all the way down.
     */
    public static final Map<String, FormatLimits> FORMAT_LIMITS = formatLimits();

    /** What a derivative is written as when the recipe does not say. */
    public static final ImageFormat DEFAULT_FORMAT = ImageFormat.PNG;

    /** Png keeps its frequency in pixels per metre, which nothing else here uses. */
    private static final double METRES_PER_INCH = 0.0254;

    /** The unit code a png frequency chunk uses for pixels per metre. */
    private static final int PER_METRE = 1;

    private Recipes() {}

    /**
     * Reads the frequency back out of written bytes.
     *
     * <p>This is the other half of the reason the frequency goes into the file: a file that carries
     * it can be checked against the sidecar years later by somebody who has both and trusts neither.
     *
     * @return the frequency; empty for a format that cannot carry one, or a png that does not, which
     *     is a different answer from zero
     * @throws io.github.rkaushikdev.decklewright.CodecError when the bytes are a png that cannot be
     *     read
     */
    public static Optional<Sampling> frequencyOf(byte[] bytes) {
        if (!isPng(bytes)) {
            return Optional.empty();
        }
        Optional<Physical> physical = Decoding.decodePng(bytes).physical();
        if (physical.isEmpty() || physical.get().unit() != PER_METRE) {
            return Optional.empty();
        }
        return Optional.of(new Sampling(
                physical.get().x() * METRES_PER_INCH, physical.get().y() * METRES_PER_INCH));
    }

    /**
     * Reads the provenance text back out of written bytes.
     *
     * @return every text chunk, key to value in the order the file holds them, read only; empty for
     *     a format that carries no text
     * @throws io.github.rkaushikdev.decklewright.CodecError when the bytes are a png that cannot be
     *     read
     */
    public static Map<String, String> textOf(byte[] bytes) {
        if (!isPng(bytes)) {
            return Map.of();
        }
        return Decoding.decodePng(bytes).text();
    }

    /**
     * What the named format can hold.
     *
     * @param format the name a recipe gives the format, such as {@code png}
     * @throws UnsupportedError when nothing here knows a format of that name
     */
    public static FormatLimits limitsFor(String format) {
        FormatLimits limits = FORMAT_LIMITS.get(format);
        if (limits == null) {
            throw new UnsupportedError("nothing here writes that format", Collections.singletonMap("format", format));
        }
        return limits;
    }

    /**
     * Whether a format can be written at all, as against merely being understood. Reading tiff and
     * writing it are different jobs and only one of them is done here, so a profile that asks for a
     * tiff master has to hear about it.
     *
     * @param format the name of the format, in any case
     * @return false for a format that is only read, and for a name nothing knows
     */
    public static boolean isWritable(String format) {
        try {
            return Codecs.codecNamed(format).isWritable();
        } catch (DeckleError unknown) {
            return false;
        }
    }

    /**
     * Everything wrong with writing this raster in this format, in the order a person would want to
     * fix them. A format that cannot be written at all is the only objection, because nothing else
     * about it matters.
     *
     * @param format the name a recipe gives the format, such as {@code png}
     * @return what is wrong; empty when it will go out cleanly
     * @throws UnsupportedError when the format can be written but nothing here knows its limits
     */
    public static List<String> objectionsTo(Raster raster, String format) {
        if (!isWritable(format)) {
            return List.of("nothing here writes " + format + ", only reads it");
        }
        FormatLimits limits = limitsFor(format);
        List<String> out = new ArrayList<>();
        if (!limits.layouts().contains(raster.layout())) {
            out.add(format + " cannot hold " + raster.layout().label());
        }
        if (!limits.depths().contains(raster.depth())) {
            out.add(format + " cannot hold " + raster.depth() + " bit samples");
        }
        return List.copyOf(out);
    }

    /**
     * The text a file carries about where it came from: the software and the accession.
     *
     * @see #provenanceText(String, ProvenanceOptions)
     */
    public static Map<String, String> provenanceText(String accession) {
        return provenanceText(accession, ProvenanceOptions.defaults());
    }

    /**
     * The text a file carries about where it came from.
     *
     * <p>It is deliberately short. A repository that wants the full record reads the sidecar, and a
     * text block long enough to hold the full record is a text block somebody will eventually strip
     * to save space, taking the short version with it.
     *
     * @return {@code Software} and {@code Source} always, then {@code Title} when there is a
     *     derivative and {@code Comment} when there is a profile, an empty name counting as none; in
     *     that order, read only
     */
    public static Map<String, String> provenanceText(String accession, ProvenanceOptions options) {
        Map<String, String> text = new LinkedHashMap<>();
        text.put("Software", options.software());
        text.put("Source", accession);
        options.derivative()
                .filter(derivative -> !derivative.isEmpty())
                .ifPresent(derivative -> text.put("Title", accession + " " + derivative));
        options.profile()
                .filter(profile -> !profile.isEmpty())
                .ifPresent(profile -> text.put("Comment", "profile " + profile));
        return Collections.unmodifiableMap(text);
    }

    /**
     * Writes one rendered derivative out as a png, carrying no text.
     *
     * @see #encodeRendered(Rendered, EncodeOptions)
     */
    public static Encoded encodeRendered(Rendered rendered) {
        return encodeRendered(rendered, EncodeOptions.defaults());
    }

    /**
     * Writes one rendered derivative out.
     *
     * <p>The rendered frequency goes into every format that can carry one, whether or not the
     * metadata is kept, and a note says so for a format that cannot. The text goes in only when the
     * metadata is kept, and a note says when it was left out.
     *
     * @throws UnsupportedError when the format cannot be written, or cannot hold the rendered
     *     layout or depth; the message is the first objection
     */
    public static Encoded encodeRendered(Rendered rendered, EncodeOptions options) {
        ImageFormat chosen = options.format().orElse(DEFAULT_FORMAT);
        List<String> objections = objectionsTo(rendered.surface(), chosen.label());
        if (!objections.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("format", chosen.label());
            details.put("derivative", rendered.name());
            details.put("layout", rendered.surface().layout().label());
            details.put("depth", rendered.surface().depth());
            throw new UnsupportedError(objections.get(0), details);
        }

        List<String> notes = new ArrayList<>();
        FormatLimits limits = limitsFor(chosen.label());

        WriteOptions write = WriteOptions.defaults();
        if (limits.carriesFrequency()) {
            write = write.withPpi(rendered.sampling());
        } else {
            notes.add(chosen.label() + " cannot carry the frequency, so it is only in the sidecar");
        }
        if (options.keepMetadata() && !options.text().isEmpty()) {
            write = write.withText(options.text());
        } else if (!options.keepMetadata()) {
            notes.add("metadata was left out, the profile asked for a bare file");
        }
        if (options.level().isPresent()) {
            write = write.withLevel(options.level().getAsInt());
        }

        byte[] bytes = Codecs.writeImage(rendered.surface(), chosen.label(), write);
        return new Encoded(rendered.name(), rendered, bytes, chosen, chosen.extension(), notes);
    }

    /**
     * Writes every rendered derivative out as its recipe asks, with no provenance text, at the codec's
     * own level.
     *
     * @see #encodeAll(List, List, EncodeAllOptions)
     */
    public static List<Encoded> encodeAll(List<Rendered> rendered, List<DerivativeRecipe> recipes) {
        return encodeAll(rendered, recipes, EncodeAllOptions.defaults());
    }

    /**
     * Writes every rendered derivative out, each in the format its own recipe asked for and keeping
     * the metadata only where its recipe says to. A rendered derivative with no recipe of its name is
     * written as a png that keeps its metadata.
     *
     * <p>A recipe that names a format nothing can write stops the whole set rather than only its own
     * file. A partial set is the worst outcome available here: the folder looks finished, the
     * manifest lists what is there, and the missing master is not noticed until somebody goes looking
     * for it years later.
     *
     * @param options with an accession, every file is given provenance text naming the accession,
     *     its derivative and the profile
     * @return one per rendered derivative, in their order
     * @throws UnsupportedError when any one of them cannot be written as its recipe asks
     */
    public static List<Encoded> encodeAll(
            List<Rendered> rendered, List<DerivativeRecipe> recipes, EncodeAllOptions options) {
        return rendered.stream()
                .map(item -> encodeRendered(item, encodingOf(item, recipes, options)))
                .toList();
    }

    /** How large the set came out, which is the number a lab plans storage on. */
    public static SetSize sizeOf(List<Encoded> encoded) {
        Map<String, Integer> perName = new LinkedHashMap<>();
        long total = 0;
        Optional<String> largest = Optional.empty();
        int most = -1;
        for (Encoded item : encoded) {
            int length = item.bytes().length;
            perName.put(item.name(), length);
            total += length;
            if (length > most) {
                most = length;
                largest = Optional.of(item.name());
            }
        }
        return new SetSize(total, largest, perName);
    }

    private static boolean isPng(byte[] bytes) {
        return Codecs.formatOf(bytes).equals(Optional.of(ImageFormat.PNG));
    }

    /** How one member of a set is written: as the recipe of its name asks, with the set's provenance. */
    private static EncodeOptions encodingOf(Rendered item, List<DerivativeRecipe> recipes, EncodeAllOptions options) {
        Optional<DerivativeRecipe> recipe =
                recipes.stream().filter(one -> one.name().equals(item.name())).findFirst();
        Map<String, String> text = options.accession()
                .filter(accession -> !accession.isEmpty())
                .map(accession -> {
                    ProvenanceOptions provenance = ProvenanceOptions.defaults().withDerivative(item.name());
                    return provenanceText(
                            accession,
                            options.profile().map(provenance::withProfile).orElse(provenance));
                })
                .orElse(Map.of());
        return new EncodeOptions(
                recipe.map(one -> ImageFormat.parse(one.format().label())),
                recipe.map(DerivativeRecipe::keepMetadata).orElse(true),
                text,
                options.level());
    }

    private static Map<String, FormatLimits> formatLimits() {
        List<Integer> depths = List.of(8, 16);
        Map<String, FormatLimits> limits = new LinkedHashMap<>();
        limits.put("png", new FormatLimits(depths, List.of(Layout.GRAY, Layout.GRAYA, Layout.RGB, Layout.RGBA), true));
        limits.put("pnm", new FormatLimits(depths, List.of(Layout.GRAY, Layout.RGB), false));
        limits.put("tiff", new FormatLimits(depths, List.of(Layout.GRAY, Layout.RGB), true));
        return Collections.unmodifiableMap(limits);
    }
}
