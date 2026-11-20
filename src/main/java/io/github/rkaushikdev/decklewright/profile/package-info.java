/**
 * Collection profiles as the rest of the tree sees them.
 *
 * <p>A collection profile is a json document a curator writes: which edge a sheet is cropped to,
 * how much paper is kept, which derivatives are written and where the quality bar sits. A stage
 * takes a resolved {@link io.github.rkaushikdev.decklewright.profile.Profile} or one of the option
 * records {@link io.github.rkaushikdev.decklewright.profile.Settings} makes from it. Nothing above
 * this layer reads a written document, and nothing below it knows profiles exist.
 *
 * <p>How a written document becomes a profile:
 *
 * <ul>
 *   <li>{@link io.github.rkaushikdev.decklewright.profile.Migrations} brings an old document forward
 *       to the current version.
 *   <li>{@link io.github.rkaushikdev.decklewright.profile.Overlays} flattens its {@code extends}
 *       chain, groups merging deep and lists replacing.
 *   <li>{@link io.github.rkaushikdev.decklewright.profile.Fields} reads it strictly against the
 *       declared fields, using the small schema language in {@link
 *       io.github.rkaushikdev.decklewright.profile.Schema}.
 *   <li>{@link io.github.rkaushikdev.decklewright.profile.Validation} finds the contradictions
 *       between settings, and {@link io.github.rkaushikdev.decklewright.profile.Diagnostics} says in
 *       plain words what the profile will do.
 * </ul>
 *
 * <p>{@link io.github.rkaushikdev.decklewright.profile.ProfileRegistry} holds the shipped profiles in
 * {@link io.github.rkaushikdev.decklewright.profile.BuiltIn} and a site's own, and resolves them by
 * name; {@link io.github.rkaushikdev.decklewright.profile.ProfileLoader} does the whole journey for
 * a document read off a disk.
 *
 * <p>Written documents are plain data, exactly as {@link
 * io.github.rkaushikdev.decklewright.util.Json#parse(String)} gives it: maps with string keys in key
 * order, lists, strings, numbers, booleans and nulls, with a missing key for a setting that is not
 * written. A setting that will not read is refused with a {@link
 * io.github.rkaushikdev.decklewright.ProfileError} whose message names the path it went wrong at.
 */
package io.github.rkaushikdev.decklewright.profile;
