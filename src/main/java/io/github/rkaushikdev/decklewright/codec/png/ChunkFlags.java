package io.github.rkaushikdev.decklewright.codec.png;

/**
 * What the letter cases of a chunk type say about it.
 *
 * @param critical an upper case first letter: a reader has to understand the chunk to show the image
 * @param publicChunk an upper case second letter: the chunk is part of the specification rather than
 *     private to some application
 * @param safeToCopy a lower case last letter: a rewriter that does not understand the chunk may keep it
 */
public record ChunkFlags(boolean critical, boolean publicChunk, boolean safeToCopy) {}
