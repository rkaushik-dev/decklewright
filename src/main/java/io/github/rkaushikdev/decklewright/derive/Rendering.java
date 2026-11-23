package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PipelineError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.profile.DerivativeRecipe;
import io.github.rkaushikdev.decklewright.raster.Conversions;
import io.github.rkaushikdev.decklewright.raster.Fill;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.ops.Crops;
import io.github.rkaushikdev.decklewright.raster.ops.PaddedCrop;
import io.github.rkaushikdev.decklewright.raster.ops.Reduction;
import io.github.rkaushikdev.decklewright.raster.ops.ResampleOptions;
import io.github.rkaushikdev.decklewright.raster.ops.Resampling;
import io.github.rkaushikdev.decklewright.raster.ops.RotateOptions;
import io.github.rkaushikdev.decklewright.raster.ops.Rotation;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Turning a plan into pixels.
 *
 * <p>The plan says where the sheet is and how far off square it sits. This turns that into the files
 * a repository keeps, and it does the two expensive parts once: the capture is straightened once and
 * cut once, and every derivative comes off that cut rather than off the raw capture. A thumbnail
 * rendered from the raw capture would be straightened and cropped again with different rounding, and
 * a thumbnail that does not line up with its master is worse than no thumbnail.
 *
 * <p>The frequency travels with the pixels. A service copy at half the linear size is at half the
 * frequency, and if that is not carried through then the sidecar claims a resolution the file does
 * not have. That is the one error in this whole tree a reader cannot catch by looking at the file.
 */
public final class Rendering {

    /** A reduction below this share of the linear size halves its way down before the final pass. */
    private static final double HALVING_THRESHOLD = 0.5;

    private Rendering() {}

    /**
     * The turn taken out of the capture, straightened bilinearly over black.
     *
     * @see #straighten(Surface, CropPlan, CutOptions)
     */
    public static Surface straighten(Surface surface, CropPlan plan) {
        return straighten(surface, plan, CutOptions.defaults());
    }

    /**
     * The turn taken out of the capture, on a canvas grown so no corner is lost, or the very surface
     * handed in when the plan has no turn to take out.
     */
    public static Surface straighten(Surface surface, CropPlan plan, CutOptions options) {
        if (plan.deskew().isZero()) {
            return surface;
        }
        RotateOptions rotate = RotateOptions.defaults()
                .withSample(options.sample())
                .withBackground(options.fill())
                .withExpand(true);
        return Rotation.deskew(surface, plan.deskew(), rotate).surface();
    }

    /**
     * The plan's rect cut out of a straightened capture, with black past its edge.
     *
     * @see #cutOut(Surface, CropPlan, Fill)
     */
    public static PaddedCrop cutOut(Surface surface, CropPlan plan) {
        return cutOut(surface, plan, Fill.uniform(0));
    }

    /**
     * The plan's rect cut out of a straightened capture, rounded outward.
     *
     * <p>A crop that reaches past the edge is padded rather than clipped. Clipping would hand back a
     * file of the wrong size, and every measurement written beside it is in the units of the plan, so
     * all of them would then be describing a rectangle nobody has.
     *
     * @param fill what the part of the crop past the edge is filled with
     */
    public static PaddedCrop cutOut(Surface surface, CropPlan plan, Fill fill) {
        return Crops.cropPadded(surface, plan.rect(), fill);
    }

    /**
     * Straightens and cuts in one call, bilinearly over black.
     *
     * @see #cutMaster(Surface, CropPlan, CutOptions)
     */
    public static MasterCut cutMaster(Surface surface, CropPlan plan) {
        return cutMaster(surface, plan, CutOptions.defaults());
    }

