package io.github.rkaushikdev.decklewright.cli.commands;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.cli.Args;
import io.github.rkaushikdev.decklewright.cli.OptionSpec;
import io.github.rkaushikdev.decklewright.cli.ParsedArgs;
import io.github.rkaushikdev.decklewright.store.DeliveryCheck;
import io.github.rkaushikdev.decklewright.store.Folder;
import io.github.rkaushikdev.decklewright.store.Workspace;
import io.github.rkaushikdev.decklewright.util.Json;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Check what is on the disk against what the sidecars say.
 *
 * <p>This is the command that runs on a schedule and is read by nobody until the morning it says
 * something. So it says as little as possible when everything is fine and names every file when it
 * is not, and it never stops at the first bad one. A sweep that reports one problem out of forty is
 * worse than useless because it gets fixed and rerun and reports the next one.
 */
public final class VerifyCommand implements Command {

    private static final Map<String, OptionSpec> OPTIONS = Args.spec(
            Map.entry(
                    "folder",
                    OptionSpec.string("which folder to sweep").withLetter('d').withFallback(Folder.DELIVERED.label())),
            Map.entry("json", OptionSpec.flag("write the result as json").withLetter('j')),
            Map.entry(
                    "quiet",
                    OptionSpec.flag("say nothing about the ones that are fine").withLetter('q')));

    @Override
    public String usage() {
        return "deckle verify <workspace> [accession ...]";
    }

    @Override
    public String describe() {
        return "check delivered files against the digests beside them";
    }

    @Override
    public Map<String, OptionSpec> options() {
        return OPTIONS;
    }

    /**
     * Check every delivery in a folder, or the ones named, and print each problem.
     *
     * @return two when no workspace was named, one when it could not be swept or any delivery has a
     *     problem, otherwise zero
     */
    @Override
    public int run(ParsedArgs args, Io sink) {
        Optional<String> root = args.positionalAt(0);
        if (root.isEmpty()) {
            sink.err("which workspace? try: deckle verify /lab/oversize");
            return 2;
        }
        List<String> named = args.positional().subList(1, args.positional().size());

        try {
            Workspace workspace = Workspace.at(Path.of(root.get()));
            Folder folder = Folder.parse(args.string("folder").orElseThrow());
            List<String> accessions = named.isEmpty() ? workspace.list(folder) : named;

            if (accessions.isEmpty()) {
                sink.out("nothing in " + folder.label());
                return 0;
            }

            List<DeliveryCheck> results = new ArrayList<>();
            for (String accession : accessions) {
                results.add(workspace.verify(accession, folder));
            }

            List<DeliveryCheck> bad = results.stream().filter(one -> !one.ok()).toList();
            int checked = results.stream().mapToInt(DeliveryCheck::checked).sum();

            if (args.flag("json")) {
                Map<String, Object> json = new LinkedHashMap<>();
                json.put("folder", folder.label());
                json.put("accessions", (double) results.size());
                json.put("files", (double) checked);
                json.put("ok", bad.isEmpty());
                json.put("problems", new ArrayList<Object>(bad));
                sink.out(Json.stringify(json, 2));
                return bad.isEmpty() ? 0 : 1;
            }

            if (!args.flag("quiet")) {
                for (DeliveryCheck one : results) {
                    if (one.ok()) {
                        sink.out(one.accession() + ": " + one.checked() + " files, all as delivered");
                    }
                }
            }
            for (DeliveryCheck one : bad) {
                sink.err(one.accession() + ":");
                for (String problem : one.problems()) {
                    sink.err("  " + problem);
                }
            }

            sink.out(results.size() + " accessions, " + checked + " files, " + bad.size() + " with problems");
            return bad.isEmpty() ? 0 : 1;
        } catch (DeckleError caught) {
            return Common.report(sink, caught);
        }
    }
}
