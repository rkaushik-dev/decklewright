package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.ProfileError;
import java.util.List;
import java.util.Map;

/**
 * Loading written profiles: read a document, bring it forward, flatten its chain, check it and hand
 * back what the stages take. This is the whole profile layer in one call, and it is what the cli
 * uses when somebody points at a file.
 */
public final class ProfileLoader {
    private ProfileLoader() {}

    /**
     * The resolved profile of a written document, whose parents are looked up among the shipped
     * profiles.
     *
     * @see #loadProfile(Map, ProfileRegistry)
     */
    public static Profile loadProfile(Map<String, ?> document) {
        return loadProfile(document, new ProfileRegistry());
    }

    /**
     * The resolved profile of a written document. The document is brought forward to the current
     * version and registered, replacing any profile of its name, so it stays in the registry.
     *
     * @param registry where the parents live, and where the document is added
     * @throws ProfileError when the document cannot be brought forward, has no name, will not
     *     resolve, or has a fault
     */
    public static Profile loadProfile(Map<String, ?> document, ProfileRegistry registry) {
        Map<String, ?> brought = Migrations.migrate(document).document();
        registry.add(brought);
        return registry.get((String) brought.get("name"));
    }

    /**
     * A registry of the shipped profiles with every document brought forward and added on top.
     *
     * @see #loadAll(List, ProfileRegistry)
     */
    public static ProfileRegistry loadAll(List<? extends Map<String, ?>> documents) {
        return loadAll(documents, new ProfileRegistry());
    }

    /**
     * Brings every document forward and adds it to the registry, in order. Nothing is resolved until
     * it is asked for, so a document may extend one that comes after it in the list.
     *
     * @return the same registry
     * @throws ProfileError when a document cannot be brought forward or has no name
     */
    public static ProfileRegistry loadAll(List<? extends Map<String, ?>> documents, ProfileRegistry registry) {
        for (Map<String, ?> document : documents) {
            registry.add(Migrations.migrate(document).document());
        }
        return registry;
    }
}
