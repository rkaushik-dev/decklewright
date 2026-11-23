package io.github.rkaushikdev.decklewright.derive;

import static io.github.rkaushikdev.decklewright.derive.FileNames.nameFor;
import static io.github.rkaushikdev.decklewright.derive.FileNames.placeholdersFor;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CallNumberTest {

    private static final Profile HOUSE = new ProfileRegistry().get("house");

    private static Profile named(String pattern) {
        return HOUSE.withNaming(new Profile.Naming(pattern, true));
    }

    @Nested
    class Read {
        @Test
        void takesTheClassNumberAndTheYear() {
            assertThat(CallNumber.read("g4181-1907"))
                    .contains(new CallNumber("g4181", Optional.empty(), OptionalInt.of(1907)));
        }

        @Test
        void takesACutterBetweenThem() {
            CallNumber call = CallNumber.read("G3701.S5_1864").orElseThrow();
            assertThat(call.classNumber()).isEqualTo("g3701");
            assertThat(call.cutter()).contains("s5");
            assertThat(call.year()).hasValue(1864);
        }

        @Test
        void readsAClassNumberWithNoDate() {
            assertThat(CallNumber.read("g4181"))
                    .contains(new CallNumber("g4181", Optional.empty(), OptionalInt.empty()));
        }

        @Test
        void letsASheetNumberFollowTheDate() {
            assertThat(CallNumber.read("g4181-1907-sheet12").orElseThrow().year())
                    .hasValue(1907);
        }

        @Test
        void refusesWhatIsNotAMapClassNumber() {
            assertThat(CallNumber.read("g1019-1907")).isEmpty();
            assertThat(CallNumber.read("loose-sheet-4")).isEmpty();
            assertThat(CallNumber.read("g418-1907")).isEmpty();
            assertThat(CallNumber.read("q18")).isEmpty();
        }

        @Test
        void doesNotMistakeASheetNumberForAYear() {
            CallNumber call = CallNumber.read("g4181-0042").orElseThrow();
            assertThat(call.year()).isEmpty();
        }
    }

    @Nested
    class Placeholders {
        @Test
        void giveTheClassAlwaysAndTheRestWhenWritten() {
            assertThat(CallNumber.read("g3701.s5-1864").orElseThrow().placeholders())
                    .containsExactly(Map.entry("class", "g3701"), Map.entry("cutter", "s5"), Map.entry("year", "1864"));
            assertThat(CallNumber.read("g4181").orElseThrow().placeholders()).containsOnlyKeys("class");
        }

        @Test
        void fillANamingPatternTheWayTheMapRoomShelves() {
            assertThat(nameFor(named("{year}-{class}_{derivative}"), "G4181-1907", "service", ".png"))
                    .isEqualTo("1907-g4181_service.png");
        }

        @Test
        void leaveTheYearVisibleWhenTheAccessionGivesNone() {
            assertThat(nameFor(named("{class}_{year}"), "g4181", "master", ".png"))
                    .contains("year");
        }

        @Test
        void giveWayToTheAccessionAndTheExtraValues() {
            Map<String, Object> values = placeholdersFor("g4181-1907", "master", Map.of("year", "1908"));
            assertThat(values).containsEntry("year", "1908").containsEntry("accession", "g4181-1907");
            assertThat(placeholdersFor("loose 4", "master", Map.of())).containsOnlyKeys("accession", "derivative");
        }
    }
}
