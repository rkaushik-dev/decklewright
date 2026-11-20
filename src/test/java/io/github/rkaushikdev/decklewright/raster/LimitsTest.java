package io.github.rkaushikdev.decklewright.raster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What the Java layer refuses outright where the original would have gone on and failed somewhere
 * less helpful: a buffer larger than an array can be, prepared tables of the wrong size, and a
 * buffer of the wrong storage.
 */
class LimitsTest {

    @Test
    void refusesASurfaceLargerThanOneBufferCanHoldBeforeAllocatingIt() {
        assertThatThrownBy(() -> Surface.create(100_000, 100_000, Layout.RGB))
                .isInstanceOf(UnsupportedError.class)
                .hasMessage("that surface holds more samples than one buffer can")
                .extracting(error -> ((UnsupportedError) error).details().get("samples"))
                .isEqualTo(30_000_000_000L);
    }

    @Test
    void sizesARowAndASurfaceInBytesPastTheRangeOfAnInt() {
        assertThat(Layouts.rowBytes(1_500_000_000, Layout.RGBA, 16)).isEqualTo(12_000_000_000L);
        assertThat(Conversions.byteSize(21_600, 14_400, Layout.RGBA, 16)).isEqualTo(2_488_320_000L);
    }

    @Test
    void refusesPreparedTablesOfTheWrongSize() {
        assertThatThrownBy(() -> new IntegralImage(new double[3], new double[4], 1, 1))
                .isInstanceOf(BadArgumentError.class)
                .hasMessage("the tables do not match the stated extent");
        double[] sums = {0, 0, 0, 5};
        IntegralImage table = new IntegralImage(sums, new double[4], 1, 1);
        sums[3] = 9;
        assertThat(table.sum(Rect.of(0, 0, 1, 1))).isEqualTo(5);
    }

    @Test
    void namesTheStorageAWrappedBufferShouldHaveHad() {
        assertThatThrownBy(() -> Surface.wrap(Uint16Samples.allocate(3), 1, 1, Layout.RGB, 8))
                .isInstanceOf(BadArgumentError.class)
                .extracting(error -> ((BadArgumentError) error).details())
                .isEqualTo(Map.of("depth", 8, "got", "Uint16Samples"));
    }
}
