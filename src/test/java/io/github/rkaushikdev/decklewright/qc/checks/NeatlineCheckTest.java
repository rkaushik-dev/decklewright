package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.runCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.NeatlineCheck.NEATLINE_LADDER;
import static io.github.rkaushikdev.decklewright.qc.checks.NeatlineCheck.neatlineCheck;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.detect.Neatline;
import io.github.rkaushikdev.decklewright.detect.Side;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.profile.Fields;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.qc.Check;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.Need;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.QcRunner;
import io.github.rkaushikdev.decklewright.qc.Rung;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NeatlineCheckTest {

    static QcContext missing(Side... sides) {
        return QcContext.empty().withNeatline(new Neatline(Rect.of(10, 10, 200, 160), Map.of(), List.of(sides)));
    }

    @Nested
    class TheCheck {
        @Test
        void givesFourStarsToABorderFoundAllRound() {
            Finding out = runCheck(neatlineCheck(), missing());
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.note()).isEqualTo("the border was found on all four sides");
        }

        @Test
        void dropsToTwoStarsForOneSideCutToThePaper() {
            Finding out = runCheck(neatlineCheck(), missing(Side.BOTTOM));
            assertThat(out.measured()).isEqualTo(1);
            assertThat(out.stars()).isEqualTo(2);
            assertThat(out.note()).isEqualTo("no border on the bottom, 1 side cut to the paper edge");
        }

        @Test
        void givesNothingOnceThreeSidesAreGone() {
            assertThat(runCheck(neatlineCheck(), missing(Side.TOP, Side.LEFT)).stars())
                    .isEqualTo(1);
            Finding out = runCheck(neatlineCheck(), missing(Side.TOP, Side.RIGHT, Side.LEFT));
            assertThat(out.stars()).isZero();
            assertThat(out.note()).isEqualTo("no border on the top and right and left, 3 sides cut to the paper edge");
        }

        @Test
        void declinesForACaptureNeverSearchedForABorder() {
            Finding out = runCheck(neatlineCheck(), QcContext.empty());
            assertThat(out.ran()).isFalse();
            assertThat(out.note()).isEqualTo("nothing to measure, no neatline");
        }

        @Test
        void skipsAStarOnItsLadderSinceOneMissingSideIsAlreadyAVisibleFault() {
            assertThat(NEATLINE_LADDER.stream().map(Rung::stars).toList()).containsExactly(4, 2, 1);
        }
    }

    @Nested
    class TheContext {
        @Test
        void holdsTheNeatlineAndLetsItGo() {
            QcContext held = missing(Side.TOP);
            assertThat(held.has(Need.NEATLINE)).isTrue();
            assertThat(held.without(Need.NEATLINE).has(Need.NEATLINE)).isFalse();
            assertThat(held.without(Need.NEATLINE)).isEqualTo(QcContext.empty());
        }

        @Test
        void changesOneEntryAndKeepsTheRest() {
            QcContext held = missing().withSpread(0.4).withSheet(Rect.of(0, 0, 5, 5));
            QcContext changed = held.withSpread(0.2);
            assertThat(changed.spread()).hasValue(0.2);
            assertThat(changed.sheet()).isEqualTo(held.sheet());
            assertThat(changed.neatline()).isEqualTo(held.neatline());
        }
    }

    @Nested
    class InTheStandardRun {
        private List<String> names(Profile profile) {
            return QcRunner.checksFor(profile, QcRunner.standardChecks()).stream()
                    .map(Check::name)
                    .toList();
        }

        @Test
        void comesStraightAfterSkewAgreementForAProfileThatCropsToTheNeatline() {
            List<String> names = names(new ProfileRegistry().get("quadrangles"));
            assertThat(names.get(names.indexOf("skewAgreement") + 1)).isEqualTo("neatline");
        }

        @Test
        void isLeftOutWhereTheCropIsTheSheet() {
            assertThat(names(new ProfileRegistry().get("atlas-plates"))).doesNotContain("neatline");
        }

        @Test
        void runsAlongsideTheToneCheckWhenAProfileAsksForBoth() {
            Profile both = Fields.parseProfile(Map.of(
                    "name",
                    "both",
                    "capture",
                    Map.of("expectNeatline", true),
                    "crop",
                    Map.of("anchor", "neatline"),
                    "qc",
                    Map.of("toneAims", List.of(20, 240))));
            List<String> names = names(both);
            assertThat(names).contains("neatline", "toneResponse");
            assertThat(names).hasSize(QcRunner.standardChecks().size() + 2);
        }
    }
}
