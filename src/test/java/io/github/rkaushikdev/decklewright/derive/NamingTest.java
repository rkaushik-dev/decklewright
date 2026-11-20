package io.github.rkaushikdev.decklewright.derive;

import static io.github.rkaushikdev.decklewright.derive.FileNames.MAX_STEM;
import static io.github.rkaushikdev.decklewright.derive.FileNames.RESERVED;
import static io.github.rkaushikdev.decklewright.derive.FileNames.collisions;
import static io.github.rkaushikdev.decklewright.derive.FileNames.nameFor;
import static io.github.rkaushikdev.decklewright.derive.FileNames.namesFor;
import static io.github.rkaushikdev.decklewright.derive.FileNames.objectionsToNames;
import static io.github.rkaushikdev.decklewright.derive.FileNames.parseName;
import static io.github.rkaushikdev.decklewright.derive.FileNames.safeStem;
import static io.github.rkaushikdev.decklewright.derive.FileNames.slug;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NamingTest {

    private static final Profile HOUSE = new ProfileRegistry().get("house");

    /** A precomposed small e with a grave accent. */
    private static final char E_GRAVE = (char) 0xE8;

    /** The house profile named under another pattern. */
    static Profile withPattern(String pattern, boolean lowercase) {
        return HOUSE.withNaming(new Profile.Naming(pattern, lowercase));
    }

    /** The house profile named under another pattern, lower cased. */
    static Profile withPattern(String pattern) {
        return withPattern(pattern, true);
    }

    /** A derivative about to be named. */
    record Pending(String name, String extension) implements Nameable {}

    @Nested
    class Slug {
        @Test
        void leavesANameThatIsAlreadySafeAlone() {
            assertThat(slug("g4181-1907")).isEqualTo("g4181-1907");
        }

        @Test
        void turnsARunOfAnythingElseIntoOneSeparator() {
            assertThat(slug("sheet   17 / plate 4")).isEqualTo("sheet-17-plate-4");
        }

        @Test
        void takesTheAccentsOff() {
            assertThat(slug("rivi" + E_GRAVE + "re-des-prairies")).isEqualTo("riviere-des-prairies");
        }

        @Test
        void doesNotStartOrEndWithASeparator() {
            assertThat(slug("  atlas  ")).isEqualTo("atlas");
            assertThat(slug("///atlas///")).isEqualTo("atlas");
        }

        @Test
        void keepsADotBecauseAnAccessionCanHaveOne() {
            assertThat(slug("g4181.c2")).isEqualTo("g4181.c2");
        }

        @Test
        void takesAnotherSeparator() {
            assertThat(slug("sheet 17", '_')).isEqualTo("sheet_17");
        }
    }

    @Nested
    class SafeStem {
        @Test
        void isTheSlugForAnOrdinaryName() {
            assertThat(safeStem("Plate 12")).isEqualTo("Plate-12");
        }

        @Test
        void cutsANameThatIsTooLong() {
            assertThat(safeStem("a".repeat(200))).hasSize(MAX_STEM);
        }

        @Test
        void doesNotLeaveACutNameEndingInASeparator() {
            String cut = safeStem("a".repeat(MAX_STEM - 1) + "-bbbb");
            assertThat(cut).doesNotEndWith("-");
        }

        @Test
        void getsANamePastAReservedWordRatherThanRefusingIt() {
            for (String word : RESERVED) {
                assertThat(safeStem(word)).as(word).isEqualTo(word + "_");
            }
        }

        @Test
        void catchesAReservedWordWhateverTheCase() {
            assertThat(safeStem("AUX")).isEqualTo("AUX_");
        }

        @Test
        void refusesANameWithNothingLeftInIt() {
            assertThatThrownBy(() -> safeStem("///"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("nothing left");
        }
    }

    @Nested
    class NameFor {
        @Test
        void fillsTheProfilePattern() {
            String name = nameFor(HOUSE, "G4181", "master", ".png");
            assertThat(name).isEqualTo("g4181_master.png");
        }

        @Test
        void keepsTheCaseWhenTheProfileSaysTo() {
            String name = nameFor(withPattern("{accession}_{derivative}", false), "G4181", "master", ".png");
            assertThat(name).isEqualTo("G4181_master.png");
        }

        @Test
        void cleansTheAccessionBeforeItGoesIntoThePattern() {
            String name = nameFor(HOUSE, "sheet 17 / plate 4", "thumb", ".png");
            assertThat(name).isEqualTo("sheet-17-plate-4_thumb.png");
        }

        @Test
        void takesExtraPlaceholders() {
            String name = nameFor(
                    withPattern("{collection}-{accession}"), "g4181", "master", ".png", Map.of("collection", "quads"));
            assertThat(name).isEqualTo("quads-g4181.png");
        }

        @Test
        void leavesAPlaceholderNobodyFilledVisibleInTheName() {
            String name = nameFor(withPattern("{accession}_{shelf}"), "g4181", "master", ".png");
            assertThat(name).containsPattern("shelf");
        }

        @Test
        void refusesAnEmptyAccession() {
            assertThatThrownBy(() -> nameFor(HOUSE, "", "master", ".png")).isInstanceOf(BadArgumentError.class);
        }
    }

    @Nested
    class NamesFor {
        private final List<Pending> encoded =
                List.of(new Pending("master", ".png"), new Pending("service", ".png"), new Pending("thumb", ".pnm"));

        @Test
        void namesEveryDerivativeInTheSet() {
            Map<String, String> names = namesFor(HOUSE, "g4181", encoded);
            assertThat(names)
                    .containsExactly(
                            entry("master", "g4181_master.png"),
                            entry("service", "g4181_service.png"),
                            entry("thumb", "g4181_thumb.pnm"));
        }

        @Test
        void givesEachOneTheExtensionItsOwnFormatAskedFor() {
            Map<String, String> names = namesFor(HOUSE, "g4181", encoded);
            assertThat(names.get("thumb")).endsWith(".pnm");
        }
    }

    @Nested
    class Collisions {
        @Test
        void findsTwoNamesThatDifferOnlyInCase() {
            assertThat(collisions(List.of("Plate.png", "plate.png")))
                    .isEqualTo(List.of(List.of("Plate.png", "plate.png")));
        }

        @Test
        void findsNothingInASetThatIsGenuinelyDistinct() {
            assertThat(collisions(List.of("a.png", "b.png"))).isEmpty();
        }

        @Test
        void groupsThreeTheSameWayItGroupsTwo() {
            assertThat(collisions(List.of("A", "a", "A"))).isEqualTo(List.of(List.of("A", "A", "a")));
        }
    }

    @Nested
    class ObjectionsToNames {
        @Test
        void hasNothingToSayAboutACleanSet() {
            assertThat(objectionsToNames(names("master", "a_master.png", "thumb", "a_thumb.png")))
                    .isEmpty();
        }

        @Test
        void saysSoWhenASetIsEmpty() {
            assertThat(objectionsToNames(Map.of())).isEqualTo(List.of("nothing was named"));
        }

        @Test
        void namesThePairThatWouldCollide() {
            List<String> out = objectionsToNames(names("a", "Plate.png", "b", "plate.png"));
            assertThat(out.get(0)).containsPattern("differ only in case");
        }

        @Test
        void saysSoWhenANameHasNoExtension() {
            assertThat(objectionsToNames(Map.of("master", "a_master")).get(0)).containsPattern("no extension");
        }

        /** Derivative name to file name, in the order given. */
        private static Map<String, String> names(String first, String firstName, String second, String secondName) {
            Map<String, String> names = new LinkedHashMap<>();
            names.put(first, firstName);
            names.put(second, secondName);
            return names;
        }
    }

    @Nested
    class ParseName {
        @Test
        void pullsThePlaceholdersBackOut() {
            assertThat(parseName("g4181_master.png", HOUSE))
                    .contains(Map.of("accession", "g4181", "derivative", "master"));
        }

        @Test
        void worksWithoutTheExtension() {
            assertThat(parseName("g4181_master", HOUSE))
                    .hasValueSatisfying(
                            values -> assertThat(values.get("derivative")).isEqualTo("master"));
        }

        @Test
        void givesNothingBackForANameWrittenUnderAnotherPattern() {
            assertThat(parseName("g4181-master.png", HOUSE)).isEmpty();
        }

        @Test
        void splitsAtTheFirstSeparatorWhenTheAccessionHasOneToo() {
            assertThat(parseName("g4181_c2_master.png", HOUSE))
                    .contains(Map.of("accession", "g4181", "derivative", "c2_master"));
        }

        @Test
        void handlesAPatternWithAPlaceholderAtTheEnd() {
            Profile profile = withPattern("{collection}-{accession}");
            assertThat(parseName("quads-g4181.png", profile))
                    .contains(Map.of("collection", "quads", "accession", "g4181"));
        }

        @Test
        void roundTripsWhatNameForWrote() {
            String name = nameFor(HOUSE, "g4181", "service", ".png");
            assertThat(parseName(name, HOUSE)).contains(Map.of("accession", "g4181", "derivative", "service"));
        }
    }
}
