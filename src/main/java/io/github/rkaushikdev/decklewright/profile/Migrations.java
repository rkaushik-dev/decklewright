package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.ProfileError;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Bringing an old profile forward.
 *
 * <p>Profiles are written by hand and kept in the collection's own folder, so there are files on
 * this site older than most of this code. Renaming a setting therefore costs a migration, and the
 * migration has to be written at the same time as the rename or the file stops loading a year later
 * with no clue why.
 *
 * <p>A document with no version is version one. That is not a guess: version one predates the
 * field. A version is read as the double json gives, so a whole number of any size is reported as
 * it was written.
 */
public final class Migrations {

    /** The version this build writes and understands. */
    public static final int CURRENT_VERSION = 2;

    /** Keyed by the version the migration moves a document off. */
    public static final Map<Integer, Migration> MIGRATIONS = Map.of(1, Migrations::toVersionTwo);

    /** The settings version two renamed, in the order they are renamed and reported. */
    private static final List<Rename> RENAMES = List.of(
            new Rename("crop", "margin", "bleed"),
            new Rename("crop", "roundTo", "snapTo"),
            new Rename("crop", "straighten", "deskew"),
            new Rename("qc", "dpi", "samplingPpi"),
            new Rename("qc", "dpiTolerance", "samplingTolerance"));

    /** The groups {@link #RENAMES} reach into, in the same order. */
    private static final List<String> RENAMED_GROUPS = List.of("crop", "qc");

    private Migrations() {}

    /** One step forward: a new document one version on, leaving the one it was given alone. */
    @FunctionalInterface
    public interface Migration {
        /** The document brought forward one version, in a new map the caller owns. */
        Map<String, Object> apply(Map<String, ?> document);
    }

    /**
     * The version a document was written for: one when it does not say.
     *
     * @throws ProfileError when the written version is not a whole number from one upward
     */
    public static double versionOf(Map<String, ?> document) {
        if (!PlainData.has(document, "version")) {
            return 1;
        }
        Object version = document.get("version");
        if (!(version instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < 1) {
            throw new ProfileError(
                    "the version has to be a whole number from one upward",
                    Collections.singletonMap("version", version));
        }
        return number.doubleValue();
    }

    /**
     * True when the document was written for a version before this build's.
     *
     * @throws ProfileError as {@link #versionOf(Map)} does
     */
    public static boolean needsMigration(Map<String, ?> document) {
        return versionOf(document) < CURRENT_VERSION;
    }

    /**
     * The document brought forward to the current version, one step at a time.
     *
     * @return the same document when it is already current, otherwise a new one the caller owns, and
     *     the versions it was moved off, oldest first
     * @throws ProfileError when the document is from a later version than this build understands
     */
    public static Migrated migrate(Map<String, ?> document) {
        Map<String, ?> current = document;
        List<Integer> applied = new ArrayList<>();

        double version = versionOf(current);
        if (version > CURRENT_VERSION) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("version", version);
            details.put("current", CURRENT_VERSION);
            throw new ProfileError(
                    "that profile was written for version " + NumberText.of(version) + " and this build understands "
                            + CURRENT_VERSION,
                    details);
        }

        while (version < CURRENT_VERSION) {
            // Below the current version and a whole number from one, so it is a small int.
            int from = (int) version;
            Migration step = MIGRATIONS.get(from);
            if (step == null) {
                throw new ProfileError(
                        "there is no way to bring version " + from + " forward", Map.of("version", version));
            }
            current = step.apply(current);
            applied.add(from);
            double next = versionOf(current);
            if (next <= version) {
                throw new ProfileError("a migration did not move the version forward", Map.of("version", version));
            }
            version = next;
        }

        return new Migrated(current, applied);
    }

    /**
     * Every document brought forward, in order.
     *
     * @throws ProfileError as {@link #migrate(Map)} does, for the first document that cannot be
     */
    public static List<Map<String, ?>> migrateAll(List<? extends Map<String, ?>> documents) {
        List<Map<String, ?>> out = new ArrayList<>(documents.size());
        for (Map<String, ?> document : documents) {
            out.add(migrate(document).document());
        }
        return List.copyOf(out);
    }