    /**
     * Straightens and cuts in one call, which is what every derivative starts from.
     *
     * <p>The straightened capture is checked against the frame the plan was written in before
     * anything is cut. The two can only disagree if the plan came off a different capture, and a
     * crop taken at plan coordinates out of the wrong frame gives a plausible file of the right size
     * showing the wrong part of the sheet. That is the one failure here nobody would catch by
     * looking.
     *
     * @throws PipelineError when the straightened capture is not the size of the plan's frame
     */
    public static MasterCut cutMaster(Surface surface, CropPlan plan, CutOptions options) {
        List<String> steps = new ArrayList<>();
        Surface straightened = straighten(surface, plan, options);
        if (straightened != surface) {
            steps.add("straightened by " + NumberText.fixed(plan.deskew().degrees(), 3) + " degrees");
        }

        Rect frame = straightened.bounds();
        if (frame.width() != plan.capture().width()
                || frame.height() != plan.capture().height()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("plan", extent(plan.capture().width(), plan.capture().height()));
            details.put("capture", extent(frame.width(), frame.height()));
            throw new PipelineError("this plan was made against a capture of another size", details);
        }

        PaddedCrop cut = cutOut(straightened, plan, options.fill());
        Surface out = cut.surface();
        steps.add("cut " + extent(out.width(), out.height()) + " out of " + extent(surface.width(), surface.height()));
        if (cut.filled()) {
            steps.add("padded, the crop reaches " + NumberText.of(Num.roundHalfUp(plan.overhangArea()))
                    + " px past the capture");
        }
        return new MasterCut(out, steps, cut.filled());
    }

    /**
     * The size a recipe asks for, with no sampling frequency to work a ppi out against.
     *
     * @see #targetSize(Size, DerivativeRecipe, Optional)
     */
    public static Optional<Size> targetSize(Size from, DerivativeRecipe recipe) {
        return targetSize(from, recipe, Optional.empty());
    }

    /**
     * The size a recipe asks for, or empty when it asks for the size it was given.
     *
     * <p>A longest side wins over a scale and a scale over a ppi, and neither a longest side nor a
     * ppi is ever reached by enlarging. A ppi is worked out against the coarser axis of the master,
     * so neither axis comes out under it. A scale of one asks for no change, and any of the three at
     * zero, like one left out, asks for nothing. The size is rounded to whole pixels, halves up.
     *
     * @param from the size the recipe starts from
     * @param sampling the master's frequency, which a ppi is set against
     * @throws BadArgumentError when the recipe asks for a ppi and there is no frequency to set it
     *     against
     */
    public static Optional<Size> targetSize(Size from, DerivativeRecipe recipe, Optional<Sampling> sampling) {
        OptionalDouble longestSide = asked(recipe.longestSide());
        if (longestSide.isPresent()) {
            double longest = Math.max(from.width(), from.height());
            return reduced(from, longestSide.getAsDouble() / longest);
        }
        OptionalDouble scale = asked(recipe.scale());
        if (scale.isPresent()) {
            return scale.getAsDouble() == 1
                    ? Optional.empty()
                    : Optional.of(from.scaled(scale.getAsDouble()).rounded());
        }
        OptionalDouble ppi = asked(recipe.ppi());
        if (ppi.isPresent()) {
            Sampling master = sampling.orElseThrow(() -> new BadArgumentError(
                    "the " + recipe.name() + " derivative asks for a ppi and the master has no frequency",
                    Map.of("derivative", recipe.name())));
            return reduced(from, ppi.getAsDouble() / Math.min(master.x(), master.y()));
        }
        return Optional.empty();
    }

    /** The size at the factor when it is a reduction, else nothing. */
    private static Optional<Size> reduced(Size from, double factor) {
        return factor >= 1 ? Optional.empty() : Optional.of(from.scaled(factor).rounded());
    }

