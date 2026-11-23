package io.github.rkaushikdev.decklewright.profile;

import static io.github.rkaushikdev.decklewright.profile.Diagnostics.describeDerivative;
import static io.github.rkaushikdev.decklewright.profile.Diagnostics.describeInPixels;
import static io.github.rkaushikdev.decklewright.profile.Diagnostics.describeProfile;
import static io.github.rkaushikdev.decklewright.profile.Diagnostics.report;
import static io.github.rkaushikdev.decklewright.profile.Documents.bag;
import static io.github.rkaushikdev.decklewright.profile.Documents.list;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DiagnosticsTest {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();
    private static final Profile HOUSE = REGISTRY.get("house");
    private static final Profile QUADS = REGISTRY.get("quadrangles");

    private static Optional<String> firstStartingWith(List<String> lines, String prefix) {
        return lines.stream().filter(line -> line.startsWith(prefix)).findFirst();
    }

    private static String last(List<String> lines) {
        return lines.get(lines.size() - 1);
    }

    @Nested
    class DescribeProfile {
        @Test
        void opensWithTheNameAndTheTitle() {
            assertThat(describeProfile(HOUSE).get(0)).isEqualTo("house: house style (version 2)");
        }

        @Test
        void saysWhatTheCropIsAnchoredTo() {
            assertThat(String.join("\n", describeProfile(HOUSE)))
                    .containsPattern("crops to the sheet, straightened first");
            assertThat(String.join("\n", describeProfile(QUADS))).containsPattern("crops to the neatline");
        }

        @Test
        void saysNothingAboutABleedAProfileDoesNotHave() {
            assertThat(String.join("\n", describeProfile(HOUSE))).doesNotContainPattern("keeps");
        }

        @Test
        void spellsOutAnUnevenBleedSideBySide() {
            Optional<String> line = firstStartingWith(describeProfile(QUADS), "keeps");
            assertThat(line)
                    .hasValueSatisfying(text ->
                            assertThat(text).containsPattern("top 3.00 mm").containsPattern("bottom 38.00 mm"));
        }

        @Test
        void shortensAnEvenBleedToOneFigure() {
            Profile plates = REGISTRY.get("atlas-plates");
            assertThat(firstStartingWith(describeProfile(plates), "keeps")).contains("keeps 2.00 mm beyond it");
        }

        @Test
        void listsEveryDerivative() {
            List<String> lines = describeProfile(HOUSE);
            assertThat(lines.stream().anyMatch(line -> line.startsWith("master:")))
                    .isTrue();
            assertThat(lines.stream().anyMatch(line -> line.startsWith("service:")))
                    .isTrue();
            assertThat(lines.stream().anyMatch(line -> line.startsWith("thumb:")))
                    .isTrue();
        }

        @Test
        void saysSoWhenAProfileWritesNothing() {
            ProfileRegistry quiet =
                    new ProfileRegistry().add(bag("name", "quiet", "extends", "house", "derivatives", list()));
            assertThat(describeProfile(quiet.get("quiet"))).contains("writes nothing");
        }

        @Test
        void mentionsASafeAreaAndAnAspectWhenTheyAreSet() {
            ProfileRegistry site = new ProfileRegistry()
                    .add(bag(
                            "name",
                            "framed",
                            "extends",
                            "house",
                            "crop",
                            bag("safeArea", "4mm", "aspect", 1.5, "aspectDirection", "shrink")));
            String lines = String.join("\n", describeProfile(site.get("framed")));
            assertThat(lines).containsPattern("holds a safe area of 4.00 mm");
            assertThat(lines).containsPattern("forces an aspect of 1.5, shrinking");
        }

        @Test
        void mentionsANeatlineHuntOnlyWhenTheCaptureDoesOne() {
            assertThat(describeProfile(QUADS)).contains("looks for a neatline");
            assertThat(describeProfile(HOUSE)).doesNotContain("looks for a neatline");
        }

        @Test
        void endsWithTheQualityBar() {
            assertThat(last(describeProfile(QUADS))).matches("quality: 600 ppi \\+/-1%, skew under 0.15 deg, 4 stars");
        }
    }

    @Nested
    class DescribeDerivative {
        @Test
        void saysFullSizeWhenNeitherASideNorAScaleIsSet() {
            assertThat(describeDerivative(HOUSE.derivatives().get(0)))
                    .isEqualTo("master: full size, rgb at 16 bit, png by lanczos3, keeping metadata");
        }

        @Test
        void saysTheLongestSideWhenOneIsSet() {
            assertThat(describeDerivative(HOUSE.derivatives().get(1))).containsPattern("longest side 3000 px");
        }

        @Test
        void saysHowFilesAreNamedAndWhatComesOffACallNumber() {
            assertThat(Diagnostics.describeProfile(HOUSE))
                    .contains("names files {accession}_{derivative}, lower cased");
            Profile shelved = Fields.parseProfile(
                    bag("name", "a", "naming", bag("pattern", "{year}-{class}_{derivative}", "lowercase", false)));
            assertThat(Diagnostics.describeProfile(shelved))
                    .contains("names files {year}-{class}_{derivative}, year and class read off a map call number");
        }

        @Test
        void countsTheToneAimsInTheQualityLine() {
            Profile aimed = Fields.parseProfile(bag("name", "a", "qc", bag("toneAims", List.of(20, 120, 240))));
            assertThat(Diagnostics.describeProfile(aimed)
                            .get(Diagnostics.describeProfile(aimed).size() - 1))
                    .endsWith(", 3 stars, 3 tone aims");
            assertThat(Diagnostics.report(aimed, 600)).containsAll(Diagnostics.describeProfile(aimed));
        }

        @Test
        void saysThePpiWhenOneIsSet() {
            Profile.Derivative service = Fields.parseProfile(
                            bag("name", "a", "derivatives", List.of(bag("name", "service", "ppi", 150.0))))
                    .derivatives()
                    .get(0);
            assertThat(describeDerivative(service)).isEqualTo("service: at 150 ppi, rgb at 8 bit, png by catmullRom");
            assertThat(service.ppi()).hasValue(150);
            assertThat(service.toJson()).containsEntry("ppi", 150.0);
        }

        @Test
        void saysTheScaleWhenOneIsSet() {
            ProfileRegistry site = new ProfileRegistry()
                    .add(bag(
                            "name",
                            "half",
                            "extends",
                            "house",
                            "derivatives",
                            list(bag("name", "half", "scale", 0.5))));
            assertThat(describeDerivative(site.get("half").derivatives().get(0)))
                    .containsPattern("scaled to 0.5");
        }
    }

    @Nested
    class DescribeInPixels {
        @Test
        void worksTheBleedOutAtTheClaimedFrequency() {
            List<String> lines = describeInPixels(QUADS);
            assertThat(lines.get(0)).isEqualTo("at 600 ppi");
            assertThat(lines.get(1)).isEqualTo("bleed 71/71/898/71 px");
        }

        @Test
        void worksItOutAtAnotherFrequencyWhenGivenOne() {
            List<String> lines = describeInPixels(QUADS, 300);
            assertThat(lines.get(0)).isEqualTo("at 300 ppi");
            assertThat(lines.get(1)).isEqualTo("bleed 35/35/449/35 px");
        }

        @Test
        void reportsZeroForAProfileWithNoBleed() {
            assertThat(describeInPixels(HOUSE).get(1)).isEqualTo("bleed 0/0/0/0 px");
        }

        @Test
        void repeatsTheSnapSettingsWhichIsWhatAHairlineProblemUsuallyIs() {
            assertThat(last(describeInPixels(QUADS))).isEqualTo("snap to 8 px up");
        }
    }

    @Nested
    class Report {
        @Test
        void putsEverythingTogether() {
            List<String> lines = report(QUADS);
            assertThat(lines.get(0)).containsPattern("^quadrangles:");
            assertThat(String.join("\n", lines)).containsPattern("bleed 71");
        }

        @Test
        void addsTheProblemsAtTheEndWhenThereAreAny() {
            ProfileRegistry noisy = new ProfileRegistry()
                    .add(bag("name", "noisy", "extends", "house", "capture", bag("expectNeatline", true)));
            assertThat(last(report(noisy.get("noisy")))).containsPattern("^note: capture.expectNeatline:");
        }

        @Test
        void saysNothingExtraAboutACleanProfile() {
            assertThat(report(HOUSE).stream().anyMatch(line -> line.startsWith("fault:")))
                    .isFalse();
        }
    }
}
