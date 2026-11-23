package io.github.rkaushikdev.decklewright.cli.commands;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.cli.Args;
import io.github.rkaushikdev.decklewright.cli.OptionSpec;
import io.github.rkaushikdev.decklewright.cli.ParsedArgs;
import io.github.rkaushikdev.decklewright.profile.Diagnostics;
import io.github.rkaushikdev.decklewright.profile.Fields;
import io.github.rkaushikdev.decklewright.profile.Migrations;
import io.github.rkaushikdev.decklewright.profile.Problem;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.profile.Validation;
import io.github.rkaushikdev.decklewright.qc.Ladders;
import io.github.rkaushikdev.decklewright.qc.QcRunner;
import io.github.rkaushikdev.decklewright.util.Json;
import io.github.rkaushikdev.decklewright.util.NumberText;
import io.github.rkaushikdev.decklewright.util.Whitespace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Look at a profile without running anything through it.
 *
 * <p>Three questions get asked about a profile and they want different answers. What profiles are
 * there. What does this one actually say once its parents are folded in. And is this one somebody
 * wrote by hand going to work. The third is the one that matters, because a profile with a typo in
 * a field name does not fail, it silently keeps the inherited value.
 */
public final class ProfileCommand implements Command {

    private static final Map<String, OptionSpec> OPTIONS = Args.spec(
            Map.entry("json", OptionSpec.flag("write it as json").withLetter('j')),
            Map.entry(
                    "setting",
                    OptionSpec.string("show one setting rather than all of them")
                            .withLetter('s')),
            Map.entry("paths", OptionSpec.flag("list every setting a profile can carry")),
            Map.entry(
                    "ladders",
                    OptionSpec.flag("set each quality limit beside the ladder its check grades on")
                            .withLetter('l')));

    @Override
    public String usage() {
        return "deckle profile [name] [options]";
    }

    @Override
    public String describe() {
        return "list the profiles, or show and check one";
    }

    @Override
    public Map<String, OptionSpec> options() {
        return OPTIONS;
    }

    /**
     * List every setting path, list the shipped profiles, show one setting, set a profile's quality
     * limits beside the ladders, or show a profile and check it, which includes noting the limits
     * looser than the level it asks for.
     *
     * @return one when the profile could not be read, carries no such setting, or has a fault,
     *     otherwise zero; a profile with only notes against it is zero
     */
    @Override
    public int run(ParsedArgs args, Io sink) {
        Optional<String> name = args.positionalAt(0);

        try {
            if (args.flag("paths")) {
                Fields.settingPaths().forEach(sink::out);
                return 0;
            }

            if (name.isEmpty()) {
                return listProfiles(args.flag("json"), sink);
            }

            Profile profile = Common.profileNamed(name.get());

            Optional<String> setting = args.string("setting").filter(path -> !path.isEmpty());
            if (setting.isPresent()) {
                return showSetting(profile, setting.get(), sink);
            }

            if (args.flag("ladders")) {
                Ladders.describeLadders(profile).forEach(sink::out);
                return 0;
            }

            if (args.flag("json")) {
                sink.out(Json.stringify(profile, 2));
            } else {
                Diagnostics.describeProfile(profile).forEach(sink::out);
            }

            Map<String, Object> document = profile.toJson();
            if (Migrations.needsMigration(document)) {
                sink.err("this profile is version " + NumberText.of(Migrations.versionOf(document))
                        + " and would be brought forward:");
                for (String note : Migrations.migrationNotes(document)) {
                    sink.err("  " + note);
                }
            }

            List<Problem> problems = new ArrayList<>(Validation.inspectProfile(profile));
            problems.addAll(QcRunner.unrunRequired(profile));
            problems.addAll(Ladders.unreachableLevels(profile));
            Validation.describeProblems(problems).forEach(sink::err);
            return Validation.faultsOnly(problems).isEmpty() ? 0 : 1;
        } catch (DeckleError caught) {
            return Common.report(sink, caught);
        }
    }

    /** Every shipped profile, one to a line with its title, or their names as a json list. */
    private static int listProfiles(boolean json, Io sink) {
        ProfileRegistry registry = new ProfileRegistry();
        if (json) {
            sink.out(Json.stringify(new ArrayList<Object>(registry.names()), 2));
            return 0;
        }
        for (String one : registry.names()) {
            sink.out(trimEnd(one + "  " + registry.get(one).title()));
        }
        return 0;
    }

    /** One setting on its own, or a refusal when the profile carries none at the path. */
    private static int showSetting(Profile profile, String path, Io sink) {
        Optional<Object> value = Fields.settingAt(profile, path);
        if (value.isEmpty()) {
            sink.err("this profile carries no setting called " + path);
            return 1;
        }
        sink.out(settingText(value.get()));
        return 0;
    }

    /** A setting on its own: a group or a list as indented json, anything else as its text. */
    private static String settingText(Object value) {
        return switch (value) {
            case Map<?, ?> group -> Json.stringify(group, 2);
            case List<?> list -> Json.stringify(list, 2);
            case Number number -> NumberText.of(number);
            default -> String.valueOf(value);
        };
    }

    /** The text without the blank space at its end. */
    private static String trimEnd(String text) {
        int end = text.length();
        while (end > 0 && Whitespace.isBlank(text.charAt(end - 1))) {
            end -= 1;
        }
        return text.substring(0, end);
    }
}
