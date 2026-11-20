package io.github.rkaushikdev.decklewright.derive;

import static io.github.rkaushikdev.decklewright.derive.Recipes.DEFAULT_FORMAT;
import static io.github.rkaushikdev.decklewright.derive.Recipes.FORMAT_LIMITS;
import static io.github.rkaushikdev.decklewright.derive.Recipes.encodeAll;
import static io.github.rkaushikdev.decklewright.derive.Recipes.encodeRendered;
import static io.github.rkaushikdev.decklewright.derive.Recipes.frequencyOf;
import static io.github.rkaushikdev.decklewright.derive.Recipes.isWritable;
import static io.github.rkaushikdev.decklewright.derive.Recipes.limitsFor;
import static io.github.rkaushikdev.decklewright.derive.Recipes.objectionsTo;
import static io.github.rkaushikdev.decklewright.derive.Recipes.provenanceText;
import static io.github.rkaushikdev.decklewright.derive.Recipes.sizeOf;
import static io.github.rkaushikdev.decklewright.derive.Recipes.textOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.codec.Codecs;
import io.github.rkaushikdev.decklewright.codec.ImageFormat;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.DerivativeRecipe;
import io.github.rkaushikdev.decklewright.profile.OutputFormat;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RecipeTest {

    /** A flat rendered derivative, 24 by 16, with nothing done to it. */
    static Rendered rendered(String name, Layout layout, int depth, double ppi) {
        return new Rendered(name, Surface.create(24, 16, layout, depth, 120), Sampling.of(ppi), List.of(), 1);
    }

    /** An eight bit rgb master at 400 ppi. */
    static Rendered rendered() {
        return rendered("master", Layout.RGB, 8, 400);
    }

    static DerivativeRecipe recipe(String name, OutputFormat format, boolean keepMetadata) {
        return TestRecipe.named(name)
                .withFormat(format)
                .withKeepMetadata(keepMetadata)
                .recipe();
    }

    static EncodeOptions options() {
        return EncodeOptions.defaults();
    }

    static double ppiOf(byte[] bytes) {
        return frequencyOf(bytes).orElseThrow().x();
    }

    @Nested
    class LimitsFor {
        @Test
        void knowsWhatEachFormatHolds() {
            assertThat(limitsFor("png").depths()).contains(16);
            assertThat(limitsFor("pnm").carriesFrequency()).isFalse();
        }

        @Test
        void refusesAFormatNobodyHereHasHeardOf() {
            assertThatThrownBy(() -> limitsFor("jp2"))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("nothing here writes");
        }

        @Test
        void isFrozenSoACallerCannotWidenAFormatByAccident() {
            FormatLimits png = FORMAT_LIMITS.get("png");
            assertThatThrownBy(() -> FORMAT_LIMITS.put("jp2", png)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> FORMAT_LIMITS.remove("png")).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> png.depths().add(32)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> FORMAT_LIMITS.get("pnm").layouts().add(Layout.RGBA))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class IsWritable {
        @Test
        void isTrueForTheFormatsWithAnEncoder() {
            assertThat(isWritable("png")).isTrue();
            assertThat(isWritable("pnm")).isTrue();
        }

        @Test
        void isFalseForAFormatThatIsOnlyRead() {
            assertThat(isWritable("tiff")).isFalse();
        }

        @Test
        void isFalseRatherThanThrowingForANameNobodyKnows() {
            assertThat(isWritable("jp2")).isFalse();
        }
    }

    @Nested
    class ObjectionsTo {
        @Test
        void hasNothingToSayAboutAFileThatWillGoOutCleanly() {
            assertThat(objectionsTo(rendered().surface(), "png")).isEmpty();
        }

        @Test
        void saysSoWhenTheFormatIsOnlyRead() {
            assertThat(objectionsTo(rendered().surface(), "tiff").get(0)).containsPattern("only reads it");
        }

        @Test
        void saysSoWhenTheLayoutWillNotFit() {
            Rendered alpha = rendered("master", Layout.RGBA, 8, 400);
            assertThat(objectionsTo(alpha.surface(), "pnm").get(0)).containsPattern("cannot hold rgba");
        }

        @Test
        void stopsAtTheFirstObjectionWhenTheFormatCannotBeWrittenAtAll() {
            assertThat(objectionsTo(rendered("master", Layout.RGBA, 8, 400).surface(), "tiff"))
                    .hasSize(1);
        }
    }

    @Nested
    class ProvenanceText {
        @Test
        void namesTheSoftwareAndTheSource() {
            Map<String, String> text = provenanceText("g4181-1907");
            assertThat(text.get("Software")).isEqualTo("decklewright");
            assertThat(text.get("Source")).isEqualTo("g4181-1907");
        }

        @Test
        void namesTheDerivativeWhenThereIsOne() {
            assertThat(provenanceText("a", ProvenanceOptions.defaults().withDerivative("thumb"))
                            .get("Title"))
                    .isEqualTo("a thumb");
        }

        @Test
        void leavesTheProfileOutWhenThereIsNone() {
            assertThat(provenanceText("a")).doesNotContainKey("Comment");
        }
    }

    @Nested
    class EncodeRendered {
        @Test
        void writesAPngByDefault() {
            Encoded out = encodeRendered(rendered());
            assertThat(out.format()).isEqualTo(DEFAULT_FORMAT);
            assertThat(out.extension()).isEqualTo(".png");
            assertThat(Codecs.readImage(out.bytes()).width()).isEqualTo(24);
        }

        @Test
        void putsTheFrequencyInTheFile() {
            Encoded out = encodeRendered(rendered("master", Layout.RGB, 8, 300));
            assertThat(ppiOf(out.bytes())).isCloseTo(300, byLessThan(0.5));
        }

        @Test
        void putsTheDerivativeFrequencyInNotTheMasterOne() {
            Rendered master = rendered();
            Rendered half = new Rendered(master.name(), master.surface(), Sampling.of(200), master.steps(), 0.5);
            assertThat(ppiOf(encodeRendered(half).bytes())).isCloseTo(200, byLessThan(0.5));
        }

        @Test
        void carriesTheTextItWasGiven() {
            Encoded out = encodeRendered(rendered(), options().withText(provenanceText("g4181")));
            assertThat(textOf(out.bytes()).get("Source")).isEqualTo("g4181");
        }

        @Test
        void leavesTheTextOutWhenTheProfileAskedForABareFile() {
            Encoded out =
                    encodeRendered(rendered(), options().withKeepMetadata(false).withText(provenanceText("g4181")));
            assertThat(textOf(out.bytes())).doesNotContainKey("Source");
            assertThat(String.join(" ", out.notes())).containsPattern("metadata was left out");
        }

        @Test
        void stillCarriesTheFrequencyInABareFile() {
            Encoded out = encodeRendered(
                    rendered("master", Layout.RGB, 8, 600), options().withKeepMetadata(false));
            assertThat(ppiOf(out.bytes())).isCloseTo(600, byLessThan(0.5));
        }

        @Test
        void saysSoWhenTheFormatCannotCarryTheFrequency() {
            Encoded out = encodeRendered(rendered(), options().withFormat(ImageFormat.PNM));
            assertThat(String.join(" ", out.notes())).containsPattern("only in the sidecar");
            assertThat(out.extension()).isEqualTo(".pnm");
            assertThat(frequencyOf(out.bytes())).isEmpty();
        }

        @Test
        void refusesAFormatThatCannotHoldWhatItWasHanded() {
            Rendered alpha = rendered("master", Layout.RGBA, 8, 400);
            assertThatThrownBy(() -> encodeRendered(alpha, options().withFormat(ImageFormat.PNM)))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("cannot hold rgba");
        }

        @Test
        void refusesAFormatNothingHereWrites() {
            assertThatThrownBy(() -> encodeRendered(rendered(), options().withFormat(ImageFormat.TIFF)))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("only reads it");
        }

        @Test
        void takesACompressionLevelThroughToTheCodec() {
            Encoded loose = encodeRendered(rendered(), options().withLevel(0));
            Encoded tight = encodeRendered(rendered(), options().withLevel(9));
            assertThat(loose.bytes().length).isGreaterThan(tight.bytes().length);
        }
    }

    @Nested
    class EncodeAll {
        private final List<Rendered> set =
                List.of(rendered("master", Layout.RGB, 16, 400), rendered("thumb", Layout.RGB, 8, 400));
        private final List<DerivativeRecipe> recipes =
                List.of(recipe("master", OutputFormat.PNG, true), recipe("thumb", OutputFormat.PNM, false));

        @Test
        void writesEachOneInTheFormatItsOwnRecipeAskedFor() {
            List<Encoded> out =
                    encodeAll(set, recipes, EncodeAllOptions.defaults().withAccession("g4181"));
            assertThat(out).extracting(Encoded::format).containsExactly(ImageFormat.PNG, ImageFormat.PNM);
        }

        @Test
        void writesTheAccessionIntoTheFilesThatKeepMetadata() {
            EncodeAllOptions options =
                    EncodeAllOptions.defaults().withAccession("g4181").withProfile("atlas-plates");
            List<Encoded> out = encodeAll(set, recipes, options);
            assertThat(textOf(out.get(0).bytes()).get("Comment")).isEqualTo("profile atlas-plates");
        }

        @Test
        void fallsBackToPngForARenderedFileWithNoRecipe() {
            assertThat(encodeAll(set, List.of(), EncodeAllOptions.defaults())
                            .get(0)
                            .format())
                    .isEqualTo(ImageFormat.PNG);
        }

        /**
         * A recipe cannot name tiff, which is only read here, so the master asks for netpbm, which
         * cannot hold the alpha channel it was rendered with.
         */
        @Test
        void stopsTheWholeSetRatherThanWritingPartOfIt() {
            List<Rendered> withAlpha = List.of(rendered("master", Layout.RGBA, 8, 400), set.get(1));
            List<DerivativeRecipe> bad = List.of(recipe("master", OutputFormat.PNM, true), recipes.get(1));
            assertThatThrownBy(() -> encodeAll(withAlpha, bad, EncodeAllOptions.defaults()))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("cannot hold rgba");
        }

        @Test
        void keepsTheRenderedFileBesideItsBytes() {
            List<Encoded> out = encodeAll(set, recipes, EncodeAllOptions.defaults());
            assertThat(out.get(0).rendered().name()).isEqualTo("master");
        }
    }

    @Nested
    class SizeOf {
        @Test
        void addsTheSetUp() {
            SetSize out = sizeOf(encodeAll(
                    List.of(rendered("a", Layout.RGB, 8, 400), rendered("b", Layout.RGB, 8, 400)), List.of()));
            assertThat(out.total())
                    .isEqualTo(out.perName().get("a") + out.perName().get("b"));
        }

        @Test
        void namesTheLargest() {
            List<Rendered> set = List.of(rendered("small", Layout.RGB, 8, 400), rendered("deep", Layout.RGB, 16, 400));
            assertThat(sizeOf(encodeAll(set, List.of())).largest()).contains("deep");
        }

        @Test
        void hasNothingToNameForAnEmptySet() {
            assertThat(sizeOf(List.of())).isEqualTo(new SetSize(0, Optional.empty(), Map.of()));
        }
    }
}
