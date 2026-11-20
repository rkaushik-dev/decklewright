package io.github.rkaushikdev.decklewright.measure;

import static io.github.rkaushikdev.decklewright.measure.Unit.CENTIMETRE;
import static io.github.rkaushikdev.decklewright.measure.Unit.INCH;
import static io.github.rkaushikdev.decklewright.measure.Unit.METRE;
import static io.github.rkaushikdev.decklewright.measure.Unit.MILLIMETRE;
import static io.github.rkaushikdev.decklewright.measure.Unit.PICA;
import static io.github.rkaushikdev.decklewright.measure.Unit.POINT;
import static io.github.rkaushikdev.decklewright.measure.Units.SHEET_SIZES;
import static io.github.rkaushikdev.decklewright.measure.Units.UNITS;
import static io.github.rkaushikdev.decklewright.measure.Units.convert;
import static io.github.rkaushikdev.decklewright.measure.Units.formatLength;
import static io.github.rkaushikdev.decklewright.measure.Units.fromInches;
import static io.github.rkaushikdev.decklewright.measure.Units.isUnit;
import static io.github.rkaushikdev.decklewright.measure.Units.parseLength;
import static io.github.rkaushikdev.decklewright.measure.Units.sheetSize;
import static io.github.rkaushikdev.decklewright.measure.Units.toInches;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UnitsTest {

    @Nested
    class TheUnitTable {
        @Test
        void isFrozenAndHasNoDuplicates() {
            assertThatThrownBy(() -> UNITS.add(INCH)).isInstanceOf(UnsupportedOperationException.class);
            assertThat(Set.copyOf(UNITS)).hasSameSizeAs(UNITS);
        }

        @Test
        void recognisesEveryListedUnitAndNothingElse() {
            for (Unit unit : UNITS) {
                assertThat(isUnit(unit)).isTrue();
                assertThat(isUnit(unit.label())).isTrue();
            }
            assertThat(isUnit("px")).isFalse();
            assertThat(isUnit("MM")).isFalse();
            assertThat(isUnit(null)).isFalse();
        }

        @Test
        void namesWhatItKnowsWhenItRejectsAUnit() {
            assertThatThrownBy(() -> Unit.parse("furlong")).isInstanceOfSatisfying(BadArgumentError.class, err -> {
                assertThat(err.getMessage()).isEqualTo("unknown unit furlong");
                assertThat(err.details().get("known"))
                        .isEqualTo(UNITS.stream().map(Unit::label).toList());
            });
        }
    }

    @Nested
    class ToInchesAndFromInches {
        @Test
        void usesTheExactInchDefinitions() {
            assertThat(toInches(25.4, MILLIMETRE)).isCloseTo(1, byLessThan(5e-13));
            assertThat(toInches(2.54, CENTIMETRE)).isCloseTo(1, byLessThan(5e-13));
            assertThat(toInches(72, POINT)).isCloseTo(1, byLessThan(5e-13));
            assertThat(toInches(6, PICA)).isCloseTo(1, byLessThan(5e-13));
            assertThat(toInches(1, METRE)).isCloseTo(39.3700787, byLessThan(5e-7));
        }

        @Test
        void roundTripsThroughTheInch() {
            for (Unit unit : UNITS) {
                assertThat(fromInches(toInches(17, unit), unit)).isCloseTo(17, byLessThan(5e-11));
            }
        }
    }

    @Nested
    class Convert {
        @Test
        void shortCircuitsWhenTheUnitsMatch() {
            assertThat(convert(3.25, MILLIMETRE, MILLIMETRE)).isEqualTo(3.25);
        }

        @Test
        void walksAPlateWidthFromInchesToMillimetres() {
            assertThat(convert(17, INCH, MILLIMETRE)).isCloseTo(431.8, byLessThan(5e-10));
            assertThat(convert(431.8, MILLIMETRE, INCH)).isCloseTo(17, byLessThan(5e-10));
        }

        @Test
        void goesBetweenThePrinterUnits() {
            assertThat(convert(1, PICA, POINT)).isCloseTo(12, byLessThan(5e-10));
        }

        @Test
        void refusesAValueThatIsNotANumber() {
            // Java's types will not take the string '17', so not-a-number stands in for it.
            assertThatThrownBy(() -> convert(Double.NaN, INCH, MILLIMETRE))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("value must be a finite number");
        }
    }

    @Nested
    class ParseLength {
        @Test
        void readsWhatAnOperatorTypes() {
            assertThat(parseLength("12.5mm")).isEqualTo(Length.of(12.5, MILLIMETRE));
            assertThat(parseLength(" 12.5 mm ")).isEqualTo(Length.of(12.5, MILLIMETRE));
            assertThat(parseLength(".5in")).isEqualTo(Length.of(0.5, INCH));
            assertThat(parseLength("-3pt")).isEqualTo(Length.of(-3, POINT));
            assertThat(parseLength("17IN")).isEqualTo(Length.of(17, INCH));
        }

        @Test
        void refusesABareNumberRatherThanAssumingAUnit() {
            assertThatThrownBy(() -> parseLength("12"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read 12 as a length");
        }

        @Test
        void refusesJunkAndUnknownUnits() {
            assertThatThrownBy(() -> parseLength("mm"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read");
            assertThatThrownBy(() -> parseLength("12 furlong"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("unknown unit furlong");
            assertThatThrownBy(() -> parseLength("12mm extra"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read");
        }
    }

    @Nested
    class FormatLength {
        @Test
        void usesTheReportPrecisionForTheUnit() {
            assertThat(formatLength(431.8004, MILLIMETRE)).isEqualTo("431.80 mm");
            assertThat(formatLength(17.00004, INCH)).isEqualTo("17.0000 in");
        }

        @Test
        void honoursAnExplicitPlaceCount() {
            assertThat(formatLength(431.8004, MILLIMETRE, 0)).isEqualTo("432 mm");
        }
    }

    @Nested
    class SheetSize {
        @Test
        void hasAFrozenTableOfTheNominalSizes() {
            assertThatThrownBy(() -> SHEET_SIZES.put("tabloid", new PhysicalSize(11, 17, INCH)))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(SHEET_SIZES.keySet()).contains("quad75");
        }

        @Test
        void answersInInchesByDefault() {
            assertThat(sheetSize("archD")).isEqualTo(new PhysicalSize(24, 36, INCH));
        }

        @Test
        void convertsOnTheWayOut() {
            PhysicalSize size = sheetSize("atlasPlate", MILLIMETRE);
            assertThat(size.width()).isCloseTo(431.8, byLessThan(5e-7));
            assertThat(size.height()).isCloseTo(355.6, byLessThan(5e-7));
            assertThat(size.unit()).isEqualTo(MILLIMETRE);
        }

        @Test
        void listsTheKnownNamesWhenItCannotFindOne() {
            assertThatThrownBy(() -> sheetSize("tabloid")).isInstanceOfSatisfying(BadArgumentError.class, err -> {
                assertThat(err.getMessage()).isEqualTo("no nominal size called tabloid");
                assertThat(err.details().get("known")).asInstanceOf(LIST).contains("archE");
            });
        }
    }
}
