package io.github.rkaushikdev.decklewright.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.PipelineError;
import io.github.rkaushikdev.decklewright.profile.Fields;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.QcResult;
import io.github.rkaushikdev.decklewright.store.Folder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** What a lab does with a result: the policy, where it comes from, and the decisions it makes. */
class PolicyTest {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();

    /** A grading at a level, with the check holding it down, the checks that could not run, and the findings. */
    private static QcResult grading(int stars, String worst, List<String> missing, List<Finding> findings) {
        return new QcResult(findings, stars, Optional.ofNullable(worst), missing, stars >= 3, 3);
    }

    private static QcResult grading(int stars) {
        return grading(stars, null, List.of(), List.of());
    }

    private static QcResult grading() {
        return grading(4);
    }

    /** A finding that ran or did not, at a level. */
    private static Finding finding(String name, boolean ran, int stars) {
        return new Finding(name, ran, ran && stars >= 3, ran ? stars : Double.NaN, stars, "", "");
    }

    @Nested
    class PolicyBehaviour {
        @Test
        void isTheHouseOneWhenNothingIsSaid() {
            assertThat(Policies.policy()).isEqualTo(Policies.HOUSE_POLICY);
        }

        @Test
        void takesAChangeWithoutTouchingTheRest() {
            Policy strict = Policies.policy().withMinimumStars(4);
            assertThat(strict.minimumStars()).isEqualTo(4);
            assertThat(strict.overwrite()).isEqualTo(Policies.HOUSE_POLICY.overwrite());
        }

