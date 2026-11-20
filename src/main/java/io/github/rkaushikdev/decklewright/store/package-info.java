/**
 * The store: the only layer that knows a disk exists.
 *
 * <p>Everything above it hands down bytes and names, which is why the rest of the tree can be
 * tested without a temporary directory, and why a lab can point this at a share that behaves
 * nothing like a local disk without changing anything else.
 *
 * <p>A {@link io.github.rkaushikdev.decklewright.store.Workspace} is four folders under one root,
 * incoming, working, delivered and rejected, with an append-only {@code ledger.jsonl} beside them.
 * {@link io.github.rkaushikdev.decklewright.store.Dispatch} holds the calls a run makes: {@code
 * deliver} writes a delivery and records it, {@code recordFailure} records a run that produced
 * nothing, and {@code shiftReport} counts what the ledger holds. Below that, {@link
 * io.github.rkaushikdev.decklewright.store.AtomicFiles} writes without leaving half a file behind,
 * for a lab that keeps its own layout, {@link io.github.rkaushikdev.decklewright.store.StorePaths}
 * builds every path the same way twice and refuses one that would leave the workspace, and {@link
 * io.github.rkaushikdev.decklewright.store.Ledger} reads and writes the ledger.
 *
 * <p>A file system failure surfaces as a {@link io.github.rkaushikdev.decklewright.StoreError}
 * saying what could not be done and where, with the failure itself as its cause.
 */
package io.github.rkaushikdev.decklewright.store;
