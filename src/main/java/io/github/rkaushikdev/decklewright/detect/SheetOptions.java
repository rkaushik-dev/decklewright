package io.github.rkaushikdev.decklewright.detect;

/**
 * How a sheet is looked for on a capture.
 *
 * @param sigma how many spreads from the bed counts as object
 * @param fraction how much of each edge is read as bed; zero means the default
 * @param speckle the radius of the opening that removes dust
 * @param gap the radius of the closing that mends a torn edge
 * @param agreement how many degrees the skew measures may differ by before confidence falls
 * @param minimumCoverage the share of the capture a sheet must fill, from zero to one
 * @see Sheets#findSheet(io.github.rkaushikdev.decklewright.raster.Surface, SheetOptions)
 */
public record SheetOptions(
        double sigma, double fraction, double speckle, double gap, double agreement, double minimumCoverage) {
    /**
     * Six spreads off the bed read from four per cent of each edge, an opening of two pixels and a
     * closing of three, a degree of agreement, and a sheet of at least two per cent of the capture.
     */
    public static SheetOptions defaults() {
        return new SheetOptions(Backgrounds.DEFAULT_SIGMA, Backgrounds.DEFAULT_FRACTION, 2, 3, 1, 0.02);
    }

    /** These options with another threshold off the bed. */
    public SheetOptions withSigma(double sigma) {
        return new SheetOptions(sigma, fraction, speckle, gap, agreement, minimumCoverage);
    }

    /** These options, reading another share of each edge as bed. */
    public SheetOptions withFraction(double fraction) {
        return new SheetOptions(sigma, fraction, speckle, gap, agreement, minimumCoverage);
    }

    /** These options with another radius for removing dust. */
    public SheetOptions withSpeckle(double speckle) {
        return new SheetOptions(sigma, fraction, speckle, gap, agreement, minimumCoverage);
    }

    /** These options with another radius for mending torn edges. */
    public SheetOptions withGap(double gap) {
        return new SheetOptions(sigma, fraction, speckle, gap, agreement, minimumCoverage);
    }

    /** These options, letting the skew measures differ by another number of degrees. */
    public SheetOptions withAgreement(double agreement) {
        return new SheetOptions(sigma, fraction, speckle, gap, agreement, minimumCoverage);
    }

    /** These options with another smallest share of the capture a sheet may take. */
    public SheetOptions withMinimumCoverage(double minimumCoverage) {
        return new SheetOptions(sigma, fraction, speckle, gap, agreement, minimumCoverage);
    }
}
