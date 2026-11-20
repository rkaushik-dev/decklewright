package io.github.rkaushikdev.decklewright.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IsoTimeTest {

    @Test
    void alwaysWritesMilliseconds() {
        assertThat(IsoTime.format(Instant.parse("2025-12-18T12:00:00Z"))).isEqualTo("2025-12-18T12:00:00.000Z");
        assertThat(IsoTime.format(Instant.parse("2025-12-18T12:00:00.25Z"))).isEqualTo("2025-12-18T12:00:00.250Z");
    }

    @Test
    void truncatesAnythingFinerThanAMillisecond() {
        assertThat(IsoTime.format(Instant.parse("2025-12-18T12:00:00.123999Z"))).isEqualTo("2025-12-18T12:00:00.123Z");
    }
}
