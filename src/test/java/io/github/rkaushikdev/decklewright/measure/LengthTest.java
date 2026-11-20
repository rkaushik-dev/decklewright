package io.github.rkaushikdev.decklewright.measure;

import static io.github.rkaushikdev.decklewright.measure.Length.maxLength;
import static io.github.rkaushikdev.decklewright.measure.Length.minLength;
import static io.github.rkaushikdev.decklewright.measure.Length.requireLength;
import static io.github.rkaushikdev.decklewright.measure.Length.toLength;
import static io.github.rkaushikdev.decklewright.measure.Unit.INCH;
import static io.github.rkaushikdev.decklewright.measure.Unit.MILLIMETRE;
import static io.github.rkaushikdev.decklewright.measure.Unit.POINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LengthTest {

    @Nested
    class BuildingALength {
        @Test
        void keepsTheUnitItWasWrittenIn() {
            Length l = Length.of(3, MILLIMETRE);
            assertThat(l.value()).isEqualTo(3);
            assertThat(l.unit()).isEqualTo(MILLIMETRE);
            // A record's components are final and these are a primitive and an enum, so nothing can change them.
            assertThat(Length.class.isRecord()).isTrue();
        }

        @Test
        void parsesTheStringAnOperatorTypes() {
            assertThat(Length.parse("0.25in").toJson()).isEqualTo(Map.of("value", 0.25, "unit", "in"));
        }

        @Test
        void hasAZeroThatStillCarriesAUnit() {
            assertThat(Length.zero().unit()).isEqualTo(MILLIMETRE);
            assertThat(Length.zero(POINT).isZero()).isTrue();
        }

        @Test
        void refusesAUnitItDoesNotKnow() {
            // A Java Length takes only a Unit, so the unknown label fails where it is read.
            assertThatThrownBy(() -> Length.of(3, Unit.parse("px")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("unknown unit px");
        }
    }

    @Nested
    class Conversion {
        @Test
        void returnsItselfWhenTheUnitAlreadyMatches() {
            Length l = Length.of(3, MILLIMETRE);
            assertThat(l.to(MILLIMETRE)).isSameAs(l);
        }

        @Test
        void convertsAndReportsInches() {
            assertThat(Length.of(25.4, MILLIMETRE).to(INCH).value()).isCloseTo(1, byLessThan(5e-13));
            assertThat(Length.of(1, INCH).inches()).isEqualTo(1);
            assertThat(Length.of(72, POINT).inches()).isCloseTo(1, byLessThan(5e-13));
        }
    }

    @Nested
    class Arithmetic {
        @Test
        void addsInTheUnitOfTheLeftOperand() {
            Length sum = Length.of(1, INCH).plus(Length.of(25.4, MILLIMETRE));
            assertThat(sum.unit()).isEqualTo(INCH);
            assertThat(sum.value()).isCloseTo(2, byLessThan(5e-10));
        }

        @Test
        void subtractsAndCanGoNegative() {
            Length diff = Length.of(3, MILLIMETRE).minus(Length.of(5, MILLIMETRE));
            assertThat(diff.value()).isEqualTo(-2);
            assertThat(diff.isNegative()).isTrue();
            assertThat(diff.abs().value()).isEqualTo(2);
            assertThat(diff.negated().value()).isEqualTo(2);
        }

        @Test
        void leavesAPositiveLengthAloneWhenTakingTheAbsoluteValue() {
            Length l = Length.of(3, MILLIMETRE);
            assertThat(l.abs()).isSameAs(l);
        }

        @Test
        void scalesByAPlainFactor() {
            assertThat(Length.of(3, MILLIMETRE).times(2).value()).isEqualTo(6);
            assertThat(Length.of(3, MILLIMETRE).times(0).isZero()).isTrue();
        }

        @Test
        void dividesOneLengthByAnotherAcrossUnits() {
            assertThat(Length.of(1, INCH).over(Length.of(25.4, MILLIMETRE))).isCloseTo(1, byLessThan(5e-10));
            assertThat(Length.of(12, INCH).over(Length.of(1, INCH))).isEqualTo(12);
        }

        @Test
        void refusesToDivideByZero() {
            assertThatThrownBy(() -> Length.of(1, INCH).over(Length.zero()))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("divide a length by zero");
        }

        @Test
        void refusesAnOperandThatIsNotALength() {
            // The operands are typed, so the number 3 and the string '3mm' cannot be passed; null can.
            assertThatThrownBy(() -> Length.of(1, INCH).plus(null))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("other must be a Length");
            assertThatThrownBy(() -> Length.of(1, INCH).minus(null))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("other must be a Length");
        }
    }

    @Nested
    class Comparison {
        @Test
        void ordersAcrossUnits() {
            assertThat(Length.of(1, INCH).compare(Length.of(25.4, MILLIMETRE))).isEqualTo(0);
            assertThat(Length.of(1, INCH).compare(Length.of(30, MILLIMETRE))).isEqualTo(-1);
            assertThat(Length.of(2, INCH).compare(Length.of(30, MILLIMETRE))).isEqualTo(1);
        }

        @Test
        void treatsEqualityAsExactUnlessAToleranceIsGiven() {
            Length a = Length.of(3, MILLIMETRE);
            Length b = Length.of(3.05, MILLIMETRE);
            assertThat(a.approxEquals(b)).isFalse();
            assertThat(a.approxEquals(b, Length.of(0.1, MILLIMETRE))).isTrue();
            assertThat(a.approxEquals(b, Length.of(-0.1, MILLIMETRE))).isTrue();
        }

        @Test
        void isNotEqualToABareNumber() {
            // approxEquals only takes a Length, so the bare number goes through equals, and null
            // through approxEquals.
            assertThat(Length.of(3, MILLIMETRE).equals(3.0)).isFalse();
            assertThat(Length.of(3, MILLIMETRE).approxEquals(null)).isFalse();
        }
    }

    @Nested
    class Printing {
        @Test
        void usesTheReportPrecisionOfItsOwnUnit() {
            assertThat(String.valueOf(Length.of(3.14159, MILLIMETRE))).isEqualTo("3.14 mm");
            assertThat(String.valueOf(Length.of(0.25, INCH))).isEqualTo("0.2500 in");
        }
    }

    @Nested
    class ToLength {
        @Test
        void takesTheThreeShapesAProfileMayHold() {
            Length l = Length.of(3, MILLIMETRE);
            assertThat(toLength(l)).isSameAs(l);
            assertThat(toLength("3mm").approxEquals(l)).isTrue();
            assertThat(toLength(Map.of("value", 3.0, "unit", "mm")).approxEquals(l))
                    .isTrue();
        }

        @Test
        void rejectsABadUnitInsideAPair() {
            assertThatThrownBy(() -> toLength(Map.of("value", 3.0, "unit", "px")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("unit must be one of");
        }

        @Test
        void rejectsAnythingElse() {
            assertThatThrownBy(() -> toLength(3))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read a length from that");
            assertThatThrownBy(() -> toLength(null))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read a length from that");
        }
    }

    @Nested
    class AssertLength {
        @Test
        void namesTheArgumentItWasChecking() {
            assertThatThrownBy(() -> requireLength("3mm", "bleed"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("bleed must be a Length");
        }
    }

    @Nested
    class MaxLengthAndMinLength {
        private final List<Length> lengths =
                List.of(Length.of(3, MILLIMETRE), Length.of(0.25, INCH), Length.of(12, POINT));

        @Test
        void picksAcrossUnits() {
            assertThat(maxLength(lengths).unit()).isEqualTo(INCH);
            assertThat(minLength(lengths).unit()).isEqualTo(MILLIMETRE);
        }

        @Test
        void refusesAnEmptyList() {
            assertThatThrownBy(() -> maxLength(List.of()))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no lengths to compare");
            assertThatThrownBy(() -> minLength(List.of()))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no lengths to compare");
        }
    }
}
