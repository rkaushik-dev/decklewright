# decklewright

Crop planning and derivative production for the oversize reformatting lab.

We photograph county atlas plates, quadrangle sheets, plat books and rolled
drawings on two copy stands and one 24x36 flatbed. Every raw capture has the
object somewhere on the bed, a degree or two out of square, next to a colour
target and a scale bar. This library turns that capture into a preservation
master, a service copy and a thumbnail that match whatever the collection
profile says, and writes a manifest saying what happened.

Node 20.15 or newer. No runtime dependencies.

## The command

    deckle plan g4181-1907.png              where the crop would be, writes nothing
    deckle crop g4181-1907.png --out cut    cut one sheet, write the files
    deckle qc g4181-1907.png                grade it, exit non zero if it fell short
    deckle batch /lab/oversize              work everything waiting in a workspace
    deckle profile quadrangles              what a profile actually says
    deckle verify /lab/oversize             check delivered files against their digests

`deckle <command> --help` lists what a command takes. Exit codes: zero did what
was asked, one ran and the answer was bad, two the command line was wrong.

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

## The library

    import { Workspace, ProfileRegistry, Sampling, processIncoming } from 'decklewright';

    const workspace = await Workspace.open('/lab/oversize');
    const profile = new ProfileRegistry().get('quadrangles');

    const { counts } = await processIncoming(
      workspace,
      { profile, sampling: Sampling.of(600) },
      { version: '1.4.0', host: 'stand-2' },
    );

`src/index.js` is the public surface and it is short on purpose. Each layer has
its own barrel with everything else in it, and reaching past `src/index.js` is
fine as long as you accept that those names can move.

## Layout

    src/measure   units, lengths, sampling frequencies, tolerances
    src/geom      points, rects, quads, angles, matrices
    src/raster    surfaces, views, resampling, statistics
    src/codec     png, pnm and tiff, in and out
    src/detect    finding the sheet, the target, the neatline, the bar
    src/profile   collection profiles, inheritance, migrations
    src/plan      the crop rules and the engine that settles them
    src/qc        the checks, the ladders and the star level
    src/derive    rendering, encoding and naming the derivatives
    src/manifest  fixity, the sidecar, the provenance chain, the delivery
    src/store     the only layer that knows a disk exists
    src/pipeline  the stages, the graph, the policy and the runner
    src/cli       the argument parser and the commands
    bin/          the deckle command
    tests/        vitest, mirrors src/

Everything above `src/store` is a pure function of its inputs, which is why the
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
reports anything in it that contradicts itself.

## Running things

    npm install
    npm test
    npm run coverage
    npm run lint
    npm run format:check
