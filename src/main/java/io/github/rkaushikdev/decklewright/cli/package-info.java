/**
 * The {@code deckle} command line: the front door that dispatches to a command, and the small
 * parser every command reads its options with.
 *
 * <p>{@link io.github.rkaushikdev.decklewright.cli.Main} is the program. It takes the words after
 * the program name, prints the help or the version when asked, and otherwise reads the rest of the
 * line against the named command's options with {@link io.github.rkaushikdev.decklewright.cli.Args}
 * and runs it. The commands themselves are in {@code cli.commands}.
 */
package io.github.rkaushikdev.decklewright.cli;
