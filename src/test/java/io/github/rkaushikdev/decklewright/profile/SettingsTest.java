package io.github.rkaushikdev.decklewright.profile;

import static io.github.rkaushikdev.decklewright.profile.Documents.bag;
import static io.github.rkaushikdev.decklewright.profile.Documents.list;
import static io.github.rkaushikdev.decklewright.profile.Settings.captureOptions;
import static io.github.rkaushikdev.decklewright.profile.Settings.claimedSampling;
import static io.github.rkaushikdev.decklewright.profile.Settings.cropOptions;
import static io.github.rkaushikdev.decklewright.profile.Settings.derivativeRecipes;
import static io.github.rkaushikdev.decklewright.profile.Settings.edgesInPixels;
import static io.github.rkaushikdev.decklewright.profile.Settings.fillName;
import static io.github.rkaushikdev.decklewright.profile.Settings.qcLimits;
import static io.github.rkaushikdev.decklewright.profile.Settings.settingsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.AspectDirection;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.RoundPolicy;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.ToleranceKind;
import io.github.rkaushikdev.decklewright.measure.Unit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SettingsTest {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();
    private static final Profile HOUSE = REGISTRY.get("house");
    private static final Profile QUADS = REGISTRY.get("quadrangles");

    @Nested
    class ClaimedSampling {
        @Test
        void readsWhatTheScannerWasSetTo() {
            assertThat(claimedSampling(HOUSE).x()).isEqualTo(400);
            assertThat(claimedSampling(QUADS).x()).isEqualTo(600);
        }
    }

    @Nested
    class CaptureOptionsBehaviour {
        @Test
        void handsTheDetectionStageWhatItTakes() {
            CaptureOptions options = captureOptions(QUADS);
            assertThat(options.sigma()).isEqualTo(6);
            assertThat(options.speckle()).isEqualTo(2);
            assertThat(options.gap()).isEqualTo(3);
            assertThat(options.expectNeatline()).isTrue();
            assertThat(options.barLength()).isEqualTo(6);
            assertThat(options.barUnit()).isEqualTo(Unit.INCH);
            assertThat(options.claimed().x()).isEqualTo(600);
        }

        @Test
        void carriesACollectionThatChangedTheDefaults() {
            CaptureOptions rolled = captureOptions(REGISTRY.get("rolled-drawings"));
            assertThat(rolled.sigma()).isEqualTo(4);
            assertThat(rolled.gap()).isEqualTo(6);
            assertThat(rolled.barLength()).isEqualTo(12);
        }
    }

    @Nested
    class EdgesInPixels {
        @Test
        void turnsMillimetresIntoPixelsAtTheFrequency() {
            Insets insets = edgesInPixels(QUADS.crop().bleed(), Sampling.of(254));
            assertThat(insets.top()).isCloseTo(30, byLessThan(0.5e-6));
            assertThat(insets.bottom()).isCloseTo(380, byLessThan(0.5e-6));
        }

        @Test
        void usesEachAxisOfANonSquareFrequency() {
            Insets insets = edgesInPixels(QUADS.crop().bleed(), new Sampling(254, 508));
            assertThat(insets.left()).isCloseTo(30, byLessThan(0.5e-6));
            assertThat(insets.top()).isCloseTo(60, byLessThan(0.5e-6));
        }

        @Test
        void isZeroForAProfileWithNoBleed() {
            assertThat(edgesInPixels(HOUSE.crop().bleed(), Sampling.of(400)).isZero())
                    .isTrue();
        }
    }

    @Nested
    class CropOptionsBehaviour {
        @Test
        void givesThePlannerPixelsAndNothingElse() {
            CropOptions options = cropOptions(QUADS, Sampling.of(600));
            assertThat(options.anchor()).isEqualTo(Anchor.NEATLINE);
            assertThat(options.bleed().bottom()).isCloseTo((38 / 25.4) * 600, byLessThan(0.5e-6));
            assertThat(options.snapTo()).isEqualTo(8);
            assertThat(options.rounding()).isEqualTo(RoundPolicy.OUTWARD);
            assertThat(options.deskew()).isTrue();
        }

        @Test
        void leavesAnAspectOutWhenTheProfileDidNotSetOne() {
            assertThat(cropOptions(HOUSE, Sampling.of(400)).aspect()).isEmpty();
        }

        @Test
        void carriesAnAspectTheProfileDidSet() {
            ProfileRegistry site = new ProfileRegistry()
                    .add(bag(
                            "name",
                            "square",
                            "extends",
                            "house",
                            "crop",
                            bag("aspect", 1.0, "aspectDirection", "shrink")));
            CropOptions options = cropOptions(site.get("square"), Sampling.of(400));
            assertThat(options.aspect()).hasValue(1);
            assertThat(options.aspectDirection()).isEqualTo(AspectDirection.SHRINK);
        }
    }

    @Nested
    class DerivativeRecipes {
        @Test
        void turnsTheWrittenDepthIntoANumber() {
            List<DerivativeRecipe> recipes = derivativeRecipes(HOUSE);
            assertThat(recipes.stream().map(DerivativeRecipe::name).toList())
                    .isEqualTo(List.of("master", "service", "thumb"));
            assertThat(recipes.get(0).depth()).isEqualTo(16);
            assertThat(recipes.get(1).depth()).isEqualTo(8);
        }

        @Test
        void carriesTheSizeInstructionThroughWhicheverItWas() {
            List<DerivativeRecipe> recipes = derivativeRecipes(HOUSE);
            assertThat(recipes.get(0).longestSide()).isEmpty();
            assertThat(recipes.get(1).longestSide()).hasValue(3000);
            assertThat(recipes.get(0).scale()).isEmpty();
        }

        @Test
        void givesAnEmptyListForAProfileThatWritesNothing() {
            ProfileRegistry site =
                    new ProfileRegistry().add(bag("name", "quiet", "extends", "house", "derivatives", list()));
            assertThat(derivativeRecipes(site.get("quiet"))).isEqualTo(List.of());
        }
    }

    @Nested
    class QcLimitsBehaviour {
        @Test
        void readsTheFunderNumbersOffTheProfile() {
            QcLimits limits = qcLimits(QUADS);
            assertThat(limits.sampling().x()).isEqualTo(600);
            assertThat(limits.skewTolerance()).isEqualTo(0.15);
            assertThat(limits.minimumStars()).isEqualTo(4);
            assertThat(limits.maximumClipping()).isEqualTo(0.001);
            assertThat(limits.samplingTolerance().kind()).isEqualTo(ToleranceKind.RELATIVE);
        }
    }

    @Nested
    class FillName {
        @Test
        void fillsThePlaceholders() {
            assertThat(fillName(HOUSE, Map.of("accession", "MS2019-004-017", "derivative", "service")))
                    .isEqualTo("ms2019-004-017_service");
        }

        @Test
        void leavesAPlaceholderItDoesNotHaveAValueFor() {
            assertThat(fillName(HOUSE, Map.of("derivative", "service"))).isEqualTo("{accession}_service");
        }

        @Test
        void keepsTheCaseWhenTheProfileSaysTo() {
            ProfileRegistry site = new ProfileRegistry()
                    .add(bag("name", "cased", "extends", "house", "naming", bag("lowercase", false)));
            assertThat(fillName(site.get("cased"), Map.of("accession", "MS2019", "derivative", "M")))
                    .isEqualTo("MS2019_M");
        }

        @Test
        void takesAPatternTheProfileSet() {
            ProfileRegistry site = new ProfileRegistry()
                    .add(bag(
                            "name",
                            "other",
                            "extends",
                            "house",
                            "naming",
                            bag("pattern", "{collection}/{accession}-{derivative}")));
            assertThat(fillName(
                            site.get("other"), Map.of("collection", "Quads", "accession", "A1", "derivative", "thumb")))
                    .isEqualTo("quads/a1-thumb");
        }
    }

    @Nested
    class SettingsFor {
        @Test
        void bundlesEveryStageTogether() {
            StageSettings settings = settingsFor(QUADS);
            assertThat(settings.profile()).isSameAs(QUADS);
            assertThat(settings.sampling().x()).isEqualTo(600);
            assertThat(settings.capture().expectNeatline()).isTrue();
            assertThat(settings.crop().anchor()).isEqualTo(Anchor.NEATLINE);
            assertThat(settings.derivatives()).hasSize(3);
            assertThat(settings.qc().minimumStars()).isEqualTo(4);
        }

        @Test
        void usesAMeasuredFrequencyOverTheClaimedOneWhenGiven() {
            StageSettings settings = settingsFor(QUADS, Sampling.of(300));
            assertThat(settings.sampling().x()).isEqualTo(300);
            assertThat(settings.crop().bleed().bottom()).isCloseTo((38 / 25.4) * 300, byLessThan(0.5e-6));
        }
    }

    @Nested
    class TurnFor {
        private CropOptions crop(Map<String, Object> written) {
            return cropOptions(Fields.parseProfile(bag("name", "atlas", "crop", written)), Sampling.of(400));
        }

        @Test
        void takesOutTheWholeSkewByDefault() {
            assertThat(crop(bag()).turnFor(Angle.degrees(0.05)).degrees()).isEqualTo(0.05);
        }

        @Test
        void leavesASheetInsideTheThresholdAsItLiesEitherWay() {
            CropOptions lenient = crop(bag("deskewAbove", 0.2));
            assertThat(lenient.turnFor(Angle.degrees(0.2)).isZero()).isTrue();
            assertThat(lenient.turnFor(Angle.degrees(-0.15)).isZero()).isTrue();
            assertThat(lenient.turnFor(Angle.degrees(-0.25)).degrees()).isEqualTo(-0.25);
        }

        @Test
        void neverTurnsACropThatDoesNotDeskew() {
            assertThat(crop(bag("deskew", false)).turnFor(Angle.degrees(3)).isZero())
                    .isTrue();
        }

        @Test
        void carriesTheThresholdIntoTheJson() {
            assertThat(Fields.parseProfile(bag("name", "atlas", "crop", bag("deskewAbove", 0.2)))
                            .crop()
                            .toJson())
                    .containsEntry("deskewAbove", 0.2);
        }
    }
}
