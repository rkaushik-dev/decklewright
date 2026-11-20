package io.github.rkaushikdev.decklewright.geom;

import static io.github.rkaushikdev.decklewright.geom.Fitting.alignIn;
import static io.github.rkaushikdev.decklewright.geom.Fitting.fitIn;
import static io.github.rkaushikdev.decklewright.geom.Fitting.fitScale;
import static io.github.rkaushikdev.decklewright.geom.Fitting.forceAspect;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FitTest {
    private final Rect bound = Rect.of(0, 0, 200, 100);

    @Nested
    class AlignIn {
        @Test
        void centresByDefault() {
            assertThat(alignIn(Size.of(100, 50), bound).toJson())
                    .isEqualTo(Map.of("x", 50.0, "y", 25.0, "width", 100.0, "height", 50.0));
        }

        @Test
        void honoursEachAlignment() {
            assertThat(alignIn(Size.of(100, 50), bound, Align.START, Align.START)
                            .origin()
                            .toJson())
                    .isEqualTo(Map.of("x", 0.0, "y", 0.0));
            assertThat(alignIn(Size.of(100, 50), bound, Align.END, Align.END)
                            .origin()
                            .toJson())
                    .isEqualTo(Map.of("x", 100.0, "y", 50.0));
        }

        @Test
        void offsetsFromTheBoundRatherThanTheOrigin() {
            Rect moved = alignIn(Size.of(100, 50), Rect.of(10, 10, 200, 100), Align.START, Align.CENTRE);
            assertThat(moved.x()).isEqualTo(10);
        }

        @Test
        void refusesAnAlignmentItDoesNotKnow() {
            // An alignment arrives as a string from a profile, so the unknown one is refused as it is read.
            assertThatThrownBy(() -> alignIn(Size.of(1, 1), bound, Align.parse("middle", "alignX"), Align.CENTRE))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("alignX must be one of");
        }
    }

    @Nested
    class FitIn {
        @Test
        void containsByDefaultAndLeavesMarginOnOneAxis() {
            Rect placed = fitIn(Size.of(100, 100), bound);
            assertThat(placed.toJson()).isEqualTo(Map.of("x", 50.0, "y", 0.0, "width", 100.0, "height", 100.0));
        }

        @Test
        void coversAndOverhangs() {
            Rect placed = fitIn(Size.of(100, 100), bound, FitOptions.defaults().withMode(FitMode.COVER));
            assertThat(placed.width()).isEqualTo(200);
            assertThat(placed.height()).isEqualTo(200);
            assertThat(placed.y()).isEqualTo(-50);
        }

        @Test
        void stretchesToTheBoundExactly() {
            assertThat(fitIn(Size.of(100, 100), bound, FitOptions.defaults().withMode(FitMode.STRETCH))
                            .toJson())
                    .isEqualTo(Map.of("x", 0.0, "y", 0.0, "width", 200.0, "height", 100.0));
        }

        @Test
        void placesWithoutScalingUnderNone() {
            assertThat(fitIn(Size.of(40, 20), bound, FitOptions.defaults().withMode(FitMode.NONE))
                            .toJson())
                    .isEqualTo(Map.of("x", 80.0, "y", 40.0, "width", 40.0, "height", 20.0));
        }

        @Test
        void refusesToUpscaleWhenToldNotTo() {
            Size small = Size.of(50, 25);
            assertThat(fitIn(small, bound).width()).isEqualTo(200);
            assertThat(fitIn(small, bound, FitOptions.defaults().withAllowUpscale(false))
                            .width())
                    .isEqualTo(50);
        }

        @Test
        void stillDownscalesWhenUpscalingIsOff() {
            Size big = Size.of(400, 200);
            assertThat(fitIn(big, bound, FitOptions.defaults().withAllowUpscale(false))
                            .width())
                    .isEqualTo(200);
        }

        @Test
        void givesAnEmptyRectForAnEmptySourceOrBound() {
            assertThat(fitIn(Size.empty(), bound).isEmpty()).isTrue();
            assertThat(fitIn(Size.of(10, 10), Rect.empty()).isEmpty()).isTrue();
        }

        @Test
        void refusesAModeItDoesNotKnow() {
            // A mode arrives as a string from a profile, so the unknown one is refused as it is read.
            assertThatThrownBy(() ->
                            fitIn(Size.of(1, 1), bound, FitOptions.defaults().withMode(FitMode.parse("squash"))))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("mode must be one of");
        }
    }

    @Nested
    class FitScale {
        @Test
        void matchesWhatFitInDid() {
            assertThat(fitScale(Size.of(100, 100), bound.size())).isEqualTo(1);
            assertThat(fitScale(Size.of(100, 100), bound.size(), FitMode.COVER)).isEqualTo(2);
            assertThat(fitScale(Size.of(100, 100), bound.size(), FitMode.NONE)).isEqualTo(1);
            assertThat(fitScale(Size.of(400, 200), bound.size())).isEqualTo(0.5);
        }

        @Test
        void isZeroWhenEitherSideIsEmpty() {
            assertThat(fitScale(Size.empty(), bound.size())).isEqualTo(0);
            assertThat(fitScale(bound.size(), Size.empty())).isEqualTo(0);
        }
    }

    @Nested
    class ForceAspect {
        private final Rect wide = Rect.of(0, 0, 200, 100);

        @Test
        void growsTheShortAxisByDefault() {
            Rect squared = forceAspect(wide, 1);
            assertThat(squared.width()).isEqualTo(200);
            assertThat(squared.height()).isEqualTo(200);
            assertThat(squared.centre().approxEquals(wide.centre())).isTrue();
        }

        @Test
        void shrinksTheLongAxisWhenAsked() {
            Rect squared = forceAspect(wide, 1, AspectDirection.SHRINK);
            assertThat(squared.width()).isEqualTo(100);
            assertThat(squared.height()).isEqualTo(100);
        }

        @Test
        void worksTheOtherWayForATallRect() {
            Rect tall = Rect.of(0, 0, 100, 200);
            assertThat(forceAspect(tall, 1).width()).isEqualTo(200);
            assertThat(forceAspect(tall, 1, AspectDirection.SHRINK).height()).isEqualTo(100);
        }

        @Test
        void leavesARectThatAlreadyMatches() {
            Rect square = Rect.of(0, 0, 100, 100);
            assertThat(forceAspect(square, 1).approxEquals(square)).isTrue();
        }

        @Test
        void passesAnEmptyRectThrough() {
            assertThat(forceAspect(Rect.empty(), 1.5).isEmpty()).isTrue();
        }

        @Test
        void refusesADirectionItDoesNotKnow() {
            // A direction arrives as a string from a profile, so the unknown one is refused as it is read.
            assertThatThrownBy(() -> forceAspect(wide, 1, AspectDirection.parse("sideways")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("direction must be one of");
        }
    }
}
