package io.github.rkaushikdev.decklewright.detect;

/**
 * How the four sides of a mask are scanned and fitted.
 *
 * @param step scan every nth line
 * @param margin the share of each side's run dropped from both ends before fitting, because the
 *     scan crosses a corner at each end
 * @param drop the share of points the robust fit discards on each pass
 * @param passes how many times the robust fit trims and refits
 * @see Edges#edgeLines(Mask, EdgeOptions)
 */
public record EdgeOptions(int step, double margin, double drop, int passes) {
    /** Every line scanned, a fifth trimmed from each end, and three passes dropping fifteen per cent. */
    public static EdgeOptions defaults() {
        return new EdgeOptions(1, 0.2, 0.15, 3);
    }

    /** These options, scanning every nth line. */
    public EdgeOptions withStep(int step) {
        return new EdgeOptions(step, margin, drop, passes);
    }

    /** These options with another end margin. */
    public EdgeOptions withMargin(double margin) {
        return new EdgeOptions(step, margin, drop, passes);
    }

    /** These options, discarding another share each pass. */
    public EdgeOptions withDrop(double drop) {
        return new EdgeOptions(step, margin, drop, passes);
    }

    /** These options with another number of passes. */
    public EdgeOptions withPasses(int passes) {
        return new EdgeOptions(step, margin, drop, passes);
    }
}
