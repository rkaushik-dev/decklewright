package io.github.rkaushikdev.decklewright.store;

import io.github.rkaushikdev.decklewright.StoreError;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The folders a workspace has, in the order a run uses them.
 *
 * <p>Captures land in incoming. A run picks one up and works in working, and the finished delivery
 * goes to delivered or, if it did not grade well enough, to rejected. Each folder is named on disk
 * by its label.
 */
public enum Folder implements JsonValue {
    /** Where captures wait to be worked. */
    INCOMING("incoming"),
    /** Where a run works, and what {@link Workspace#tidy()} empties. */
    WORKING("working"),
    /** Where a delivery that graded well enough goes. */
    DELIVERED("delivered"),
    /** Where one that did not goes, to be looked at rather than removed. */
    REJECTED("rejected");

    private final String label;

    Folder(String label) {
        this.label = label;
    }

    /** The folder's name on disk, such as {@code delivered}. */
    public String label() {
        return label;
    }

    /**
     * The folder of that name.
     *
     * @throws StoreError when a workspace has no folder by that name, the root and the ledger
     *     included; the details name it and list the folders there are
     */
    public static Folder parse(String name) {
        return named(name).orElseThrow(() -> {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("folder", name);
            details.put("known", Arrays.stream(values()).map(Folder::label).collect(Collectors.joining(", ")));
            return new StoreError("a workspace has no folder by that name", details);
        });
    }

    /** The folder of that name, matched exactly; empty for any other name. */
    public static Optional<Folder> named(String name) {
        return Arrays.stream(values())
                .filter(folder -> folder.label.equals(name))
                .findFirst();
    }

    /** The label, which is how a folder is written in a report. */
    @Override
    public String toJson() {
        return label;
    }

    /** The label, such as {@code rejected}. */
    @Override
    public String toString() {
        return label;
    }
}