    /**
     * What a migration would change, without changing it. The verify command prints this so a
     * curator can update their own file rather than relying on the migration for ever.
     *
     * @return nothing for a current document; otherwise the versions it would pass through, then each
     *     rename the document actually needs
     */
    public static List<String> migrationNotes(Map<String, ?> document) {
        if (!needsMigration(document)) {
            return List.of();
        }
        Migrated migrated = migrate(document);
        List<String> notes = new ArrayList<>();
        notes.add("brought forward from version " + NumberText.of(versionOf(document)) + " through "
                + migrated.applied().stream().map(String::valueOf).collect(Collectors.joining(", ")));

        for (Rename rename : RENAMES) {
            if (document.get(rename.group()) instanceof Map<?, ?> group && group.containsKey(rename.from())) {
                notes.add(rename.group() + "." + rename.from() + " is now " + rename.group() + "." + rename.to());
            }
        }
        Object written = document.get("derivatives");
        if (written instanceof List<?>) {
            if (anyDerivative(written, derivative -> derivative.containsKey("maxSide"))) {
                notes.add("derivatives[].maxSide is now longestSide");
            }
            Object after = migrated.document().get("derivatives");
            if (anyDerivative(after, derivative -> derivative.get("depth") instanceof String)
                    && anyDerivative(written, derivative -> derivative.get("depth") instanceof Number)) {
                notes.add("derivatives[].depth is written as text now");
            }
        }
        return List.copyOf(notes);
    }

    /** True when the value is a list holding a derivative that passes the test. */
    private static boolean anyDerivative(Object derivatives, Predicate<Map<?, ?>> test) {
        return derivatives instanceof List<?> list
                && list.stream().anyMatch(derivative -> derivative instanceof Map<?, ?> bag && test.test(bag));
    }

    /**
     * Version one to two: the crop and quality settings renamed, and the derivatives' {@code maxSide}
     * renamed and a numeric depth written as text. A rename onto a setting already there replaces it
     * where it stands; otherwise the new name goes last.
     */
    private static Map<String, Object> toVersionTwo(Map<String, ?> document) {
        Map<String, Object> out = PlainData.copyOf(document);
        out.put("version", 2.0);

        for (String group : RENAMED_GROUPS) {
            if (out.get(group) instanceof Map<?, ?> written) {
                Map<String, Object> settings = PlainData.copyOf(written);
                for (Rename rename : RENAMES) {
                    if (rename.group().equals(group)) {
                        rename(settings, rename.from(), rename.to());
                    }
                }
                out.put(group, settings);
            }
        }

        if (out.get("derivatives") instanceof List<?> derivatives) {
            List<Object> brought = new ArrayList<>(derivatives.size());
            for (Object derivative : derivatives) {
                brought.add(derivative instanceof Map<?, ?> written ? derivativeToVersionTwo(written) : derivative);
            }
            out.put("derivatives", brought);
        }
        return out;
    }

    private static Map<String, Object> derivativeToVersionTwo(Map<?, ?> written) {
        Map<String, Object> copy = PlainData.copyOf(written);
        rename(copy, "maxSide", "longestSide");
        if (copy.get("depth") instanceof Number depth) {
            copy.put("depth", NumberText.of(depth));
        }
        return copy;
    }

    /** The rename made in place: the value moves under the new name and the old name goes. */
    private static void rename(Map<String, Object> settings, String from, String to) {
        if (settings.containsKey(from)) {
            settings.put(to, settings.get(from));
            settings.remove(from);
        }
    }

    /**
     * A document brought forward.
     *
     * @param document the current document: the one given when it was already current
     * @param applied the versions it was moved off, oldest first; empty when nothing was done
     */
    public record Migrated(Map<String, ?> document, List<Integer> applied) {

        /** A migrated document and its steps; the steps are copied. */
        public Migrated {
            Objects.requireNonNull(document, "document");
            applied = List.copyOf(applied);
        }
    }

    /** A setting in a group that a version renamed. */
    private record Rename(String group, String from, String to) {}
}
