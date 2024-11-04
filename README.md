# decklewright

Crop planning and derivative production for the oversize reformatting lab.

We photograph county atlas plates, quadrangle sheets, plat books and rolled
drawings on two copy stands and one 24x36 flatbed. Every raw capture has the
object somewhere on the bed, a degree or two out of square, next to a colour
target and a scale bar. This library turns that capture into a preservation
master, a service copy and a thumbnail that match whatever the collection
profile says, and writes a manifest saying what happened.

Node 20.11 or newer. No runtime dependencies.

## Layout

    src/        the library
    bin/        the deckle command
    tests/      vitest, mirrors src/

## Running things

    npm install
    npm test
    npm run lint
