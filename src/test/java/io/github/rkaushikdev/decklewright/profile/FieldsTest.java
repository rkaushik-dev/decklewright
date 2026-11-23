package io.github.rkaushikdev.decklewright.profile;

import static io.github.rkaushikdev.decklewright.profile.Documents.bag;
import static io.github.rkaushikdev.decklewright.profile.Documents.list;
import static io.github.rkaushikdev.decklewright.profile.Fields.ANCHORS;
import static io.github.rkaushikdev.decklewright.profile.Fields.OUTPUT_FORMATS;
import static io.github.rkaushikdev.decklewright.profile.Fields.PROFILE_FIELDS;
import static io.github.rkaushikdev.decklewright.profile.Fields.parseProfile;
import static io.github.rkaushikdev.decklewright.profile.Fields.settingAt;
import static io.github.rkaushikdev.decklewright.profile.Fields.settingPaths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.ProfileError;
import io.github.rkaushikdev.decklewright.geom.RoundPolicy;
import io.github.rkaushikdev.decklewright.measure.Length;
import io.github.rkaushikdev.decklewright.raster.Layout;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FieldsTest {

    @Nested
    class ParseProfile {
        @Test
        void readsABareProfileAndFillsEverythingElseIn() {
            Profile profile = parseProfile(bag("name", "atlas"));
            assertThat(profile.name()).isEqualTo("atlas");
            assertThat(profile.title()).isEqualTo("untitled collection");
            assertThat(profile.version()).isEqualTo(1);
            assertThat(profile.crop().anchor()).isEqualTo(Anchor.SHEET);
            assertThat(profile.crop().minimumPpi()).isEqualTo(300);
            assertThat(profile.capture().sigma()).isEqualTo(6);
            assertThat(profile.qc().minimumStars()).isEqualTo(3);
            assertThat(profile.derivatives()).isEqualTo(List.of());
            assertThat(profile.naming().pattern()).isEqualTo("{accession}_{derivative}");
        }

        @Test
        void demandsAName() {
            assertThatThrownBy(() -> parseProfile(bag()))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("name: is required");
        }

        @Test
        void readsTheCropSettingsAQuadrangleCollectionUses() {
            Profile profile = parseProfile(bag(
                    "name",
                    "quads",
                    "crop",
                    bag(
                            "anchor",
                            "neatline",
                            "bleed",
                            bag("bottom", "6mm"),
                            "safeArea",
                            "2mm",
                            "minimumPpi",
                            400.0,
                            "snapTo",
                            8.0,
                            "rounding",
                            "inward")));
            assertThat(profile.crop().anchor()).isEqualTo(Anchor.NEATLINE);
            assertThat(profile.crop().bleed().bottom()).isInstanceOf(Length.class);
            assertThat(profile.crop().bleed().bottom().value()).isEqualTo(6);
            assertThat(profile.crop().bleed().top().isZero()).isTrue();
            assertThat(profile.crop().safeArea().left().value()).isEqualTo(2);
            assertThat(profile.crop().snapTo()).isEqualTo(8);
            assertThat(profile.crop().rounding()).isEqualTo(RoundPolicy.INWARD);
        }

        @Test
        void readsAListOfDerivatives() {
            Profile profile = parseProfile(bag(
                    "name",
                    "atlas",
                    "derivatives",
                    list(
                            bag("name", "master", "depth", "16", "kernel", "lanczos3", "keepMetadata", true),
                            bag("name", "service", "longestSide", 3000.0),
                            bag("name", "thumb", "longestSide", 400.0, "format", "pnm", "layout", "gray"))));
            assertThat(profile.derivatives()).hasSize(3);
            assertThat(profile.derivatives().get(0).depth()).isEqualTo(Depth.SIXTEEN);
            assertThat(profile.derivatives().get(0).keepMetadata()).isTrue();
            assertThat(profile.derivatives().get(1).format()).isEqualTo(OutputFormat.PNG);
            assertThat(profile.derivatives().get(2).layout()).isEqualTo(Layout.GRAY);
        }

        @Test
        void namesThePathOfABadSettingInsideADerivative() {
            assertThatThrownBy(() -> parseProfile(bag(
                            "name", "a", "derivatives", list(bag("name", "m"), bag("name", "x", "format", "jpeg")))))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("derivatives\\[1\\].format: expected one of png, pnm");
        }

        @Test
        void refusesADerivativeWithNoName() {
            assertThatThrownBy(() -> parseProfile(bag("name", "a", "derivatives", list(bag()))))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("derivatives\\[0\\].name: is required");
        }

        @Test
        void refusesASettingItDoesNotKnow() {
            assertThatThrownBy(() -> parseProfile(bag("name", "a", "cropp", bag())))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("cropp: is not a setting");
            assertThatThrownBy(() -> parseProfile(bag("name", "a", "crop", bag("bleeding", "1mm"))))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("crop.bleeding: is not a setting");
        }

        @Test
        void enforcesTheBoundsOnTheCaptureSettings() {
            assertThatThrownBy(() -> parseProfile(bag("name", "a", "capture", bag("sigma", 0.0))))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("capture.sigma: must be at least 1");
            assertThatThrownBy(() -> parseProfile(bag("name", "a", "capture", bag("speckle", 1.5))))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("whole number");
        }

        @Test
        void readsAToleranceInTheQualitySettings() {
            Profile profile = parseProfile(bag("name", "a", "qc", bag("samplingTolerance", "+2/-0")));
            assertThat(profile.qc().samplingTolerance().plus()).isEqualTo(2);
            assertThat(profile.qc().samplingTolerance().minus()).isEqualTo(0);
        }

        @Test
        void leavesAnOptionalSettingOutWhenItWasNotWritten() {
            Profile profile = parseProfile(bag("name", "a"));
            assertThat(profile.crop().aspect()).isEmpty();
            assertThat(profile.parent()).isEmpty();
        }
    }

    @Nested
    class TheTables {
        @Test
        void listsTheAnchorsAndFormats() {
            assertThat(ANCHORS).containsExactly("sheet", "neatline");
            assertThat(OUTPUT_FORMATS).containsExactly("png", "pnm");
            assertThatThrownBy(() -> PROFILE_FIELDS.put("colour", Schema.text()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class ToneAims {
        @Test
        void areLeftEmptyAndOutOfTheJsonWhenNotWritten() {
            Profile profile = parseProfile(bag("name", "atlas"));
            assertThat(profile.qc().toneAims()).isEmpty();
            assertThat(profile.qc().toneTolerance()).isEqualTo(6);
            assertThat(profile.qc().toJson()).doesNotContainKey("toneAims").containsEntry("toneTolerance", 6.0);
        }

        @Test
        void readAsNumbersAndWriteBackInOrder() {
            Profile profile = parseProfile(
                    bag("name", "atlas", "qc", bag("toneAims", List.of(242, 200, 120, 40), "toneTolerance", 4)));
            assertThat(profile.qc().toneAims()).containsExactly(242.0, 200.0, 120.0, 40.0);
            assertThat(profile.qc().toJson())
                    .containsEntry("toneAims", List.of(242.0, 200.0, 120.0, 40.0))
                    .containsEntry("toneTolerance", 4.0);
            assertThat(settingAt(profile, "qc.toneAims.1")).contains(200.0);
        }

        @Test
        void refuseAnAimThatIsNotASampleValue() {
            assertThatThrownBy(() -> parseProfile(bag("name", "atlas", "qc", bag("toneAims", List.of(40, -1)))))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageStartingWith("qc.toneAims[1]: ");
        }

        @Test
        void carryOnThroughAnotherGrade() {
            Profile.Qc qc = parseProfile(bag("name", "atlas", "qc", bag("toneAims", List.of(40, 240))))
                    .qc();
            Profile.Qc four = qc.withMinimumStars(4);
            assertThat(four.minimumStars()).isEqualTo(4);
            assertThat(four.toneAims()).isEqualTo(qc.toneAims());
            assertThat(four.withMinimumStars(qc.minimumStars())).isEqualTo(qc);
        }
    }

    @Nested
    class SettingPaths {
        @Test
        void walksTheWholeTree() {
            List<String> paths = settingPaths();
            assertThat(paths).contains("name");
            assertThat(paths).contains("crop");
            assertThat(paths).contains("crop.minimumPpi");
            assertThat(paths).contains("capture.expectNeatline");
            assertThat(paths).contains("qc.minimumStars");
            assertThat(paths).contains("naming.pattern");
        }

        @Test
        void hasNoDuplicates() {
            List<String> paths = settingPaths();
            assertThat(new HashSet<>(paths)).hasSize(paths.size());
        }
    }

    @Nested
    class SettingAt {
        private final Profile profile = parseProfile(bag("name", "atlas", "crop", bag("minimumPpi", 500.0)));

        @Test
        void readsADottedPath() {
            assertThat(settingAt(profile, "name")).contains("atlas");
            assertThat(settingAt(profile, "crop.minimumPpi")).contains(500.0);
        }

        @Test
        void givesNothingBackForAPathThatIsNotThere() {
            assertThat(settingAt(profile, "crop.nothing")).isEmpty();
            assertThat(settingAt(profile, "nothing.at.all")).isEmpty();
        }

        @Test
        void reachesEveryPathTheTreeLists() {
            for (String path : settingPaths()) {
                assertThatCode(() -> settingAt(profile, path)).doesNotThrowAnyException();
            }
        }
    }
}
