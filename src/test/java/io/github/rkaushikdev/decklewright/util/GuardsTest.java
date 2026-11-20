package io.github.rkaushikdev.decklewright.util;

import static io.github.rkaushikdev.decklewright.util.Guards.require;
import static io.github.rkaushikdev.decklewright.util.Guards.requireFinite;
import static io.github.rkaushikdev.decklewright.util.Guards.requireInRange;
import static io.github.rkaushikdev.decklewright.util.Guards.requireInteger;
import static io.github.rkaushikdev.decklewright.util.Guards.requireListOf;
import static io.github.rkaushikdev.decklewright.util.Guards.requireNonEmptyString;
import static io.github.rkaushikdev.decklewright.util.Guards.requireNonNegative;
import static io.github.rkaushikdev.decklewright.util.Guards.requireOneOf;
import static io.github.rkaushikdev.decklewright.util.Guards.requirePlainObject;
import static io.github.rkaushikdev.decklewright.util.Guards.requirePositive;
import static io.github.rkaushikdev.decklewright.util.Guards.requirePositiveInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.ErrorCode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GuardsTest {

    @Nested
    class Assert {
        @Test
        void passesATruthyConditionThroughSilently() {
            assertThatCode(() -> require(true, "never")).doesNotThrowAnyException();
        }

        @Test
        void throwsABadArgumentErrorCarryingTheDetails() {
            assertThatThrownBy(() -> require(false, "bed width is wrong", Map.of("width", 0)))
                    .isInstanceOfSatisfying(BadArgumentError.class, err -> {
                        assertThat(err.code()).isEqualTo(ErrorCode.BAD_ARGUMENT);
                        assertThat(err.getMessage()).isEqualTo("bed width is wrong");
                        assertThat(err.details()).isEqualTo(Map.of("width", 0));
                    });
        }
    }

    @Nested
    class AssertFinite {
        @Test
        void returnsTheValueItWasGiven() {
            assertThat(requireFinite(3.5, "ppi")).isEqualTo(3.5);
            assertThat(requireFinite(-0.25, "skew")).isEqualTo(-0.25);
        }

        static Stream<Arguments> rejected() {
            // undefined and null are the same absence in Java, so null stands for both.
            return Stream.of(
                    Arguments.of(Double.NaN),
                    Arguments.of(Double.POSITIVE_INFINITY),
                    Arguments.of(Double.NEGATIVE_INFINITY),
                    Arguments.of("4"),
                    Arguments.of((Object) null));
        }

        @ParameterizedTest(name = "rejects {0}")
        @MethodSource("rejected")
        void rejects(Object value) {
            assertThatThrownBy(() -> requireFinite(value, "ppi"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("ppi must be a finite number");
        }
    }

    @Nested
    class AssertInteger {
        @Test
        void acceptsWholeNumbersIncludingZeroAndNegatives() {
            assertThat(requireInteger(0, "x")).isEqualTo(0);
            assertThat(requireInteger(-12, "x")).isEqualTo(-12);
        }

        @Test
        void rejectsAFraction() {
            assertThatThrownBy(() -> requireInteger(2.5, "rows")).hasMessageFindingMatch("rows must be a whole number");
        }
    }

    @Nested
    class AssertPositiveAndAssertNonNegative {
        @Test
        void separatesZeroFromTheNegatives() {
            assertThatThrownBy(() -> requirePositive(0, "w")).hasMessageFindingMatch("greater than zero");
            assertThat(requireNonNegative(0, "w")).isEqualTo(0);
            assertThatThrownBy(() -> requireNonNegative(-1, "w")).hasMessageFindingMatch("must not be negative");
        }
    }

    @Nested
    class AssertPositiveInteger {
        @Test
        void wantsBothProperties() {
            assertThat(requirePositiveInteger(7, "n")).isEqualTo(7);
            assertThatThrownBy(() -> requirePositiveInteger(0, "n")).hasMessageFindingMatch("above zero");
            assertThatThrownBy(() -> requirePositiveInteger(1.5, "n")).hasMessageFindingMatch("whole number");
        }
    }

    @Nested
    class AssertInRange {
        @Test
        void isInclusiveAtBothEnds() {
            assertThat(requireInRange(0, 0, 1, "t")).isEqualTo(0);
            assertThat(requireInRange(1, 0, 1, "t")).isEqualTo(1);
        }

        @Test
        void namesTheBoundsInTheMessageAndTheDetails() {
            assertThatThrownBy(() -> requireInRange(4, 0, 1, "t"))
                    .isInstanceOfSatisfying(BadArgumentError.class, err -> {
                        assertThat(err.getMessage()).isEqualTo("t must be between 0 and 1");
                        assertThat(err.details()).isEqualTo(Map.of("name", "t", "value", 4.0, "low", 0.0, "high", 1.0));
                    });
        }
    }

    @Nested
    class AssertOneOf {
        @Test
        void returnsTheMatchedMember() {
            assertThat(requireOneOf("gray", List.of("gray", "rgb"), "layout")).isEqualTo("gray");
        }

        @Test
        void listsWhatWasAllowed() {
            assertThatThrownBy(() -> requireOneOf("cmyk", List.of("gray", "rgb"), "layout"))
                    .hasMessageFindingMatch("layout must be one of gray, rgb");
        }
    }

    @Nested
    class AssertNonEmptyString {
        @Test
        void rejectsTheEmptyStringAndNonStrings() {
            assertThat(requireNonEmptyString("q17", "sheet")).isEqualTo("q17");
            assertThatThrownBy(() -> requireNonEmptyString("", "sheet")).hasMessageFindingMatch("non empty string");
            assertThatThrownBy(() -> requireNonEmptyString(17, "sheet")).hasMessageFindingMatch("non empty string");
        }
    }

    @Nested
    class AssertPlainObject {
        @Test
        void acceptsObjectLiteralsAndNullPrototypeBags() {
            assertThat(requirePlainObject(Map.of("a", 1), "profile")).isEqualTo(Map.of("a", 1));
            assertThat(requirePlainObject(new LinkedHashMap<String, Object>(), "profile"))
                    .isEqualTo(Map.of());
        }

        static Stream<Arguments> rejected() {
            return Stream.of(
                    Arguments.of(List.of()),
                    Arguments.of((Object) null),
                    Arguments.of(Instant.EPOCH),
                    Arguments.of("text"));
        }

        @ParameterizedTest(name = "rejects {0}")
        @MethodSource("rejected")
        void rejects(Object value) {
            assertThatThrownBy(() -> requirePlainObject(value, "profile"))
                    .hasMessageFindingMatch("profile must be a plain object");
        }
    }

    @Nested
    class AssertArrayOf {
        @Test
        void mapsTheCheckerOverTheMembers() {
            assertThat(requireListOf(List.of(1, 2, 3), (v, i) -> requireInteger(v, "n"), "list"))
                    .containsExactly(1.0, 2.0, 3.0);
        }

        @Test
        void letsTheMemberFailureSurfaceWithItsOwnMessage() {
            assertThatThrownBy(() -> requireListOf(List.of(1, "two"), (v, i) -> requireInteger(v, "n"), "list"))
                    .hasMessageFindingMatch("n must be a finite number");
        }

        @Test
        void rejectsANonArrayOutright() {
            assertThatThrownBy(() -> requireListOf("abc", (v, i) -> v, "list"))
                    .hasMessageFindingMatch("list must be an array");
        }
    }
}
