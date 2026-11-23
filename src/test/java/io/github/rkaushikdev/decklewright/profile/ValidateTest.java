package io.github.rkaushikdev.decklewright.profile;

import static io.github.rkaushikdev.decklewright.profile.Documents.bag;
import static io.github.rkaushikdev.decklewright.profile.Documents.list;
import static io.github.rkaushikdev.decklewright.profile.Fields.parseProfile;
import static io.github.rkaushikdev.decklewright.profile.Validation.describeProblems;
import static io.github.rkaushikdev.decklewright.profile.Validation.fault;
import static io.github.rkaushikdev.decklewright.profile.Validation.faultsOnly;
import static io.github.rkaushikdev.decklewright.profile.Validation.inspectDerivatives;
import static io.github.rkaushikdev.decklewright.profile.Validation.inspectProfile;
import static io.github.rkaushikdev.decklewright.profile.Validation.note;
import static io.github.rkaushikdev.decklewright.profile.Validation.requireUsable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import io.github.rkaushikdev.decklewright.ProfileError;
import io.github.rkaushikdev.decklewright.qc.Ladders;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValidateTest {

    /** A sensible profile with the written settings on top, replacing its top level keys. */
    private static Profile profile(Map<String, Object> written) {
        Map<String, Object> document = bag(
                "name",
                "atlas",
                "derivatives",
                list(bag("name", "master"), bag("name", "service", "longestSide", 3000.0)));
        document.putAll(written);
        return parseProfile(document);
    }

    private static List<String> paths(List<Problem> problems) {
        return problems.stream().map(Problem::path).toList();
    }

    @Nested
    class InspectProfile {
        @Test
        void hasNothingToSayAboutASensibleProfile() {
            assertThat(inspectProfile(profile(bag()))).isEqualTo(List.of());
        }

        @Test
        void catchesANeatlineCropTheCaptureNeverLooksFor() {
            List<Problem> problems = inspectProfile(profile(bag("crop", bag("anchor", "neatline"))));
            assertThat(faultsOnly(problems)).hasSize(1);
            assertThat(paths(problems)).contains("crop.anchor");
        }

        @Test
        void refusesToneAimsThatDoNotStepOneWay() {
            List<Problem> problems =
                    inspectProfile(profile(bag("qc", bag("toneAims", list(240.0, 180.0, 200.0, 40.0)))));
            assertThat(faultsOnly(problems)).hasSize(1);
            assertThat(problems.get(0).path()).isEqualTo("qc.toneAims");
            assertThat(problems.get(0).message()).isEqualTo("does not step one way, so no wedge can read it");
        }

        @Test
        void acceptsToneAimsEitherWayRoundAndLevelSteps() {
            assertThat(inspectProfile(profile(bag("qc", bag("toneAims", list(40.0, 40.0, 120.0, 240.0))))))
                    .isEqualTo(List.of());
            assertThat(inspectProfile(profile(bag("qc", bag("toneAims", list(240.0, 120.0, 40.0))))))
                    .isEqualTo(List.of());
        }

        @Test
        void notesAStraighteningThresholdOnACropThatNeverStraightens() {
            List<Problem> problems = inspectProfile(profile(bag("crop", bag("deskew", false, "deskewAbove", 0.1))));
            assertThat(problems)
                    .containsExactly(note("crop.deskewAbove", "has no effect, the crop never straightens a sheet"));
        }

        @Test
        void notesAThresholdThatLeavesSheetsTheSkewCheckWillMarkDown() {
            List<Problem> problems = inspectProfile(profile(bag("crop", bag("deskewAbove", 0.5))));
            assertThat(problems)
                    .containsExactly(note(
                            "crop.deskewAbove",
                            "leaves sheets up to 0.5 deg off square, past the 0.3 the skew check allows"));
            assertThat(inspectProfile(profile(bag("crop", bag("deskewAbove", 0.3)))))
                    .isEmpty();
        }

        @Test
        void acceptsThePairWhenBothAreSet() {
            List<Problem> problems = inspectProfile(
                    profile(bag("crop", bag("anchor", "neatline"), "capture", bag("expectNeatline", true))));
            assertThat(problems).isEqualTo(List.of());
        }

        @Test
        void notesANeatlineTheCropThenIgnores() {
            List<Problem> problems = inspectProfile(profile(bag("capture", bag("expectNeatline", true))));
            assertThat(faultsOnly(problems)).hasSize(0);
            assertThat(paths(problems)).contains("capture.expectNeatline");
        }

        @Test
        void catchesACropThatDemandsMoreThanTheCaptureHas() {
            List<Problem> problems =
                    inspectProfile(profile(bag("crop", bag("minimumPpi", 800.0), "qc", bag("samplingPpi", 400.0))));
            assertThat(faultsOnly(problems)).hasSize(1);
            assertThat(problems.get(0).message()).containsPattern("demands 800 ppi from a capture taken at 400");
        }

        @Test
        void catchesANegativeBleedOrSafeArea() {
            List<Problem> problems = inspectProfile(
                    profile(bag("crop", bag("bleed", bag("top", "-3mm"), "safeArea", bag("left", "-1mm")))));
            assertThat(paths(faultsOnly(problems))).isEqualTo(List.of("crop.bleed.top", "crop.safeArea.left"));
        }

        @Test
        void notesABleedAndASafeAreaSetOnTheSameEdge() {
            List<Problem> problems = inspectProfile(
                    profile(bag("crop", bag("bleed", bag("top", "3mm"), "safeArea", bag("top", "1mm")))));
            assertThat(faultsOnly(problems)).hasSize(0);
            assertThat(paths(problems)).contains("crop.safeArea.top");
        }

        @Test
        void notesAProfileThatWritesNothing() {
            List<Problem> problems = inspectProfile(parseProfile(bag("name", "atlas")));
            assertThat(paths(problems)).contains("derivatives");
            assertThat(faultsOnly(problems)).hasSize(0);
        }

        @Test
        void notesAStarLevelTheClippingLimitCannotReach() {
            // the ladders live with the checks, so the cross check is made there and not here
            Profile written = profile(bag("qc", bag("minimumStars", 4.0, "maximumClipping", 0.01)));
            assertThat(paths(inspectProfile(written))).doesNotContain("qc.maximumClipping");
            List<Problem> problems = Ladders.unreachableLevels(written);
            assertThat(paths(problems)).contains("qc.maximumClipping");
            assertThat(problems)
                    .contains(
                            note(
                                    "qc.maximumClipping",
                                    "is looser than four stars allows, which is 0.001, so a capture can pass it and miss the level"));
        }
    }

    @Nested
    class InspectDerivatives {
        @Test
        void catchesARepeatedName() {
            List<Problem> problems = inspectDerivatives(parseProfile(bag(
                            "name",
                            "a",
                            "derivatives",
                            list(bag("name", "service", "longestSide", 100.0), bag("name", "service"))))
                    .derivatives());
            assertThat(faultsOnly(problems)).hasSize(1);
            assertThat(problems.get(0).message()).containsPattern("repeats the name service");
        }

        @Test
        void catchesADerivativeThatSetsBothASideAndAScale() {
            List<Problem> problems = inspectDerivatives(parseProfile(bag(
                            "name",
                            "a",
                            "derivatives",
                            list(bag("name", "service", "longestSide", 100.0, "scale", 0.5))))
                    .derivatives());
            assertThat(faultsOnly(problems)).hasSize(1);
            assertThat(problems.get(0).path()).isEqualTo("derivatives[0]");
        }

        @Test
        void namesEverySizeADerivativeSetsAtOnce() {
            List<Problem> problems = inspectDerivatives(parseProfile(bag(
                            "name",
                            "a",
                            "derivatives",
                            list(
                                    bag("name", "service", "longestSide", 100.0, "scale", 0.5),
                                    bag("name", "thumb", "longestSide", 100.0, "scale", 0.5, "ppi", 50.0))))
                    .derivatives());
            assertThat(problems)
                    .containsExactly(
                            fault("derivatives[0]", "sets a longest side and a scale, which cannot both hold"),
                            fault(
                                    "derivatives[1]",
                                    "sets a longest side and a scale and a ppi, which cannot all hold"));
        }

        @Test
        void countsADerivativeAtAPpiAsReduced() {
            List<Problem> problems = inspectDerivatives(parseProfile(bag(
                            "name",
                            "a",
                            "derivatives",
                            list(bag("name", "master"), bag("name", "service", "ppi", 150.0))))
                    .derivatives());
            assertThat(problems).isEmpty();
        }

        @Test
        void notesAPpiTheCaptureCannotBeReducedTo() {
            List<Problem> problems = inspectProfile(
                    profile(bag("derivatives", list(bag("name", "master"), bag("name", "service", "ppi", 400.0)))));
            assertThat(problems)
                    .containsExactly(note(
                            "derivatives[1].ppi",
                            "asks for 400 ppi from a capture taken at 400, so it is written full size"));
        }

        @Test
        void notesTwoDerivativesAtFullSize() {
            List<Problem> problems = inspectDerivatives(
                    parseProfile(bag("name", "a", "derivatives", list(bag("name", "a"), bag("name", "b"))))
                            .derivatives());
            assertThat(paths(problems)).contains("derivatives");
        }

        @Test
        void notesMetadataAskedForOnAFormatThatCannotCarryIt() {
            List<Problem> problems = inspectDerivatives(parseProfile(bag(
                            "name", "a", "derivatives", list(bag("name", "a", "format", "pnm", "keepMetadata", true))))
                    .derivatives());
            assertThat(paths(problems)).contains("derivatives[0].keepMetadata");
        }

        @Test
        void notesAVeryLargeNetpbm() {
            List<Problem> problems = inspectDerivatives(parseProfile(bag(
                            "name",
                            "a",
                            "derivatives",
                            list(bag("name", "a", "format", "pnm", "depth", "16", "layout", "rgb"))))
                    .derivatives());
            assertThat(paths(problems)).contains("derivatives[0].format");
        }

        @Test
        void hasNothingToSayAboutACleanList() {
            assertThat(inspectDerivatives(parseProfile(bag(
                                    "name",
                                    "a",
                                    "derivatives",
                                    list(bag("name", "master"), bag("name", "thumb", "longestSide", 400.0))))
                            .derivatives()))
                    .isEqualTo(List.of());
        }
    }

    @Nested
    class RequireUsable {
        @Test
        void passesASensibleProfileThrough() {
            Profile good = profile(bag());
            assertThat(requireUsable(good)).isSameAs(good);
        }

        @Test
        void throwsOnTheFirstFaultAndListsThemAll() {
            ProfileError err = catchThrowableOfType(
                    ProfileError.class,
                    () -> requireUsable(profile(bag("crop", bag("anchor", "neatline", "minimumPpi", 900.0)))));
            assertThat(err).hasMessageFindingMatch("^the atlas profile cannot be used:");
            assertThat(err.details().get("faults")).asInstanceOf(LIST).hasSize(2);
            assertThat(err.details().get("name")).isEqualTo("atlas");
        }

        @Test
        void letsAProfileWithOnlyNotesThrough() {
            Profile noted = profile(bag("capture", bag("expectNeatline", true)));
            assertThat(requireUsable(noted)).isSameAs(noted);
        }
    }

    @Nested
    class TheHelpers {
        @Test
        void buildsAndFiltersProblems() {
            List<Problem> problems = List.of(fault("a", "one"), note("b", "two"));
            assertThat(faultsOnly(problems)).hasSize(1);
            assertThat(describeProblems(problems)).isEqualTo(List.of("fault: a: one", "note: b: two"));
        }
    }
}