    /**
     * Renders one derivative off the cut master.
     *
     * <p>The order is layout, then size, then depth. Layout first because dropping two channels
     * before resampling is a third of the work. Depth last because reducing to eight bits and then
     * resampling rounds twice, and the rounding shows up as banding in exactly the flat margins an
     * oversize sheet is mostly made of. A reduction to less than half the linear size halves its way
     * down first. A depth of zero, like a layout the master already has, asks for no change.
     *
     * @param sampling the frequency of the master, which the derivative's is scaled from
     * @throws PipelineError when the recipe has no name
     * @throws BadArgumentError when the recipe names a kernel there is none of, or a size that
     *     rounds to nothing
     */
    public static Rendered renderDerivative(Surface master, DerivativeRecipe recipe, Sampling sampling) {
        if (recipe.name().isEmpty()) {
            throw new PipelineError("a derivative recipe has no name");
        }

        List<String> steps = new ArrayList<>();
        Surface surface = master;

        if (recipe.layout() != surface.layout()) {
            surface = Conversions.toLayout(surface, recipe.layout());
            steps.add("to " + recipe.layout().label());
        }

        Optional<Size> wanted = targetSize(surface.size(), recipe, Optional.of(sampling));
        double scale = 1;
        if (wanted.isPresent()) {
            Size size = wanted.get();
            scale = size.width() / surface.width();
            String kernel = recipe.kernel();
            ResampleOptions options = ResampleOptions.defaults().withKernel(kernel);
            String resized = "to " + extent(size.width(), size.height()) + " with " + kernel;
            if (scale < HALVING_THRESHOLD) {
                Reduction reduced = Resampling.reduceInSteps(surface, size, options);
                surface = reduced.surface();
                steps.add(resized + " after " + reduced.halvings() + " halvings");
            } else {
                surface = Resampling.resample(surface, size, options);
                steps.add(resized);
            }
        }

        if (recipe.depth() != 0 && recipe.depth() != surface.depth()) {
            surface = Conversions.toDepth(surface, recipe.depth());
            steps.add("to " + recipe.depth() + " bit");
        }

        return new Rendered(recipe.name(), surface, sampling.scaled(scale), steps, scale);
    }

    /**
     * Renders every derivative a profile asks for off one cut, straightened bilinearly over black.
     *
     * @see #renderAll(Surface, CropPlan, List, CutOptions)
     */
    public static RenderedSet renderAll(Surface surface, CropPlan plan, List<DerivativeRecipe> recipes) {
        return renderAll(surface, plan, recipes, CutOptions.defaults());
    }

    /**
     * Renders every derivative a profile asks for off one cut, each at the plan's frequency scaled
     * by its own reduction.
     *
     * @param surface the raw capture
     * @param options how the capture is straightened and cut; the reductions use each recipe's kernel
     * @throws PipelineError when there are no recipes, two recipes share a name, or the plan was made
     *     against a capture of another size
     */
    public static RenderedSet renderAll(
            Surface surface, CropPlan plan, List<DerivativeRecipe> recipes, CutOptions options) {
        if (recipes.isEmpty()) {
            throw new PipelineError("the profile asks for no derivatives");
        }

        Set<String> seen = new HashSet<>();
        for (DerivativeRecipe recipe : recipes) {
            if (!seen.add(recipe.name())) {
                throw new PipelineError("two derivatives want the same name", Map.of("name", recipe.name()));
            }
        }

        MasterCut cut = cutMaster(surface, plan, options);
        List<Rendered> rendered = recipes.stream()
                .map(recipe -> renderDerivative(cut.surface(), recipe, plan.sampling()))
                .toList();
        return new RenderedSet(cut.surface(), cut.steps(), cut.filled(), rendered);
    }

    /**
     * The derivative rendered under that name.
     *
     * @throws PipelineError when none was
     */
    public static Rendered named(List<Rendered> rendered, String name) {
        return rendered.stream()
                .filter(item -> item.name().equals(name))
                .findFirst()
                .orElseThrow(
                        () -> new PipelineError("no derivative was rendered under that name", Map.of("name", name)));
    }

    /** A size setting the recipe really makes: present, and neither zero nor NaN, which ask for nothing. */
    private static OptionalDouble asked(OptionalDouble setting) {
        if (setting.isEmpty() || setting.getAsDouble() == 0 || Double.isNaN(setting.getAsDouble())) {
            return OptionalDouble.empty();
        }
        return setting;
    }

    /** Two extents as the steps and the errors print them, such as {@code 160 by 120}. */
    private static String extent(double width, double height) {
        return NumberText.of(width) + " by " + NumberText.of(height);
    }
}
