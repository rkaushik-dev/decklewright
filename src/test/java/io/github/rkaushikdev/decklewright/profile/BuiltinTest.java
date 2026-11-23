package io.github.rkaushikdev.decklewright.profile;

import static io.github.rkaushikdev.decklewright.profile.BuiltIn.ATLAS_PLATES;
import static io.github.rkaushikdev.decklewright.profile.BuiltIn.BUILT_IN;
import static io.github.rkaushikdev.decklewright.profile.BuiltIn.HOUSE;
import static io.github.rkaushikdev.decklewright.profile.BuiltIn.MICROFILM;
import static io.github.rkaushikdev.decklewright.profile.BuiltIn.QUADRANGLES;
import static io.github.rkaushikdev.decklewright.profile.BuiltIn.ROLLED_DRAWINGS;
import static io.github.rkaushikdev.decklewright.profile.BuiltIn.builtInNamed;
import static io.github.rkaushikdev.decklewright.profile.BuiltIn.builtInNames;
import static io.github.rkaushikdev.decklewright.profile.Validation.faultsOnly;
import static io.github.rkaushikdev.decklewright.profile.Validation.inspectProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.raster.Layout;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BuiltinTest {

    private static Profile resolved(Map<String, Object> document) {
        return Fields.parseProfile(
                Overlays.resolveExtends(document, BuiltIn::builtInNamed).document());
    }

    @Nested
    class TheShippedProfiles {
        static Stream<Arguments> shipped() {
            return BUILT_IN.stream().map(profile -> Arguments.of(profile.get("name"), profile));
        }

        @Test
        void listsFiveOfThemHouseFirst() {
            assertThat(builtInNames())
                    .isEqualTo(List.of("house", "atlas-plates", "quadrangles", "rolled-drawings", "microfilm"));
            assertThat(BUILT_IN.get(0)).isSameAs(HOUSE);
        }

        @Test
        void isFrozenSoACallerCannotEditTheShippedSettings() {
            assertThatThrownBy(() -> BUILT_IN.add(Map.of())).isInstanceOf(UnsupportedOperationException.class);
            for (Map<String, Object> profile : BUILT_IN) {
                assertThatThrownBy(() -> profile.put("title", "changed"))
                        .isInstanceOf(UnsupportedOperationException.class);
            }
        }

        @Test
        void findsOneByNameAndNothingForANameItDoesNotHave() {
            assertThat(builtInNamed("quadrangles")).containsSame(QUADRANGLES);
            assertThat(builtInNamed("plat-books")).isEmpty();
        }

        @ParameterizedTest(name = "{0} parses")
        @MethodSource("shipped")
        void parses(String name, Map<String, Object> document) {
            assertThatCode(() -> resolved(document)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0} has no faults")
        @MethodSource("shipped")
        void hasNoFaults(String name, Map<String, Object> document) {
            assertThat(faultsOnly(inspectProfile(resolved(document)))).isEqualTo(List.of());
        }

        @Test
        void everyProfileButHouseExtendsHouse() {
            for (Map<String, Object> profile : BUILT_IN.subList(1, BUILT_IN.size())) {
                assertThat(Overlays.resolveExtends(profile, BuiltIn::builtInNamed)
                                .chain())
                        .isEqualTo(List.of("house"));
            }
        }
    }

    @Nested
    class TheHouseStyle {
        private final Profile profile = resolved(HOUSE);

        @Test
        void cropsToThePaperEdgeWithNoBleed() {
            assertThat(profile.crop().anchor()).isEqualTo(Anchor.SHEET);
            assertThat(profile.crop().bleed().top().isZero()).isTrue();
        }

        @Test
        void writesThreeDerivatives() {
            assertThat(profile.derivatives().stream()
                            .map(Profile.Derivative::name)
                            .toList())
                    .isEqualTo(List.of("master", "service", "thumb"));
            assertThat(profile.derivatives().get(0).depth()).isEqualTo(Depth.SIXTEEN);
            assertThat(profile.derivatives().get(2).longestSide()).hasValue(400);
        }
    }

    @Nested
    class AtlasPlates {
        private final Profile profile = resolved(ATLAS_PLATES);

        @Test
        void inheritsTheDerivativesAndChangesOnlyWhatItSays() {
            assertThat(profile.derivatives()).hasSize(3);
            assertThat(profile.crop().bleed().top().value()).isEqualTo(2);
            assertThat(profile.crop().minimumPpi()).isEqualTo(400);
            assertThat(profile.qc().samplingPpi()).isEqualTo(600);
        }

        @Test
        void keepsTheHouseNaming() {
            assertThat(profile.naming().pattern()).isEqualTo("{accession}_{derivative}");
        }

        @Test
        void claimsTheFrequencyItIsGradedAt() {
            assertThat(profile.capture().claimedPpi()).isEqualTo(profile.qc().samplingPpi());
            assertThat(Validation.inspectProfile(profile)).isEmpty();
        }
    }

    @Nested
    class Quadrangles {
        private final Profile profile = resolved(QUADRANGLES);

        @Test
        void cropsToTheNeatlineAndKeepsTheLegendCollar() {
            assertThat(profile.crop().anchor()).isEqualTo(Anchor.NEATLINE);
            assertThat(profile.capture().expectNeatline()).isTrue();
            assertThat(profile.crop().bleed().bottom().value()).isEqualTo(38);
            assertThat(profile.crop().bleed().top().value()).isEqualTo(3);
        }

        @Test
        void asksForATighterCaptureThanTheHouseDoes() {
            assertThat(profile.qc().skewTolerance())
                    .isLessThan(resolved(HOUSE).qc().skewTolerance());
            assertThat(profile.qc().minimumStars()).isEqualTo(4);
        }
    }

    @Nested
    class RolledDrawings {
        private final Profile profile = resolved(ROLLED_DRAWINGS);

        @Test
        void replacesTheDerivativeListRatherThanAddingToIt() {
            assertThat(profile.derivatives().stream()
                            .map(Profile.Derivative::name)
                            .toList())
                    .isEqualTo(List.of("master", "service"));
        }

        @Test
        void isMoreForgivingAboutSkewAndNoise() {
            assertThat(profile.qc().skewTolerance())
                    .isGreaterThan(resolved(HOUSE).qc().skewTolerance());
            assertThat(profile.qc().minimumSnr())
                    .isLessThan(resolved(HOUSE).qc().minimumSnr());
        }

        @Test
        void usesTheLongerRule() {
            assertThat(profile.capture().barLength().value()).isEqualTo(12);
        }
    }

    @Nested
    class Microfilm {
        private final Profile profile = resolved(MICROFILM);

        @Test
        void writesGrayscaleEverywhere() {
            assertThat(profile.derivatives().stream().allMatch(d -> d.layout() == Layout.GRAY))
                    .isTrue();
        }

        @Test
        void turnsTheGrayBalanceCheckOffByOpeningItAllTheWay() {
            assertThat(profile.qc().grayBalanceTolerance()).isEqualTo(255);
        }
    }
}
