/**
 * The manifest layer: a finished run turned into a delivery that is ready to be written.
 *
 * <p>One call, {@link io.github.rkaushikdev.decklewright.manifest.Deliveries#deliveryFor}, builds
 * the whole delivery from a {@link io.github.rkaushikdev.decklewright.manifest.FinishedRun} and
 * refuses to hand back one that does not check out against itself. The delivery is a {@link
 * io.github.rkaushikdev.decklewright.manifest.Payload}: the derivative files, the sidecar that
 * describes them, the provenance chain, and a plain checksum file a tape robot can read without
 * knowing anything about the rest of it.
 *
 * <p>The pieces are public on their own, because a repository that already has its own sidecar
 * format wants the fixity and the provenance without ours: {@link
 * io.github.rkaushikdev.decklewright.manifest.Checksums} keeps the digests and the sum file, {@link
 * io.github.rkaushikdev.decklewright.manifest.Sidecars} writes and reads the sidecar, {@link
 * io.github.rkaushikdev.decklewright.manifest.Provenance} keeps the chain of events, and {@link
 * io.github.rkaushikdev.decklewright.manifest.Payloads} assembles and checks the set of files.
 *
 * <p>Every text this layer produces is written to disk and digested, so each is written in one
 * fixed form, byte for byte: keys in a fixed order, two space indentation, numbers in their
 * shortest round-trip text, timestamps in UTC with milliseconds, and the trailing newlines each
 * file has always had. Files written in that form by earlier releases read back unchanged.
 *
 * <p>Like the derivative layer, nothing here touches a disk; the store does that. A delivery that
 * does not agree with itself is a {@link io.github.rkaushikdev.decklewright.ManifestError}.
 */
package io.github.rkaushikdev.decklewright.manifest;
