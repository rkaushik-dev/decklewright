package io.github.rkaushikdev.decklewright.measure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Covers the readers skipping the blank space a pasted figure brings with it. */
class PastedBlanksTest {
    private static final char NO_BREAK_SPACE = 0xA0;

    @Test
    void letsTheReadersSkipANoBreakSpace() {
        assertThat(Units.parseLength("4" + NO_BREAK_SPACE + "mm")).isEqualTo(Length.of(4, Unit.MILLIMETRE));
        assertThat(Tolerance.parse(NO_BREAK_SPACE + "+3 /" + NO_BREAK_SPACE + "-1" + NO_BREAK_SPACE))
                .isEqualTo(new Tolerance(1, 3));
    }
}
