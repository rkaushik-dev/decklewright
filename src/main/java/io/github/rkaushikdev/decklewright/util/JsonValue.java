package io.github.rkaushikdev.decklewright.util;

/**
 * A value that knows its own plain-data form.
 *
 * <p>Sidecars, provenance chains, ledgers and batch reports are written from plain data: maps with
 * string keys, lists, strings, numbers, booleans and nulls. A domain type that appears in one of
 * those documents implements this, and {@link Json#stringify(Object)} writes whatever {@link
 * #toJson()} returns in its place.
 */
public interface JsonValue {
    /**
     * The plain-data form of this value: a {@code Map<String, ?>}, a {@code List<?>}, a string, a
     * number, a boolean, null, or another {@link JsonValue}.
     */
    Object toJson();
}
