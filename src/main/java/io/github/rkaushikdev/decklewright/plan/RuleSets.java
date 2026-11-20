package io.github.rkaushikdev.decklewright.plan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.plan.rules.AnchorRules;
import io.github.rkaushikdev.decklewright.plan.rules.AspectRules;
import io.github.rkaushikdev.decklewright.plan.rules.BleedMode;
import io.github.rkaushikdev.decklewright.plan.rules.BleedRules;
import io.github.rkaushikdev.decklewright.plan.rules.BoundsMode;
import io.github.rkaushikdev.decklewright.plan.rules.BoundsRules;
import io.github.rkaushikdev.decklewright.plan.rules.Landmarks;
import io.github.rkaushikdev.decklewright.plan.rules.ResolutionRules;
import io.github.rkaushikdev.decklewright.plan.rules.Rule;
import io.github.rkaushikdev.decklewright.plan.rules.Rules;
import io.github.rkaushikdev.decklewright.plan.rules.SafeAreaRules;
import io.github.rkaushikdev.decklewright.plan.rules.SnapRules;
import io.github.rkaushikdev.decklewright.profile.CropOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Building the rule set a profile asks for.
 *
 * <p>The profile says what it wants in its own words and this turns that into rules. It is the only
 * place that knows which rule goes with which setting, so adding a setting means editing one file
 * and adding a rule means editing the same one.
 *
 * <p>A set always ends with {@code notEmpty}, whatever the profile said. There is no collection
 * anywhere that wants a crop with no area.
 *
 * <p>This replaces the planner that ran the same steps in a fixed order. That one was easier to read
 * and it could not be made right: it had to choose between honouring an aspect and honouring a snap,
 * and both collections that use them are still here.
 */
public final class RuleSets {
    /**
     * Rules that are known to argue with each other. They are allowed to be in the same set, because
     * the engine settles most of them, but the pairing is worth naming so an explanation can point at
     * it.
     */
    public static final List<Tension> KNOWN_TENSIONS = List.of(
            new Tension(
                    List.of("aspect", "snap"),
                    "a forced aspect and a snap pull against each other and the engine settles them"),
            new Tension(List.of("bleed", "safeArea"), "a bleed pushes out and a safe area pulls in on the same edge"),
            new Tension(
                    List.of("bleed", "bounds"),
                    "a bleed can push the crop off the capture, which the bounds rule then answers"),
            new Tension(List.of("aspect", "bounds"), "a forced aspect can push the crop off the capture"));

    private RuleSets() {}

    /**
     * The set the profile asks for, with the default options.
     *
     * @see #rulesFor(CropOptions, Landmarks, PlanOptions)
     */
    public static List<Rule> rulesFor(CropOptions crop, Landmarks found) {
        return rulesFor(crop, found, PlanOptions.defaults());
    }

    /**
     * The set the profile asks for: the anchor, a bleed and a safe area when the profile has them, an
     * aspect when it set one, a snap when it snaps to more than one pixel, then the rounding, the
     * bounds, the sampling floor and {@code notEmpty}. Under the clamp bounds mode the bleed is
     * trimmed at the capture too.
     *
     * @param crop the crop options, already in pixels
     * @param found what the detector found, in the frame the plan is drawn in
     * @param options the fall back, the bounds mode and the strictness of the floor; the pass limit is
     *     not used here
     * @throws PlanError when the profile crops to the neatline, none was found and falling back was not
     *     allowed
     * @throws BadArgumentError when a setting cannot make a rule
     */
    public static List<Rule> rulesFor(CropOptions crop, Landmarks found, PlanOptions options) {
        BoundsMode boundsMode = options.boundsMode();
        List<Rule> rules = new ArrayList<>();
        rules.add(
                options.fallBackToSheet()
                        ? AnchorRules.anchorOrSheetRule(crop.anchor(), found)
                        : AnchorRules.anchorFor(crop.anchor(), found));

        if (!crop.bleed().isZero()) {
            rules.add(BleedRules.bleedFor(
                    crop.bleed(), boundsMode == BoundsMode.CLAMP ? BleedMode.TRIMMED : BleedMode.PLAIN));
        }
        if (!crop.safeArea().isZero()) {
            rules.add(SafeAreaRules.safeAreaFor(crop.safeArea()));
        }

        AspectRules.aspectFor(crop.aspect(), crop.aspectDirection()).ifPresent(rules::add);

        if (crop.snapTo() > 1) {
            rules.add(SnapRules.snapFor(crop.snapTo(), crop.snapDirection()));
        }
        rules.add(SnapRules.roundRule(crop.rounding()));
        rules.add(BoundsRules.boundsFor(boundsMode));
        rules.add(ResolutionRules.resolutionFor(crop.minimumPpi(), options.strictResolution()));
        rules.add(BoundsRules.notEmptyRule());

        Rules.requireNoDuplicates(rules);
        return List.copyOf(rules);
    }

    /** The {@linkplain #KNOWN_TENSIONS known tensions} whose rules are all in the set. */
    public static List<Tension> tensionsIn(List<Rule> rules) {
        Set<String> present = rules.stream().map(Rule::name).collect(Collectors.toSet());
        return KNOWN_TENSIONS.stream()
                .filter(tension -> present.containsAll(tension.between()))
                .toList();
    }

    /** The set in the order it runs, one line per rule, such as {@code grid: snap}. */
    public static List<String> describeSet(List<Rule> rules) {
        return Rules.ordered(rules).stream()
                .map(item -> item.phase().label() + ": " + item.name())
                .toList();
    }

    /**
     * The set the house style produces, which is what most collections run and what the regression
     * corpus is measured against: an overhang is warned about, not answered.
     */
    public static List<Rule> houseRules(CropOptions crop, Landmarks found) {
        return rulesFor(crop, found, PlanOptions.defaults().withBoundsMode(BoundsMode.WARN));
    }
}
