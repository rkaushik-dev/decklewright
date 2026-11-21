/**
 * The commands of the {@code deckle} tool, one class each: {@code plan}, {@code crop}, {@code qc},
 * {@code batch}, {@code profile} and {@code verify}, and the parts they share.
 *
 * <p>Every command is a {@link io.github.rkaushikdev.decklewright.cli.commands.Command}: a usage
 * line, a description, a table of options and a run that takes the parsed command line and an
 * {@link io.github.rkaushikdev.decklewright.cli.commands.Io}, and returns the exit code. A command
 * never prints anywhere but the io it was handed.
 */
package io.github.rkaushikdev.decklewright.cli.commands;
