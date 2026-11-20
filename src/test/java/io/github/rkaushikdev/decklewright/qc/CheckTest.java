package io.github.rkaushikdev.decklewright.qc;

import static io.github.rkaushikdev.decklewright.qc.Check.MAX_STARS;
import static io.github.rkaushikdev.decklewright.qc.Check.missingFrom;
import static io.github.rkaushikdev.decklewright.qc.Check.requireNoDuplicates;
import static io.github.rkaushikdev.decklewright.qc.Check.runCheck;
import static io.github.rkaushikdev.decklewright.qc.Check.runChecks;
import static io.github.rkaushikdev.decklewright.qc.Check.starsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.QcError;
import io.github.rkaushikdev.decklewright.detect.TargetFinding;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.profile.Settings;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CheckTest {

    /** A check that needs nothing and always measures the given level. */
    static Check always(int stars) {
        return Check.of("always" + stars, context -> new Measurement(stars, stars, "measured"));
    }

    /** A context holding every entry a check can ask for. */
    static QcContext everything() {
        Surface surface = Surface.create(1, 1);
        return QcContext.empty()
                .withSurface(surface)
                .withSheet(Rect.of(0, 0, 1, 1))
                .withAvoid(List.of())
                .withBed(surface.bounds())
                .withCrop(surface.bounds())
                .withTarget(new TargetFinding(Rect.empty(), List.of(), 0, true))
                .withSampling(Sampling.of(400))
                .withSkew(Angle.zero())
                .withSpread(0)
                .withLimits(Settings.qcLimits(new ProfileRegistry().get("house")));
    }

    @Nested
    class CheckBehaviour {
        @Test
        void buildsAFrozenCheck() {
            Check made = always(4);
            // a final class whose state is all final fields has no way to change
            assertThat(Modifier.isFinal(Check.class.getModifiers())).isTrue();
            List<Field> state = Arrays.stream(Check.class.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .toList();
            assertThat(state).isNotEmpty();
            for (Field field : state) {
                assertThat(Modifier.isFinal(field.getModifiers()))
                        .as(field.getName())
                        .isTrue();
            }
            assertThatThrownBy(() -> made.needs().add(Need.SURFACE)).isInstanceOf(UnsupportedOperationException.class);
            assertThat(made.name()).isEqualTo("always4");
        }

        @Test
        void refusesACheckWithNoNameOrNoBody() {
            assertThatThrownBy(() -> Check.of("", context -> new Measurement(1, 1, "x")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("non empty");
            assertThatThrownBy(() -> Check.of("x", null))
                    .isInstanceOf(QcError.class)
                    .hasMessageFindingMatch("nothing to run");
        }
    }

    @Nested
    class MissingFrom {
        @Test
        void namesWhatTheContextDoesNotHave() {
            QcContext withSurface = QcContext.empty().withSurface(Surface.create(1, 1));
            assertThat(missingFrom(withSurface, List.of(Need.SURFACE, Need.TARGET)))
                    .isEqualTo(List.of(Need.TARGET));
            // a target that was never set is empty, which is how absent reads
            assertThat(missingFrom(withSurface, List.of(Need.TARGET))).isEqualTo(List.of(Need.TARGET));
            assertThat(missingFrom(withSurface, List.of())).isEqualTo(List.of());

            List<Need> all = Arrays.asList(Need.values());
            assertThat(missingFrom(everything(), all)).isEmpty();
            for (Need need : all) {
                assertThat(missingFrom(everything().without(need), all))
                        .as(need.label())
                        .isEqualTo(List.of(need));
            }
        }
    }

    @Nested
    class RunCheck {
        @Test
        void passesACheckThatReachesTheStarFloor() {
            Finding out = runCheck(always(4), QcContext.empty(), 3);
            assertThat(out.ran()).isTrue();
            assertThat(out.passed()).isTrue();
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.limit()).isEqualTo("3 stars");
        }

        @Test
        void failsACheckThatDoesNot() {
            Finding out = runCheck(always(2), QcContext.empty(), 3);
            assertThat(out.ran()).isTrue();
            assertThat(out.passed()).isFalse();
        }

        @Test
        void separatesCouldNotRunFromFailed() {
            Check needy = Check.of("needy", List.of(Need.TARGET), context -> new Measurement(1, 4, "x"));
            Finding out = runCheck(needy, QcContext.empty());
            assertThat(out.ran()).isFalse();
            assertThat(out.passed()).isFalse();
            assertThat(out.note()).isEqualTo("nothing to measure, no target");
            assertThat(out.measured()).isNaN();
        }

        @Test
        void runsACheckWhoseNeedsAreMet() {
            // the body reads the range off a hand-made target
            Check needy = Check.of(
                    "needy",
                    List.of(Need.TARGET),
                    context -> new Measurement(context.target().orElseThrow().range(), 4, "read the target"));
            QcContext context = QcContext.empty().withTarget(new TargetFinding(Rect.empty(), List.of(), 7, true));
            assertThat(runCheck(needy, context).measured()).isEqualTo(7);
        }

        @Test
        void catchesACheckThatThrowsRatherThanStoppingTheBatch() {
            Check angry = Check.of("angry", context -> {
                throw new IllegalStateException("the patch is off the capture");
            });
            Finding out = runCheck(angry, QcContext.empty());
            assertThat(out.ran()).isFalse();
            assertThat(out.note()).isEqualTo("the patch is off the capture");
        }

        @Test
        void usesThreeStarsAsTheFloorByDefault() {
            assertThat(runCheck(always(3), QcContext.empty()).passed()).isTrue();
            assertThat(runCheck(always(2), QcContext.empty()).passed()).isFalse();
        }
    }

    @Nested
    class RunChecks {
        @Test
        void runsEachOneAndKeepsTheOrder() {
            List<Finding> out = runChecks(List.of(always(4), always(2)), QcContext.empty(), 3);
            assertThat(out.stream().map(Finding::name)).containsExactly("always4", "always2");
            assertThat(out.stream().map(Finding::passed)).containsExactly(true, false);
        }

        @Test
        void givesAnEmptyListForNoChecks() {
            assertThat(runChecks(List.of(), QcContext.empty())).isEqualTo(List.of());
        }
    }

    @Nested
    class StarsFor {
        private final List<Rung> ladder = List.of(new Rung(4, 1), new Rung(3, 2), new Rung(2, 4), new Rung(1, 8));

        @Test
        void walksDownTheLadderForALowerIsBetterMeasure() {
            assertThat(starsFor(0.5, ladder)).isEqualTo(4);
            assertThat(starsFor(1.5, ladder)).isEqualTo(3);
            assertThat(starsFor(3, ladder)).isEqualTo(2);
            assertThat(starsFor(20, ladder)).isEqualTo(0);
        }

        @Test
        void takesTheLimitsInclusively() {
            assertThat(starsFor(1, ladder)).isEqualTo(4);
            assertThat(starsFor(2, ladder)).isEqualTo(3);
        }

        @Test
        void walksTheOtherWayForAHigherIsBetterMeasure() {
            List<Rung> snr = List.of(new Rung(4, 40), new Rung(3, 30), new Rung(2, 20));
            assertThat(starsFor(45, snr, true)).isEqualTo(4);
            assertThat(starsFor(31, snr, true)).isEqualTo(3);
            assertThat(starsFor(5, snr, true)).isEqualTo(0);
        }

        @Test
        void givesNoStarsForAMeasurementThatIsNotANumber() {
            assertThat(starsFor(Double.NaN, ladder)).isEqualTo(0);
        }

        @Test
        void givesTheTopRungToAnInfiniteReadingThatIsBetterWhenHigher() {
            List<Rung> snr = List.of(new Rung(4, 40), new Rung(3, 30));
            assertThat(starsFor(Double.POSITIVE_INFINITY, snr, true)).isEqualTo(4);
            assertThat(starsFor(Double.POSITIVE_INFINITY, ladder)).isEqualTo(0);
        }

        @Test
        void neverAwardsMoreThanTheMaximum() {
            assertThat(MAX_STARS).isEqualTo(4);
            assertThat(starsFor(0, ladder)).isLessThanOrEqualTo(MAX_STARS);
        }
    }

    @Nested
    class RequireNoDuplicates {
        @Test
        void catchesTheSameCheckTwice() {
            assertThatThrownBy(() -> requireNoDuplicates(List.of(always(4), always(4))))
                    .isInstanceOf(QcError.class)
                    .hasMessageFindingMatch("the always4 check is in the set twice");
            assertThatCode(() -> requireNoDuplicates(List.of(always(4), always(3))))
                    .doesNotThrowAnyException();
        }
    }
}