        @Test
        void isFrozenSoAStageCannotChangeTheRulesMidRun() {
            assertThat(Policy.class.isRecord()).isTrue();
            assertThatThrownBy(() -> Policies.policy().holdOn().add("noise"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void copiesTheHoldListRatherThanSharingIt() {
            List<String> holdOn = new ArrayList<>(List.of("skew"));
            Policy rules = Policies.policy().withHoldOn(holdOn);
            holdOn.add("noise");
            assertThat(rules.holdOn()).isEqualTo(List.of("skew"));
        }

        @Test
        void refusesAStarLevelOutsideTheLadder() {
            assertThatThrownBy(() -> Policies.policy().withMinimumStars(5))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("zero to four");
            assertThatThrownBy(() -> Policies.policy().withMinimumStars(-1))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("zero to four");
            assertThatThrownBy(() -> Policies.policy().withMinimumStars(2.5))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("whole number");
        }

        @Test
        void refusesANegativeWarningCount() {
            assertThatThrownBy(() -> Policies.policy().withMaximumWarnings(-1))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("cannot be negative");
        }

        @Test
        void allowsALabThatDeliversEverything() {
            assertThat(Policies.policy().withMinimumStars(0).minimumStars()).isEqualTo(0);
        }
    }

    @Nested
    class PolicyFor {
        @Test
        void takesTheStarLevelOffTheProfile() {
            assertThat(Policies.policyFor(REGISTRY.get("quadrangles")).minimumStars())
                    .isEqualTo(4);
            assertThat(Policies.policyFor(REGISTRY.get("microfilm")).minimumStars())
                    .isEqualTo(2);
        }

        @Test
        void letsTheCallerOverruleTheProfile() {
            assertThat(Policies.policyFor(REGISTRY.get("quadrangles"))
                            .withMinimumStars(2)
                            .minimumStars())
                    .isEqualTo(2);
        }

        @Test
        void fallsBackToTheHouseLevelForAProfileWithNoQualitySection() {
            assertThat(Policies.policyFor(Fields.parseProfile(Map.of("name", "bare")))
                            .minimumStars())
                    .isEqualTo(Policies.HOUSE_POLICY.minimumStars());
        }
    }

    @Nested
    class Decide {
        @Test
        void deliversWorkThatReachedTheLevel() {
            Decision out = Policies.decide(DecisionInput.graded(grading(4)), Policies.policy());
            assertThat(out.decision()).isEqualTo(Verdict.DELIVER);
            assertThat(out.folder()).isEqualTo(Folder.DELIVERED);
            assertThat(out.reason()).isEqualTo("4 of 4 stars");
        }

        @Test
        void rejectsWorkThatDidNot() {
            Decision out =
                    Policies.decide(DecisionInput.graded(grading(1, "skew", List.of(), List.of())), Policies.policy());
            assertThat(out.decision()).isEqualTo(Verdict.REJECT);
            assertThat(out.folder()).isEqualTo(Folder.REJECTED);
            assertThat(out.reason()).containsPattern("held down by skew");
        }

        @Test
        void deliversAtExactlyTheLevelAskedFor() {
            assertThat(Policies.decide(DecisionInput.graded(grading(3)), Policies.policy())
                            .decision())
                    .isEqualTo(Verdict.DELIVER);
        }

        @Test
        void holdsWorkThatWasNeverGraded() {
            Decision out = Policies.decide(DecisionInput.ungraded(), Policies.policy());
            assertThat(out.decision()).isEqualTo(Verdict.HOLD);
            assertThat(out.reason()).isEqualTo("nothing was graded");
        }

        @Test
        void deliversUngradedWorkWhenTheLabSaysSo() {
            Decision out =
                    Policies.decide(DecisionInput.ungraded(), Policies.policy().withDeliverUngraded(true));
            assertThat(out.decision()).isEqualTo(Verdict.DELIVER);
        }

        @Test
        void holdsRatherThanRejectingWhenACheckCouldNotRun() {
            Decision out = Policies.decide(
                    DecisionInput.graded(grading(0, null, List.of("sampling"), List.of())), Policies.policy());
            assertThat(out.decision()).isEqualTo(Verdict.HOLD);
            assertThat(out.reason()).containsPattern("sampling could not be measured");
        }

        @Test
        void holdsWhenTheDetectorsDisagreedWhateverTheLevel() {
            Decision out = Policies.decide(
                    DecisionInput.graded(grading(4, null, List.of(), List.of(finding("skewAgreement", true, 1)))),
                    Policies.policy());
            assertThat(out.decision()).isEqualTo(Verdict.HOLD);
            assertThat(out.reason()).containsPattern("somebody should look at this");
        }

        @Test
        void doesNotHoldOnACheckThatCouldNotRun() {
            Decision out = Policies.decide(
                    DecisionInput.graded(grading(4, null, List.of(), List.of(finding("skewAgreement", false, 0)))),
                    Policies.policy());
            assertThat(out.decision()).isEqualTo(Verdict.DELIVER);
        }

        @Test
        void holdsWorkWithMoreWarningsThanTheLabAllows() {
            Decision out = Policies.decide(
                    DecisionInput.graded(grading(4)).withWarnings(List.of("a", "b", "c", "d")),
                    Policies.policy().withMaximumWarnings(3));
            assertThat(out.decision()).isEqualTo(Verdict.HOLD);
            assertThat(out.reason()).containsPattern("4 warnings");
        }

        @Test
        void deliversWorkAtExactlyTheWarningLimit() {
            Decision out = Policies.decide(
                    DecisionInput.graded(grading(4)).withWarnings(List.of("a", "b", "c")),
                    Policies.policy().withMaximumWarnings(3));
            assertThat(out.decision()).isEqualTo(Verdict.DELIVER);
        }

        @Test
        void holdsRatherThanReplacingADeliveryThatIsAlreadyThere() {
            Decision out = Policies.decide(DecisionInput.graded(grading()).withAlreadyThere(true), Policies.policy());
            assertThat(out.decision()).isEqualTo(Verdict.HOLD);
            assertThat(out.reason()).containsPattern("already been delivered");
        }

        @Test
        void replacesOneWhenTheLabSaidItMay() {
            Decision out = Policies.decide(
                    DecisionInput.graded(grading()).withAlreadyThere(true),
                    Policies.policy().withOverwrite(true));
            assertThat(out.decision()).isEqualTo(Verdict.DELIVER);
        }

        @Test
        void rejectsBeforeItCountsWarningsBecauseBadWorkIsBadEitherWay() {
            Decision out = Policies.decide(
                    DecisionInput.graded(grading(1, "noise", List.of(), List.of()))
                            .withWarnings(List.of("a", "b", "c", "d", "e")),
                    Policies.policy());
            assertThat(out.decision()).isEqualTo(Verdict.REJECT);
        }

        @Test
        void onlyEverGivesBackADecisionTheCallerKnows() {
            for (DecisionInput input : List.of(
                    DecisionInput.graded(grading(4)), DecisionInput.graded(grading(0)), DecisionInput.ungraded())) {
                assertThat(Policies.DECISIONS)
                        .contains(Policies.decide(input, Policies.policy()).decision());
            }
        }

        @Test
        void worksWithNoPolicyPassedAtAll() {
            assertThat(Policies.decide(DecisionInput.graded(grading(4))).decision())
                    .isEqualTo(Verdict.DELIVER);
        }
    }

    @Nested
    class KeepGoing {
        @Test
        void carriesOnPastAFailureByDefault() {
            assertThat(Policies.keepGoing(Policies.policy(), 3)).isTrue();
        }

        @Test
        void stopsWhenTheLabAskedItTo() {
            assertThat(Policies.keepGoing(Policies.policy().withStopOnFailure(true), 1))
                    .isFalse();
            assertThat(Policies.keepGoing(Policies.policy().withStopOnFailure(true), 0))
                    .isTrue();
        }
    }

    @Nested
    class DescribePolicy {
        @Test
        void saysWhatTheLabDoesInWords() {
            String said = String.join("\n", Policies.describePolicy(Policies.policy()));
            assertThat(said).containsPattern("delivers at 3 of 4 stars");
            assertThat(said).containsPattern("holds ungraded work");
            assertThat(said).containsPattern("carries on past a failure");
        }

        @Test
        void saysTheOtherThingWhenTheRulesAreTheOtherWay() {
            String said = String.join(
                    "\n",
                    Policies.describePolicy(Policies.policy()
                            .withDeliverUngraded(true)
                            .withOverwrite(true)
                            .withStopOnFailure(true)
                            .withHoldOn(List.of())));
            assertThat(said).containsPattern("delivers ungraded work");
            assertThat(said).containsPattern("replaces a delivery");
            assertThat(said).containsPattern("stops the batch");
            assertThat(said).doesNotContainPattern("holds on ");
        }
    }
}
