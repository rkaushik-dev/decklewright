package io.github.rkaushikdev.decklewright.measure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WhitespaceTest {
    private static final char NO_BREAK_SPACE = 0xA0;
    private static final char IDEOGRAPHIC_SPACE = 0x3000;
    private static final char BYTE_ORDER_MARK = 0xFEFF;
    private static final char UNIT_SEPARATOR = 0x1F;
    private static final char NEXT_LINE = 0x85;
    private static final char ZERO_WIDTH_SPACE = 0x200B;

    @Test
    void trimsTheUnicodeSpacesACuratorPastes() {
        String pasted = NO_BREAK_SPACE + " 4 mm\t" + IDEOGRAPHIC_SPACE + BYTE_ORDER_MARK;
        assertThat(Whitespace.trim(pasted)).isEqualTo("4 mm");
        assertThat(Whitespace.trim(" \n ")).isEmpty();
    }

    @Test
    void keepsWhatIsNotBlankThoughTrimOrStripWouldTakeIt() {
        assertThat(Whitespace.trim(UNIT_SEPARATOR + "4mm")).isEqualTo(UNIT_SEPARATOR + "4mm");
        assertThat(Whitespace.trim("4mm" + NEXT_LINE)).isEqualTo("4mm" + NEXT_LINE);
        assertThat(Whitespace.trim(ZERO_WIDTH_SPACE + "4mm")).isEqualTo(ZERO_WIDTH_SPACE + "4mm");
    }

    @Test
    void hasACharacterClassForExactlyTheBlankCharacters() {
        Pattern blank = Pattern.compile(Whitespace.CLASS);
        int count = 0;
        for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c += 1) {
            boolean isBlank = Whitespace.isBlank((char) c);
            assertThat(blank.matcher(String.valueOf((char) c)).matches())
                    .as("U+%04X", c)
                    .isEqualTo(isBlank);
            count += isBlank ? 1 : 0;
        }
        assertThat(count).isEqualTo(25);
    }

    @Test
    void letsTheReadersSkipANoBreakSpace() {
        assertThat(Units.parseLength("4" + NO_BREAK_SPACE + "mm")).isEqualTo(Length.of(4, Unit.MILLIMETRE));
        assertThat(Tolerance.parse(NO_BREAK_SPACE + "+3 /" + NO_BREAK_SPACE + "-1" + NO_BREAK_SPACE))
                .isEqualTo(new Tolerance(1, 3));
    }
}
