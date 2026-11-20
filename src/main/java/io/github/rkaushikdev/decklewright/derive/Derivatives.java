package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PipelineError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.profile.DerivativeRecipe;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A capture, a plan and a profile in, named bytes out: the one call a stage makes.
 *
 * <p>Nothing here touches a disk. That is deliberate and it is the reason the whole layer is
 * testable without a temporary directory: a derivative is a pure function of a capture, a plan and
 * a profile, and anything that is not belongs in the store.
 */
public final class Derivatives {

    /** What a profile calls the file a repository preserves. */
    private static final String MASTER = "master";

    private Derivatives() {}

    /**
     * Renders, encodes and names a set at the codec's own level over black fill.
     *
     * @see #deriveFromPlan(Surface, CropPlan, Profile, String, List, DeriveOptions)
     */
    public static DerivedSet deriveFromPlan(
            Surface surface, CropPlan plan, Profile profile, String accession, List<DerivativeRecipe> recipes) {
        return deriveFromPlan(surface, plan, profile, accession, recipes, DeriveOptions.defaults());
    }

    /**
     * Renders, encodes and names a set, given a plan somebody else made.
     *
     * <p>The reading and the plan have already been done further up, and doing them again here would
     * be both slow and a second opinion nobody asked for: the plan is used exactly as given. There is
     * deliberately no call beside this one that does the whole job from a capture, because two paths
     * through the same work are two answers waiting to disagree.
     *
     * <p>Every file that keeps metadata carries provenance text naming the accession, its derivative
     * and the profile, and every file is named from the profile's pattern.
     *
     * @param surface the raw capture the plan was made against
     * @param profile the resolved profile, whose name and naming pattern are used
     * @param accession what the set is of
     * @param recipes the derivatives to make, usually every one the profile asks for
     * @throws PipelineError when there are no recipes, two share a name, the plan was made against a
     *     capture of another size, or the names would collide or lack an extension; the last carries
     *     the accession and the profile name
     * @throws UnsupportedError when a recipe's format cannot hold what it is handed
     * @throws BadArgumentError when the accession is empty or a recipe names an unknown kernel
     */
    public static DerivedSet deriveFromPlan(
            Surface surface,
            CropPlan plan,
            Profile profile,
            String accession,
            List<DerivativeRecipe> recipes,
            DeriveOptions options) {
        RenderedSet rendered = Rendering.renderAll(
                surface, plan, recipes, CutOptions.defaults().withFill(options.fill()));
        List<Encoded> encoded = Recipes.encodeAll(
                rendered.rendered(),
                recipes,
                new EncodeAllOptions(Optional.of(accession), Optional.of(profile.name()), options.level()));

        Map<String, String> names = FileNames.namesFor(profile, accession, encoded);
        List<String> objections = FileNames.objectionsToNames(names);
        if (!objections.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("accession", accession);
            details.put("profile", profile.name());
            throw new PipelineError(objections.get(0), details);
        }

        List<DerivedFile> files = encoded.stream()
                .map(item -> new DerivedFile(
                        item.name(),
                        names.get(item.name()),
                        item.bytes(),
                        item.format(),
                        item.rendered().sampling(),
                        item.notes()))
                .toList();
        List<String> steps = new ArrayList<>(rendered.cut());
        for (Encoded item : encoded) {
            for (String step : item.rendered().steps()) {
                steps.add(item.name() + ": " + step);
            }
        }
        return new DerivedSet(files, steps, Recipes.sizeOf(encoded).total());
    }

    /**
     * The file a caller means when it says the master, without having to know that the profile calls
     * it that: the one named {@code master}, or else the first.
     *
     * @throws PipelineError when the set is empty, rather than handing back nothing
     */
    public static DerivedFile preservationCopy(List<DerivedFile> files) {
        if (files.isEmpty()) {
            throw new PipelineError("nothing was derived");
        }
        return files.stream()
                .filter(file -> file.name().equals(MASTER))
                .findFirst()
                .orElse(files.get(0));
    }
}
