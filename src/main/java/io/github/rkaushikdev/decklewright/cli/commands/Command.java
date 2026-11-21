package io.github.rkaushikdev.decklewright.cli.commands;

import io.github.rkaushikdev.decklewright.cli.OptionSpec;
import io.github.rkaushikdev.decklewright.cli.ParsedArgs;
import java.util.Map;

/**
 * One thing the tool does. A command owns its own options and its own help, so the help for a
 * command cannot drift from what the command actually accepts, because both come off the same
 * object.
 *
 * <p>The exit codes are the same for every command, because a lab will put this in a cron job.
 * Zero means it did what it was asked. One means it ran and the answer was bad: a capture that
 * failed its grading, a delivery that did not verify. Two means the command line itself was wrong,
 * which is a different thing and wants a different alert.
 */
public interface Command {

    /** The usage line, such as {@code deckle crop <capture> [options]}. */
    String usage();

    /** What it does, in one line, for the help. */
    String describe();

    /** The options it understands, by long name, in the order the help lists them. */
    Map<String, OptionSpec> options();

    /**
     * Do it.
     *
     * @param args the command line, read against {@link #options()}
     * @param sink where the lines go
     * @return the exit code
     */
    int run(ParsedArgs args, Io sink);
}
