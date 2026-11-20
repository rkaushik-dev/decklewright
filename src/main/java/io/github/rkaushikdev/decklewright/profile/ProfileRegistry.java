package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.ProfileError;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Where profiles live at run time.
 *
 * <p>The shipped ones are registered by default and a site adds its own on top. A site profile with
 * the name of a shipped one replaces it, which is deliberate: that is how a lab that disagrees with
 * the house style says so, once, in one place, rather than by passing options at every call.
 *
 * <p>Resolution is cached because a batch of four hundred captures asks for the same profile four
 * hundred times and the extends chain is not free. A registry holds a read only copy of each document
 * it is given, so changing a document after registering it changes nothing here. A registry is not
 * safe for use from several threads at once without outside locking.
 */
public final class ProfileRegistry {

    private final Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
    private final Map<String, Profile> resolved = new HashMap<>();

    /** A registry holding the shipped profiles. */
    public ProfileRegistry() {
        this(true);
    }

    /**
     * A registry holding the shipped profiles or nothing at all.
     *
     * @param builtIn true to register the shipped profiles
     */
    public ProfileRegistry(boolean builtIn) {
        if (builtIn) {
            for (Map<String, Object> profile : BuiltIn.BUILT_IN) {
                documents.put((String) profile.get("name"), profile);
            }
        }
    }

    /** A registry holding the shipped profiles with the given documents added on top, in order. */
    public static ProfileRegistry registryWith(List<? extends Map<String, ?>> documents) {
        return new ProfileRegistry().addAll(documents);
    }

    /**
     * Registers a written document under its name, replacing any profile of that name, and forgets
     * everything already resolved.
     *
     * @return this registry, so calls chain
     * @throws ProfileError when the document has no name, or the name is not a non empty string
     */
    public ProfileRegistry add(Map<String, ?> document) {
        if (!(document.get("name") instanceof String name) || name.isEmpty()) {
            throw new ProfileError("a profile needs a name before it can be registered", Map.of());
        }
        documents.put(name, PlainData.frozen(document));
        resolved.clear();
        return this;
    }

    /**
     * Registers each document in turn.
     *
     * @return this registry, so calls chain
     * @throws ProfileError as {@link #add(Map)} does
     */
    public ProfileRegistry addAll(List<? extends Map<String, ?>> documents) {
        for (Map<String, ?> document : documents) {
            add(document);
        }
        return this;
    }

    /** True when a profile of that name is registered. */
    public boolean has(String name) {
        return documents.containsKey(name);
    }

    /** Every registered name, in the order first registered. */
    public List<String> names() {
        return List.copyOf(documents.keySet());
    }

    /**
     * The document as written, before merging; read only.
     *
     * @throws BadArgumentError when the name is not a non empty string
     * @throws ProfileError when nothing is registered under the name, with every known name in the
     *     details
     */
    public Map<String, Object> document(String name) {
        Guards.requireNonEmptyString(name, "name");
        Map<String, Object> found = documents.get(name);
        if (found == null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("wanted", name);
            details.put("known", names());
            throw new ProfileError("there is no profile called " + name, details);
        }
        return found;
    }

    /**
     * The flattened, parsed, checked profile. This is what every other stage in the tree takes. The
     * same name gives back the same profile until the registry changes.
     *
     * @throws ProfileError when the profile is not registered, its chain will not flatten, a setting
     *     will not read, or it has a fault
     */
    public Profile get(String name) {
        Profile cached = resolved.get(name);
        if (cached != null) {
            return cached;
        }
        Profile profile = Validation.requireUsable(parsed(name));
        resolved.put(name, profile);
        return profile;
    }

    /**
     * Every problem with a profile, faults included, without refusing it.
     *
     * @throws ProfileError when the profile is not registered, its chain will not flatten, or a
     *     setting will not read
     */
    public List<Problem> inspect(String name) {
        return Validation.inspectProfile(parsed(name));
    }

    /**
     * What a profile inherited and what its own file changed, measured against its flattened parent.
     *
     * @throws ProfileError as {@link #inspect(String)} does
     */
    public Explanation explain(String name) {
        Overlays.Resolved flat = flatten(document(name));
        List<String> chain = flat.chain();
        Map<String, ?> parent =
                chain.isEmpty() ? Map.of() : flatten(document(chain.get(0))).document();
        List<String> changed = Overlays.changedPaths(parent, flat.document()).stream()
                .filter(path -> !path.equals("name"))
                .toList();
        return new Explanation(name, chain, changed, inspect(name).size());
    }

    /**
     * Check every registered profile at once, which is what the verify command does before a shift
     * starts. A profile that will not resolve at all is reported by its error's message.
     *
     * @return each profile that has a problem, by name in registration order, with each problem as
     *     {@code path: message}; read only
     */
    public Map<String, List<String>> inspectAll() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String name : names()) {
            try {
                List<Problem> problems = inspect(name);
                if (!problems.isEmpty()) {
                    out.put(
                            name,
                            problems.stream()
                                    .map(problem -> problem.path() + ": " + problem.message())
                                    .toList());
                }
            } catch (DeckleError error) {
                out.put(name, List.of(error.getMessage()));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** The named profile flattened and read, not yet checked. */
    private Profile parsed(String name) {
        return Fields.parseProfile(flatten(document(name)).document());
    }

    private Overlays.Resolved flatten(Map<String, Object> document) {
        return Overlays.resolveExtends(document, this::lookup);
    }

    private Optional<Map<String, Object>> lookup(String name) {
        return Optional.ofNullable(documents.get(name));
    }

    /**
     * What {@link #explain(String)} says about a profile.
     *
     * @param name the profile asked about
     * @param chain the profiles it inherits from, nearest first
     * @param changed the dotted paths its own file sets differently from its parent, its name aside
     * @param problems how many problems it has, faults and notes together
     */
    public record Explanation(String name, List<String> chain, List<String> changed, int problems) {

        /** An explanation of the given parts; the lists are copied. */
        public Explanation {
            Objects.requireNonNull(name, "name");
            chain = List.copyOf(chain);
            changed = List.copyOf(changed);
        }
    }
}
