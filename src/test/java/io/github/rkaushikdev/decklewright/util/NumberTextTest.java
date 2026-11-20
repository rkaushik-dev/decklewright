package io.github.rkaushikdev.decklewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NumberTextTest {

    @Nested
    class Of {
        @ParameterizedTest(name = "{0} is written {1}")
        @CsvSource({
            "3, 3",
            "3.5, 3.5",
            "-0.25, -0.25",
            "0.30000000000000004, 0.30000000000000004",
            "100, 100",
            "10000000, 10000000",
            "123456789012, 123456789012",
            "12345.678, 12345.678",
            "1e20, 100000000000000000000",
            "1e21, 1e+21",
            "1.5e21, 1.5e+21",
            "0.000001, 0.000001",
            "1e-7, 1e-7",
            "1.5e-7, 1.5e-7",
            "9007199254740992, 9007199254740992",
            "1.7976931348623157e308, 1.7976931348623157e+308",
            "4.9e-324, 5e-324",
            "-1e-7, -1e-7"
        })
        void writesTheShortestRoundTrippingDecimal(double value, String expected) {
            assertThat(NumberText.of(value)).isEqualTo(expected);
        }

        @Test
        void writesTheSpecialValuesByName() {
            assertThat(NumberText.of(Double.NaN)).isEqualTo("NaN");
            assertThat(NumberText.of(Double.POSITIVE_INFINITY)).isEqualTo("Infinity");
            assertThat(NumberText.of(Double.NEGATIVE_INFINITY)).isEqualTo("-Infinity");
            assertThat(NumberText.of(-0.0)).isEqualTo("0");
        }

        @Test
        void writesBoxedWholeNumbersExactly() {
            assertThat(NumberText.of(Integer.valueOf(-3))).isEqualTo("-3");
            assertThat(NumberText.of(Long.valueOf(9007199254740993L))).isEqualTo("9007199254740993");
            assertThat(NumberText.of(Short.valueOf((short) 7))).isEqualTo("7");
            assertThat(NumberText.of(Byte.valueOf((byte) 8))).isEqualTo("8");
            assertThat(NumberText.of(Double.valueOf(2.0))).isEqualTo("2");
            assertThat(NumberText.of(new BigDecimal("0.5"))).isEqualTo("0.5");
            assertThat(NumberText.of(42L)).isEqualTo("42");
        }

        @Test
        void sumsThatLandOnOneDigitStayOneDigit() {
            assertThat(NumberText.of(0.1 * 3)).isEqualTo("0.30000000000000004");
            assertThat(NumberText.of(0.3)).isEqualTo("0.3");
            assertThat(NumberText.of(1e23)).isEqualTo("1e+23");
        }
    }

    @Nested
    class Fixed {
        @ParameterizedTest(name = "{0} to {1} places is {2}")
        @CsvSource({
            "1.005, 2, 1.00",
            "1.255, 2, 1.25",
            "10.235, 2, 10.23",
            "2.5, 0, 3",
            "-1.5, 0, -2",
            "0, 2, 0.00",
            "-0.0001, 2, -0.00",
            "123.456, 1, 123.5",
            "7, 3, 7.000",
            "1e21, 2, 1e+21"
        })
        void roundsTheExactValueHalfAwayFromZero(double value, int digits, String expected) {
            assertThat(NumberText.fixed(value, digits)).isEqualTo(expected);
        }

        @Test
        void writesNotANumberAndTheInfinities() {
            assertThat(NumberText.fixed(Double.NaN, 2)).isEqualTo("NaN");
            assertThat(NumberText.fixed(Double.POSITIVE_INFINITY, 2)).isEqualTo("Infinity");
        }

        @Test
        void refusesAPlaceCountOutOfRange() {
            assertThatThrownBy(() -> NumberText.fixed(1, -1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> NumberText.fixed(1, 101)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
