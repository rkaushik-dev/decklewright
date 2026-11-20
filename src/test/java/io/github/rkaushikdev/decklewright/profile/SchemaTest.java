package io.github.rkaushikdev.decklewright.profile;

import static io.github.rkaushikdev.decklewright.profile.Documents.bag;
import static io.github.rkaushikdev.decklewright.profile.Documents.list;
import static io.github.rkaushikdev.decklewright.profile.Schema.choice;
import static io.github.rkaushikdev.decklewright.profile.Schema.edges;
import static io.github.rkaushikdev.decklewright.profile.Schema.edgesToInsets;
import static io.github.rkaushikdev.decklewright.profile.Schema.fieldError;
import static io.github.rkaushikdev.decklewright.profile.Schema.flag;
import static io.github.rkaushikdev.decklewright.profile.Schema.group;
import static io.github.rkaushikdev.decklewright.profile.Schema.length;
import static io.github.rkaushikdev.decklewright.profile.Schema.list;
import static io.github.rkaushikdev.decklewright.profile.Schema.number;
import static io.github.rkaushikdev.decklewright.profile.Schema.readGroup;
import static io.github.rkaushikdev.decklewright.profile.Schema.text;
import static io.github.rkaushikdev.decklewright.profile.Schema.tolerance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import io.github.rkaushikdev.decklewright.ProfileError;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.measure.Length;
import io.github.rkaushikdev.decklewright.measure.Tolerance;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SchemaTest {

    @Nested
    class FieldError {
        @Test
        void putsThePathInFrontOfTheMessageAndInTheDetails() {
            ProfileError err = fieldError("crop.bleed", "expected a length", Map.of("got", 3.0));
            assertThat(err.getMessage()).isEqualTo("crop.bleed: expected a length");
            assertThat(err.details()).isEqualTo(Map.of("path", "crop.bleed", "got", 3.0));
        }
    }

    @Nested
    class Text {
        @Test
        void acceptsANonEmptyString() {
            assertThat(text().read("quadrangle", "name")).isEqualTo("quadrangle");
        }

        @Test
        void refusesAnythingElse() {
            assertThatThrownBy(() -> text().read("", "name"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("name: expected a non empty string");
            assertThatThrownBy(() -> text().read(7.0, "name"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("expected a non empty string");
        }
    }

    @Nested
    class NumberBehaviour {
        @Test
        void acceptsANumberAndEnforcesTheBounds() {
            assertThat(number().read(3.5, "ppi")).isEqualTo(3.5);
            assertThatThrownBy(
                            () -> number(NumberOptions.defaults().withMin(100)).read(50.0, "ppi"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("ppi: must be at least 100");
            assertThatThrownBy(
                            () -> number(NumberOptions.defaults().withMax(10)).read(50.0, "ppi"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("must be at most 10");
        }

        @Test
        void canDemandAWholeNumber() {
            assertThat(number(NumberOptions.defaults().withInteger(true)).read(8.0, "n"))
                    .isEqualTo(8.0);
            assertThatThrownBy(() ->
                            number(NumberOptions.defaults().withInteger(true)).read(8.5, "n"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("whole number");
        }

        @Test
        void refusesAnythingThatIsNotAFiniteNumber() {
            assertThatThrownBy(() -> number().read("400", "ppi"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("expected a number");
            assertThatThrownBy(() -> number().read(Double.NaN, "ppi"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("expected a number");
        }
    }

    @Nested
    class FlagAndChoice {
        @Test
        void acceptsOnlyBooleans() {
            assertThat(flag().read(true, "on")).isEqualTo(true);
            assertThatThrownBy(() -> flag().read("yes", "on"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("expected true or false");
        }

        @Test
        void acceptsOnlyListedChoicesAndNamesThem() {
            Field anchor = choice(List.of("sheet", "neatline"));
            assertThat(anchor.read("sheet", "crop.anchor")).isEqualTo("sheet");
            assertThatThrownBy(() -> anchor.read("collar", "crop.anchor"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("crop.anchor: expected one of sheet, neatline");
        }
    }

    @Nested
    class LengthAndTolerance {
        @Test
        void readsTheShapesAProfileWrites() {
            assertThat(length().read("3mm", "bleed")).isInstanceOf(Length.class);
            assertThat(((Length) length().read(bag("value", 3.0, "unit", "mm"), "bleed")).value())
                    .isEqualTo(3);
        }

        @Test
        void namesTheFieldWhenALengthWillNotRead() {
            assertThatThrownBy(() -> length().read(3.0, "crop.bleed"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("crop.bleed: expected a length");
        }

        @Test
        void readsATolerance() {
            assertThat(((Tolerance) tolerance().read("+2/-1", "skew")).plus()).isEqualTo(2);
            assertThatThrownBy(() -> tolerance().read(bag(), "skew"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("skew: expected a tolerance");
        }
    }

    @Nested
    class EdgesBehaviour {
        @Test
        void spreadsASingleLengthOverAllFour() {
            Edges bag = (Edges) edges().read("3mm", "bleed");
            assertThat(bag.toJson().keySet()).containsExactly("top", "right", "bottom", "left");
            assertThat(bag.top().value()).isEqualTo(3);
            assertThat(bag.left().value()).isEqualTo(3);
        }

        @Test
        void readsABagAndDefaultsTheEdgesLeftOut() {
            Edges bag = (Edges) edges().read(bag("bottom", "10mm"), "bleed");
            assertThat(bag.bottom().value()).isEqualTo(10);
            assertThat(bag.top().isZero()).isTrue();
        }

        @Test
        void refusesAnEdgeNameItDoesNotKnow() {
            assertThatThrownBy(() -> edges().read(bag("middle", "1mm"), "bleed"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("bleed.middle: is not an edge");
        }

        @Test
        void refusesAList() {
            assertThatThrownBy(() -> edges().read(list(1.0, 2.0), "bleed"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("expected a length or a bag of four");
        }

        @Test
        void turnsIntoInsetsInPixels() {
            Insets insets = edgesToInsets((Edges) edges().read(bag("top", "1mm", "bottom", "2mm"), "bleed"));
            assertThat(insets.top()).isEqualTo(1);
            assertThat(insets.bottom()).isEqualTo(2);
            assertThat(insets.left()).isEqualTo(0);
        }
    }

    @Nested
    class ListBehaviour {
        @Test
        void readsEachMemberAndPathsThemByIndex() {
            assertThat(list(number()).read(list(1.0, 2.0), "sizes")).isEqualTo(List.of(1.0, 2.0));
            assertThatThrownBy(() -> list(number()).read(list(1.0, "two"), "sizes"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("sizes\\[1\\]: expected a number");
        }

        @Test
        void refusesAValueThatIsNotAList() {
            assertThatThrownBy(() -> list(number()).read(3.0, "sizes"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("sizes: expected a list");
        }
    }

    @Nested
    class ReadGroup {
        private final Map<String, Field> fields = fields();

        private static Map<String, Field> fields() {
            Map<String, Field> fields = new LinkedHashMap<>();
            fields.put("name", text(FieldOptions.defaults().withRequired(true)));
            fields.put("ppi", number(NumberOptions.defaults().withMin(100).withFallback(400.0)));
            fields.put(
                    "anchor",
                    choice(List.of("sheet", "neatline"), FieldOptions.defaults().withFallback("sheet")));
            fields.put(
                    "nested",
                    group(
                            Map.of("bleed", length(FieldOptions.defaults().withFallback("0mm"))),
                            FieldOptions.defaults().withFallback(Map.of())));
            return fields;
        }

        @Test
        void readsTheFieldsAndFillsTheFallbacks() {
            Map<String, Object> out = readGroup(fields, bag("name", "atlas"));
            assertThat(out.get("name")).isEqualTo("atlas");
            assertThat(out.get("ppi")).isEqualTo(400.0);
            assertThat(out.get("anchor")).isEqualTo("sheet");
            assertThat(out.get("nested"))
                    .asInstanceOf(MAP)
                    .containsOnlyKeys("bleed")
                    .hasEntrySatisfying("bleed", bleed -> assertThat(bleed).isInstanceOf(Length.class));
        }

        @Test
        void takesWhatIsGivenOverTheFallback() {
            Map<String, Object> out = readGroup(fields, bag("name", "atlas", "ppi", 600.0, "anchor", "neatline"));
            assertThat(out.get("ppi")).isEqualTo(600.0);
            assertThat(out.get("anchor")).isEqualTo("neatline");
        }

        @Test
        void demandsTheRequiredFields() {
            assertThatThrownBy(() -> readGroup(fields, bag()))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("name: is required");
        }

        @Test
        void refusesAKeyItDoesNotKnowAndListsWhatItDoes() {
            ProfileError err = catchThrowableOfType(
                    ProfileError.class, () -> readGroup(fields, bag("name", "atlas", "bleeed", "3mm")));
            assertThat(err.getMessage()).isEqualTo("bleeed: is not a setting this profile understands");
            assertThat(err.details().get("known")).asInstanceOf(LIST).contains("ppi");
        }

        @Test
        void pathsANestedFailure() {
            assertThatThrownBy(() -> readGroup(fields, bag("name", "a", "nested", bag("bleed", 3.0))))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("nested.bleed: expected a length");
        }

        @Test
        void refusesAValueThatIsNotAGroup() {
            assertThatThrownBy(() -> readGroup(fields, "atlas"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("\\(root\\): expected a group of settings");
            assertThatThrownBy(() -> readGroup(fields, list()))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("expected a group of settings");
        }

        @Test
        void treatsAnEmptyOptionalAsAbsent() {
            // An entry holding an empty Optional is a value that is not there, so the fallback
            // is read.
            assertThat(readGroup(fields, bag("name", "atlas", "ppi", Optional.empty()))
                            .get("ppi"))
                    .isEqualTo(400.0);
        }
    }
}
