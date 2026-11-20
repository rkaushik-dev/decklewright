/**
 * Png: the container, the row filters, the decoder and encoder, and the container level edits that
 * strip a master down to a service copy without touching a sample.
 *
 * <p>{@link io.github.rkaushikdev.decklewright.codec.png.Chunks} splits a file into {@link
 * io.github.rkaushikdev.decklewright.codec.png.Chunk}s and assembles them again; {@link
 * io.github.rkaushikdev.decklewright.codec.png.Decoding} and {@link
 * io.github.rkaushikdev.decklewright.codec.png.Encoding} read and write the samples; {@link
 * io.github.rkaushikdev.decklewright.codec.png.Rewriting} edits the metadata and checks a file
 * against a chunk policy. The stages read and write through {@link
 * io.github.rkaushikdev.decklewright.codec.Codecs} rather than through these.
 */
package io.github.rkaushikdev.decklewright.codec.png;
