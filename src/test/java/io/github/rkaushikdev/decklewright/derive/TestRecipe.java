package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.profile.DerivativeRecipe;
import io.github.rkaushikdev.decklewright.profile.OutputFormat;
import io.github.rkaushikdev.decklewright.raster.Layout;
import java.util.OptionalDouble;

/**
 * A derivative recipe under construction, for tests that state only the parts they are about.
 *
 * <p>Whatever a test leaves out takes the value a profile falls back on: png, no resizing, eight
 * bit rgb, Catmull Rom, and no metadata. Against the eight bit rgb captures the tests cut, those
 * ask the render for no change.
 */
record TestRecipe(
        String name,
        OutputFormat format,
        OptionalDouble longestSide,
        OptionalDouble scale,
        OptionalDouble ppi,
        int depth,
        Layout layout,
        String kernel,
        boolean keepMetadata) {

    /** A recipe of that name with every other part a profile's fallback. */
    static TestRecipe named(String name) {
        return new TestRecipe(
                name,
                OutputFormat.PNG,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                8,
                Layout.RGB,
                "catmullRom",
                false);
    }

    /** A recipe with an empty name, which is the closest a recipe can come to having none. */
    static TestRecipe unnamed() {
        return named("");
    }

    /** A copy of a finished recipe, to change one part of. */
    static TestRecipe of(DerivativeRecipe recipe) {
        return new TestRecipe(
                recipe.name(),
                recipe.format(),
                recipe.longestSide(),
                recipe.scale(),
                recipe.ppi(),
                recipe.depth(),
                recipe.layout(),
                recipe.kernel(),
                recipe.keepMetadata());
    }

    TestRecipe withName(String value) {
        return new TestRecipe(value, format, longestSide, scale, ppi, depth, layout, kernel, keepMetadata);
    }

    TestRecipe withFormat(OutputFormat value) {
        return new TestRecipe(name, value, longestSide, scale, ppi, depth, layout, kernel, keepMetadata);
    }

    TestRecipe withLongestSide(double value) {
        return new TestRecipe(name, format, OptionalDouble.of(value), scale, ppi, depth, layout, kernel, keepMetadata);
    }

    TestRecipe withScale(double value) {
        return new TestRecipe(
                name, format, longestSide, OptionalDouble.of(value), ppi, depth, layout, kernel, keepMetadata);
    }

    TestRecipe withPpi(double value) {
        return new TestRecipe(
                name, format, longestSide, scale, OptionalDouble.of(value), depth, layout, kernel, keepMetadata);
    }

    TestRecipe withDepth(int value) {
        return new TestRecipe(name, format, longestSide, scale, ppi, value, layout, kernel, keepMetadata);
    }

    TestRecipe withLayout(Layout value) {
        return new TestRecipe(name, format, longestSide, scale, ppi, depth, value, kernel, keepMetadata);
    }

    TestRecipe withKernel(String value) {
        return new TestRecipe(name, format, longestSide, scale, ppi, depth, layout, value, keepMetadata);
    }

    TestRecipe withKeepMetadata(boolean value) {
        return new TestRecipe(name, format, longestSide, scale, ppi, depth, layout, kernel, value);
    }

    /** The finished recipe. */
    DerivativeRecipe recipe() {
        return new DerivativeRecipe(name, format, longestSide, scale, ppi, depth, layout, kernel, keepMetadata);
    }
}
