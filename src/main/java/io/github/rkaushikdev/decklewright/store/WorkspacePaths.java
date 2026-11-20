package io.github.rkaushikdev.decklewright.store;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Every path a workspace has, absolute and normalised, built once.
 *
 * <p>These are names, not files: a name for a place that does not exist is still a useful thing to
 * hold.
 *
 * @param root the workspace itself
 * @param incoming where captures wait
 * @param working where a run works
 * @param delivered where deliveries go
 * @param rejected where rejections go
 * @param ledger the ledger, at the root because it is about the workspace itself
 * @see StorePaths#workspacePaths(Path)
 */
public record WorkspacePaths(Path root, Path incoming, Path working, Path delivered, Path rejected, Path ledger) {

    /** Paths of the given parts. */
    public WorkspacePaths {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(working, "working");
        Objects.requireNonNull(delivered, "delivered");
        Objects.requireNonNull(rejected, "rejected");
        Objects.requireNonNull(ledger, "ledger");
    }

    /** The path of one of the four folders. */
    public Path folder(Folder folder) {
        return switch (folder) {
            case INCOMING -> incoming;
            case WORKING -> working;
            case DELIVERED -> delivered;
            case REJECTED -> rejected;
        };
    }
}
