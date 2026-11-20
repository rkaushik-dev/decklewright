package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.ProfileError;
import io.github.rkaushikdev.decklewright.util.Json;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Profile inheritance.
 *
 * <p>Every collection here is a variation on one of two house styles, so a profile says what it
 * extends and then only what it changes. The merge is deep for groups and flat for lists.
 *
 * <p>Lists replace rather than merge on purpose. A collection that lists two derivatives means two,
 * not two plus whatever the parent had, and the alternative was tried and produced a thumbnail
 * nobody asked for in every collection that inherited.
 *
 * <p>Everything here works on written documents, the plain data {@link Json#parse(String)} gives,
 * and hands back new maps the caller owns. A value the result shares with an input, such as a list,
 * is the input's own.
 */
public final class Overlays {

    /** How deep an extends chain may go when the caller does not say. */
    private static final int DEFAULT_LIMIT = 8;

    private Overlays() {}

    /**
     * The base with the patch written over it. A group in both is merged a level down, as deep as the
     * groups go; anything else in the patch replaces what the base had, lists included; and a setting
     * the patch writes as null is removed. Neither input is changed.
     */
    public static Map<String, Object> overlay(Map<String, ?> base, Map<String, ?> patch) {
        return merge(base, patch);
    }

    /** The documents overlaid in turn, oldest first; an empty map for none. */
    public static Map<String, Object> overlayAll(List<? extends Map<String, ?>> documents) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map<String, ?> document : documents) {
            out = merge(out, document);
        }
        return out;
    }

    /**
     * Walks the extends chain and flattens it, with a chain at most eight deep.
     *
     * @see #resolveExtends(Map, Function, int)
     */
    public static Resolved resolveExtends(
            Map<String, ?> document, Function<String, Optional<? extends Map<String, ?>>> lookup) {
        return resolveExtends(document, lookup, DEFAULT_LIMIT);
    }

    /**
     * Walks the extends chain and flattens it. The chain is followed by name and a name that is not
     * there is an error rather than a silent skip, because a typo in extends would otherwise hand back
     * the house style unchanged.
     *
     * <p>The flattened document has no {@code extends} and keeps the child's own name.
     *
     * @param lookup the written document of a profile by name, empty when there is none
     * @param limit how deep a chain may go
     * @throws ProfileError when the chain loops, goes deeper than the limit, or names a profile the
     *     lookup does not have
     */
    public static Resolved resolveExtends(
            Map<String, ?> document, Function<String, Optional<? extends Map<String, ?>>> lookup, int limit) {
        List<Map<String, ?>> stack = new ArrayList<>();
        stack.add(document);
        List<String> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String own = document.get("name") instanceof String name && !name.isEmpty() ? name : null;
        if (own != null) {
            seen.add(own);
        }

        Map<String, ?> current = document;
        while (current.get("extends") instanceof String parentName) {
            if (seen.contains(parentName)) {
                List<String> looped = new ArrayList<>(chain);
                looped.add(parentName);
                throw new ProfileError("the extends chain loops back to " + parentName, Map.of("chain", looped));
            }
            if (chain.size() >= limit) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("chain", List.copyOf(chain));
                details.put("limit", limit);
                throw new ProfileError("the extends chain is too deep", details);
            }
            Optional<? extends Map<String, ?>> parent = lookup.apply(parentName);
            if (parent.isEmpty()) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("wanted", parentName);
                details.put("chain", List.copyOf(chain));
                throw new ProfileError("there is no profile called " + parentName + " to extend", details);
            }
            seen.add(parentName);
            chain.add(parentName);
            stack.add(0, parent.get());
            current = parent.get();
        }

        Map<String, Object> flattened = overlayAll(stack);
        flattened.remove("extends");
        if (own != null) {
            flattened.put("name", own);
        }
        return new Resolved(flattened, chain);
    }

    /**
     * Which settings a child actually changed, as dotted paths. The registry prints this so a curator
     * can see what their own file is doing rather than what it inherited.
     *
     * @see #changedPaths(Map, Map, String)
     */
    public static List<String> changedPaths(Map<String, ?> base, Map<String, ?> child) {
        return changedPaths(base, child, "");
    }

    /**
     * Which settings a child actually changed, as dotted paths under the prefix. A group in both is
     * compared setting by setting; anything else, a list included, is one path that changed when its
     * json differs from the base's or the base did not have it.
     *
     * @param prefix the path the two sit at, such as {@code crop}; empty for whole documents
     */
    public static List<String> changedPaths(Map<String, ?> base, Map<String, ?> child, String prefix) {
        List<String> out = new ArrayList<>();
        collectChanges(base, child, prefix, out);
        return List.copyOf(out);
    }

    private static Map<String, Object> merge(Map<?, ?> base, Map<?, ?> patch) {
        Map<String, Object> out = new LinkedHashMap<>();
        base.forEach((key, value) -> out.put(String.valueOf(key), value));
        patch.forEach((written, value) -> {
            String key = String.valueOf(written);
            if (value == null) {
                out.remove(key);
            } else if (value instanceof Map<?, ?> group && out.get(key) instanceof Map<?, ?> under) {
                out.put(key, merge(under, group));
            } else {
                out.put(key, value);
            }
        });
        return out;
    }

    private static void collectChanges(Map<?, ?> base, Map<?, ?> child, String prefix, List<String> out) {
        child.forEach((written, value) -> {
            String key = String.valueOf(written);
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object before = base.get(key);
            if (value instanceof Map<?, ?> group && before instanceof Map<?, ?> earlier) {
                collectChanges(earlier, group, path, out);
            } else if (!base.containsKey(key) || !Json.stringify(before).equals(Json.stringify(value))) {
                out.add(path);
            }
        });
    }

    /**
     * A document with its extends chain flattened.
     *
     * @param document the flattened document, a new map the caller owns
     * @param chain the profiles it inherited from, nearest first; empty for a profile with no parent
     */
    public record Resolved(Map<String, Object> document, List<String> chain) {

        /** A flattened document and its chain; the chain is copied. */
        public Resolved {
            Objects.requireNonNull(document, "document");
            chain = List.copyOf(chain);
        }
    }
}
