package io.github.rkaushikdev.decklewright.cli;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.cli.commands.BatchCommand;
import io.github.rkaushikdev.decklewright.cli.commands.Command;
import io.github.rkaushikdev.decklewright.cli.commands.Common;
import io.github.rkaushikdev.decklewright.cli.commands.CropCommand;
import io.github.rkaushikdev.decklewright.cli.commands.Io;
import io.github.rkaushikdev.decklewright.cli.commands.PlanCommand;
import io.github.rkaushikdev.decklewright.cli.commands.ProfileCommand;
import io.github.rkaushikdev.decklewright.cli.commands.QcCommand;
import io.github.rkaushikdev.decklewright.cli.commands.VerifyCommand;
import io.github.rkaushikdev.decklewright.manifest.DeliveryOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * The front door of the {@code deckle} tool.
 *
 * <p>It dispatches and nothing else. Every command owns its own options and its own help, so
 * adding one means writing one class and adding one line here, and the help for a command cannot
 * drift from what the command actually accepts because both come off the same object.
 *
 * <p>picocli is the outer layer: it receives the process arguments and hands every word, unchanged
 * and in order, to {@link #run(List, Io, String)}, which reads them with {@link Args}. Nothing is
 * declared to picocli as an option, a lone {@code --} is not taken as its own marker, and an
 * {@code @file} word is not expanded, so a command sees exactly what was typed.
 *
 * <p>The exit codes are worth stating because a lab will put this in a cron job. Zero means it did
 * what it was asked. One means it ran and the answer was bad: a capture that failed its grading, a
 * delivery that did not verify. Two means the command line itself was wrong, which is a different
 * thing and wants a different alert.
 */
@CommandLine.Command(name = Main.PROGRAM, description = "crop and derivative production for oversize sheets")
public final class Main implements Callable<Integer> {

    /** What the tool answers to. */
    public static final String PROGRAM = "deckle";

    /** Every command, by name, in the order the help lists them. */
    public static final Map<String, Command> COMMANDS = commands();

    /**
     * What picocli is told marks the end of its options: a character no command line holds, so a lone
     * {@code --} reaches {@link Args} like any other word.
     */
    private static final String NO_DELIMITER = "\0";

    private static final List<String> HELP_WORDS = List.of("--help", "-h", "help");

    /** Every word on the command line, which picocli fills in before {@link #call()}. */
    @CommandLine.Parameters(arity = "0..*", paramLabel = "<word>", description = "a command and what it takes")
    private List<String> words = new ArrayList<>();

    private final Io sink;
    private final String version;

    private Main(Io sink, String version) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.version = Objects.requireNonNull(version, "version");
    }

    /** Run the tool against the terminal and exit with its code. */
    public static void main(String[] args) {
        System.exit(commandLine(Io.system(), version()).execute(args));
    }

    /**
     * The tool as picocli runs it, with its lines going to the given io and the given version
     * reported. {@link CommandLine#execute(String...)} returns the exit code.
     */
    public static CommandLine commandLine(Io sink, String version) {
        return new CommandLine(new Main(sink, version))
                .setUnmatchedOptionsArePositionalParams(true)
                .setStopAtPositional(true)
                .setExpandAtFiles(false)
                .setEndOfOptionsDelimiter(NO_DELIMITER);
    }

    /** Hands the words picocli collected to {@link #run(List, Io, String)}. */
    @Override
    public Integer call() {
        return run(words, sink, version);
    }

    /**
     * The version this build was packaged as, read off the jar; {@code 0.0.0} when it was not run
     * from one.
     */
    public static String version() {
        String packaged = Main.class.getPackage().getImplementationVersion();
        return packaged == null ? DeliveryOptions.UNKNOWN_VERSION : packaged;
    }

    /**
     * Names close enough to a mistyped command to be worth suggesting: every command that starts the
     * same way or that the typed name starts with, otherwise every command sharing all but two of
     * its letters with it, and at least two.
     */
    public static List<String> nearestCommands(String given) {
        List<String> names = List.copyOf(COMMANDS.keySet());
        List<String> exact = names.stream()
                .filter(name -> name.startsWith(given) || given.startsWith(name))
                .toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return names.stream()
                .filter(name -> {
                    long shared = name.codePoints()
                            .filter(letter -> given.indexOf(letter) >= 0)
                            .count();
                    return shared >= Math.max(2, name.length() - 2);
                })
                .toList();
    }

    /**
     * The help with no version in its first line.
     *
     * @see #help(String)
     */
    public static List<String> help() {
        return help("");
    }

    /** The help: the tool and its version, the usage line, and every command with what it does. */
    public static List<String> help(String version) {
        int width = COMMANDS.keySet().stream().mapToInt(String::length).max().orElse(0);
        List<String> lines = new ArrayList<>();
        lines.add(PROGRAM + (version.isEmpty() ? "" : " " + version)
                + ", crop and derivative production for oversize sheets");
        lines.add("");
        lines.add("usage: " + PROGRAM + " <command> [options]");
        lines.add("");
        COMMANDS.forEach((name, command) -> lines.add("  " + padEnd(name, width) + "  " + command.describe()));
        lines.add("");
        lines.add("try " + PROGRAM + " <command> --help for what a command takes");
        return List.copyOf(lines);
    }

    /** The help for one command: its usage, what it does, and its options. */
    public static List<String> helpFor(String name) {
        Command command = COMMANDS.get(name);
        List<String> lines = new ArrayList<>(List.of(command.usage(), "", command.describe(), ""));
        lines.addAll(Args.describeOptions(command.options()));
        return List.copyOf(lines);
    }

    /**
     * Run one command line.
     *
     * <p>No words, or a first word asking for help, prints the help; a first word asking for the
     * version prints it. A command asked for {@code --help} or {@code -h} anywhere prints its own
     * help. Otherwise the rest of the line is read against the command's options and the command is
     * run, with the program's version in place of a {@code --version} option left at {@code 0.0.0}.
     *
     * @param argv what came after the program name
     * @param sink where the lines go
     * @param version what the program reports as its version
     * @return two for no words, an unknown command or a command line its command refuses, otherwise
     *     what the command returned, or one when it threw
     */
    public static int run(List<String> argv, Io sink, String version) {
        if (argv.isEmpty() || HELP_WORDS.contains(argv.get(0))) {
            help(version).forEach(sink::out);
            return argv.isEmpty() ? 2 : 0;
        }
        String name = argv.get(0);
        List<String> rest = argv.subList(1, argv.size());

        if (name.equals("--version") || name.equals("-V")) {
            sink.out(version);
            return 0;
        }

        Command command = COMMANDS.get(name);
        if (command == null) {
            sink.err("there is no command called " + name);
            List<String> near = nearestCommands(name);
            if (near.isEmpty()) {
                sink.err("  the commands are " + String.join(", ", COMMANDS.keySet()));
            } else {
                sink.err("  did you mean " + String.join(" or ", near) + "?");
            }
            return 2;
        }

        if (rest.contains("--help") || rest.contains("-h")) {
            helpFor(name).forEach(sink::out);
            return 0;
        }

        ParsedArgs parsed;
        try {
            parsed = Args.parseArgs(rest, command.options());
        } catch (BadArgumentError refused) {
            Common.report(sink, refused);
            sink.err("  try " + PROGRAM + " " + name + " --help");
            return 2;
        }

        if (command.options().containsKey("version")
                && DeliveryOptions.UNKNOWN_VERSION.equals(parsed.options().get("version"))) {
            parsed = parsed.with("version", version);
        }

        try {
            return command.run(parsed, sink);
        } catch (DeckleError caught) {
            return Common.report(sink, caught);
        }
    }

    private static Map<String, Command> commands() {
        Map<String, Command> out = new LinkedHashMap<>();
        out.put("plan", new PlanCommand());
        out.put("crop", new CropCommand());
        out.put("qc", new QcCommand());
        out.put("batch", new BatchCommand());
        out.put("profile", new ProfileCommand());
        out.put("verify", new VerifyCommand());
        return Collections.unmodifiableMap(out);
    }

    private static String padEnd(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }
}
