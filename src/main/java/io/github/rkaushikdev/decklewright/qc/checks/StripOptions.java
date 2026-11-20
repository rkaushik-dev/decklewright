package io.github.rkaushikdev.decklewright.qc.checks;

/**
 * Where the strips across a sheet's edges are cut.
 *
 * @param reach how far either side of the edge to look, in pixels; eight by default
 * @param along how much of the edge to average over, as a share of its length; four tenths by
 *     default
 * @see Sharpness#edgeStrips(io.github.rkaushikdev.decklewright.geom.Rect, StripOptions)
 */
public record StripOptions(double reach, double along) {
    private static final StripOptions DEFAULTS = new StripOptions(8, 0.4);

    /** Eight pixels either side, over the middle four tenths of each edge. */
    public static StripOptions defaults() {
        return DEFAULTS;
    }

    /** These options looking another distance either side of the edge. */
    public StripOptions withReach(double reach) {
        return new StripOptions(reach, along);
    }

    /** These options averaging over another share of each edge. */
    public StripOptions withAlong(double along) {
        return new StripOptions(reach, along);
    }
}
