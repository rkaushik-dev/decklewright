# decklewright

[![CI](https://github.com/rkaushik-dev/decklewright/actions/workflows/ci.yml/badge.svg)](https://github.com/rkaushik-dev/decklewright/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396)
![Maven](https://img.shields.io/badge/build-Maven-C71A36)
![JUnit 5](https://img.shields.io/badge/tests-JUnit%205-25A162)

Crop planning and derivative production for the oversize reformatting lab.

We photograph county atlas plates, quadrangle sheets, plat books and rolled
drawings on two copy stands and one 24x36 flatbed. Every raw capture has the
object somewhere on the bed, a degree or two out of square, next to a colour
target and a scale bar. This library turns that capture into a preservation
master, a service copy and a thumbnail that match whatever the collection
profile says, and writes a manifest saying what happened.

## Tech stack

- Java 21, built with Maven (the wrapper is checked in, so `./mvnw` is all you need)
- [picocli](https://picocli.info) 4.7 for the `deckle` entry point, and nothing
  else at run time: the png, pnm and tiff codecs and the json writer are in the
  tree, on top of the JDK
- JUnit 5 and AssertJ for the tests, JaCoCo for coverage, Spotless with
  palantir-java-format for formatting

## Building and testing

    ./mvnw verify            compile, run every test, check formatting, enforce 80% line coverage
    ./mvnw spotless:apply    format the sources
    ./mvnw test -Dtest='io/github/rkaushikdev/decklewright/plan/**'    one layer's tests

`verify` also packages the tool as a single runnable jar,
`target/decklewright-0.1.0-cli.jar`, with picocli inside it. The coverage
report is in `target/site/jacoco/index.html`.

## The command

    java -jar target/decklewright-0.1.0-cli.jar <command> [options]

or, with `alias deckle='java -jar /path/to/decklewright-0.1.0-cli.jar'`:

    deckle plan g4181-1907.png              where the crop would be, writes nothing
    deckle crop g4181-1907.png --out cut    cut one sheet, write the files
    deckle qc g4181-1907.png                grade it, exit non zero if it fell short
    deckle batch /lab/oversize              work everything waiting in a workspace
    deckle profile quadrangles              what a profile actually says
    deckle verify /lab/oversize             check delivered files against their digests

`deckle <command> --help` lists what a command takes, and `deckle --version`
says which build this is. Exit codes: zero did what was asked, one ran and the
answer was bad, two the command line was wrong.

The options every capture command takes are `--profile`/`-p` (a built in
profile name, or a path to a `.json` one; `house` when not given) and
`--ppi`/`-r` (the capture's sampling frequency, used over the one the profile
claims and the one read off the scale bar). Short options bundle (`-fq`), a long option takes its value
after a space or an equals sign, and a lone `--` ends the options.

## A workspace

A workspace is four folders and a ledger. Captures land in `incoming`, a run
works in `working`, and a finished delivery goes to `delivered` or `rejected`.
Nothing is ever deleted; a rejection is a folder somewhere else and a line in
`ledger.jsonl` saying why.

    /lab/oversize
      incoming/       captures off the stand
      working/        held for somebody to look at
      delivered/      g4181-1907/  the files, sidecar.json, provenance.json, sha256sums.txt
      rejected/       the ones that did not reach the level
      ledger.jsonl    one line per accession, appended

The ledger lines, sidecars, provenance chains and sum files are written in a
fixed format, with the key order and number text pinned, because they are
digested. Existing workspaces load and verify.

## The library

    import io.github.rkaushikdev.decklewright.measure.Sampling;
    import io.github.rkaushikdev.decklewright.pipeline.ProcessedIncoming;
    import io.github.rkaushikdev.decklewright.pipeline.RunOptions;
    import io.github.rkaushikdev.decklewright.pipeline.WorkspaceRunner;
    import io.github.rkaushikdev.decklewright.profile.Profile;
    import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
    import io.github.rkaushikdev.decklewright.store.Workspace;

    Workspace workspace = Workspace.open(Path.of("/lab/oversize"));
    Profile profile = new ProfileRegistry().get("quadrangles");

    ProcessedIncoming shift = WorkspaceRunner.processIncoming(
            workspace,
            profile,
            Optional.of(Sampling.of(600)),
            RunOptions.defaults().withVersion("1.4.0").withHost("stand-2"));
    Map<String, Integer> counts = shift.counts();   // {delivered=..., rejected=...}

`Decklewright.PUBLIC_NAMES` is the public surface and it is short on purpose:
the types and calls a caller needs to take a capture and end up with a
delivery, plus the errors it has to catch. Every other public type is public so
the layers can use one another, and reaching for one is fine as long as you
accept that those names can move. Every deliberate failure is a `DeckleError`
subclass carrying a stable `ErrorCode` and a map of details.

## Layout

    src/main/java/io/github/rkaushikdev/decklewright/
      measure       units, lengths, sampling frequencies, tolerances
      geom          points, rects, quads, angles, matrices
      raster        surfaces, views, resampling, statistics (ops: crop, pad, rotate, resample)
      codec         png, pnm and tiff, in and out
      detect        finding the sheet, the target, the neatline, the bar
      profile       collection profiles, inheritance, migrations
      plan          the crop rules and the engine that settles them
      qc            the checks, the ladders and the star level
      derive        rendering, encoding and naming the derivatives
      manifest      fixity, the sidecar, the provenance chain, the delivery
      store         the only layer that knows a disk exists
      pipeline      the stages, the graph, the policy and the runner
      cli           the argument parser, the commands and the deckle entry point
      util          guards, number text, json, blank space, timestamps
    src/test/java/  JUnit 5, mirrors src/main; testing/ holds the shared fixtures

Everything above `store` is a pure function of its inputs, which is why the
tests do not need a temporary directory until they get there.

## Profiles

Five ship: `house` and four that extend it for atlas plates, quadrangle sheets,
rolled drawings and microfilm. A profile says where to crop from, how much
bleed to keep, what derivatives to write, and what the quality floors are. A
local one is json that names its parent:

    { "name": "plat-books", "extends": "atlas-plates", "version": 2,
      "crop": { "bleed": "4mm" }, "qc": { "minimumStars": 4 } }

    deckle batch /lab/oversize --profile ./plat-books.json

`deckle profile ./plat-books.json` resolves it, prints what it means and
reports anything in it that contradicts itself. `deckle profile --paths` lists
every setting a profile can carry, and `deckle profile quadrangles -s
qc.minimumStars` shows one.
