package io.github.rkaushikdev.decklewright.profile;

import static io.github.rkaushikdev.decklewright.profile.Documents.bag;
import static io.github.rkaushikdev.decklewright.profile.Documents.group;
import static io.github.rkaushikdev.decklewright.profile.Documents.item;
import static io.github.rkaushikdev.decklewright.profile.Documents.list;
import static io.github.rkaushikdev.decklewright.profile.Migrations.CURRENT_VERSION;
import static io.github.rkaushikdev.decklewright.profile.Migrations.MIGRATIONS;
import static io.github.rkaushikdev.decklewright.profile.Migrations.migrate;
import static io.github.rkaushikdev.decklewright.profile.Migrations.migrateAll;
import static io.github.rkaushikdev.decklewright.profile.Migrations.migrationNotes;
import static io.github.rkaushikdev.decklewright.profile.Migrations.needsMigration;
import static io.github.rkaushikdev.decklewright.profile.Migrations.versionOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.ProfileError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MigrateTest {

    /** A profile as it was written before the renames. */
    private static Map<String, Object> oldProfile() {
        return bag(
                "name", "plat-books",
                "crop", bag("margin", "3mm", "roundTo", 8.0, "straighten", true, "anchor", "sheet"),
                "qc", bag("dpi", 600.0, "dpiTolerance", "1%"),
                "derivatives",
                        list(
                                bag("name", "master", "depth", 16.0),
                                bag("name", "service", "maxSide", 3000.0, "depth", 8.0)));
    }

    @Nested
    class VersionOf {
        @Test
        void readsAWrittenVersion() {
            assertThat(versionOf(bag("version", 2.0))).isEqualTo(2);
        }

        @Test
        void treatsADocumentWithNoVersionAsVersionOne() {
            assertThat(versionOf(bag("name", "a"))).isEqualTo(1);
        }

        @Test
        void refusesAVersionThatIsNotAWholeNumberFromOne() {
            assertThatThrownBy(() -> versionOf(bag("version", 0.0)))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("whole number from one");
            assertThatThrownBy(() -> versionOf(bag("version", 1.5)))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("whole number from one");
            assertThatThrownBy(() -> versionOf(bag("version", "two")))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("whole number from one");
        }
    }

    @Nested
    class NeedsMigration {
        @Test
        void isTrueForAnOldDocumentAndFalseForACurrentOne() {
            assertThat(needsMigration(oldProfile())).isTrue();
            assertThat(needsMigration(bag("name", "a", "version", (double) CURRENT_VERSION)))
                    .isFalse();
        }
    }

    @Nested
    class Migrate {
        @Test
        void renamesTheCropSettings() {
            Map<String, ?> document = migrate(oldProfile()).document();
            Map<String, Object> crop = group(document, "crop");
            assertThat(crop.get("bleed")).isEqualTo("3mm");
            assertThat(crop.get("snapTo")).isEqualTo(8.0);
            assertThat(crop.get("deskew")).isEqualTo(true);
            assertThat(crop).doesNotContainKey("margin");
            assertThat(crop).doesNotContainKey("roundTo");
            assertThat(crop).doesNotContainKey("straighten");
        }

        @Test
        void renamesTheQualitySettings() {
            Map<String, Object> qc = group(migrate(oldProfile()).document(), "qc");
            assertThat(qc.get("samplingPpi")).isEqualTo(600.0);
            assertThat(qc.get("samplingTolerance")).isEqualTo("1%");
            assertThat(qc).doesNotContainKey("dpi");
        }

        @Test
        void renamesAndRetypesTheDerivativeSettings() {
            Map<String, ?> document = migrate(oldProfile()).document();
            assertThat(item(document, "derivatives", 0).get("depth")).isEqualTo("16");
            assertThat(item(document, "derivatives", 1).get("longestSide")).isEqualTo(3000.0);
            assertThat(item(document, "derivatives", 1)).doesNotContainKey("maxSide");
        }

        @Test
        void stampsTheNewVersionAndSaysWhatItApplied() {
            Migrations.Migrated migrated = migrate(oldProfile());
            assertThat(migrated.document().get("version")).isEqualTo((double) CURRENT_VERSION);
            assertThat(migrated.applied()).isEqualTo(List.of(1));
        }

        @Test
        void leavesACurrentDocumentAlone() {
            Map<String, Object> current =
                    bag("name", "a", "version", (double) CURRENT_VERSION, "crop", bag("bleed", "1mm"));
            Migrations.Migrated migrated = migrate(current);
            assertThat(migrated.applied()).isEqualTo(List.of());
            assertThat(migrated.document()).isSameAs(current);
        }

        @Test
        void doesNotTouchTheDocumentItWasGiven() {
            Map<String, Object> before = oldProfile();
            migrate(before);
            assertThat(group(before, "crop").get("margin")).isEqualTo("3mm");
            assertThat(before).doesNotContainKey("version");
        }

        @Test
        void producesSomethingTheSchemaWillRead() {
            Profile profile = Fields.parseProfile(migrate(oldProfile()).document());
            assertThat(profile.crop().bleed().top().value()).isEqualTo(3);
            assertThat(profile.derivatives().get(1).longestSide()).hasValue(3000);
            assertThat(profile.qc().samplingPpi()).isEqualTo(600);
        }

        @Test
        void refusesADocumentFromTheFuture() {
            assertThatThrownBy(() -> migrate(bag("name", "a", "version", 99.0)))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("written for version 99 and this build understands 2");
        }

        @Test
        void leavesADocumentThatOnlyHasSomeOfTheOldNamesAloneOtherwise() {
            Map<String, ?> document =
                    migrate(bag("name", "a", "crop", bag("anchor", "sheet"))).document();
            assertThat(document.get("crop")).isEqualTo(bag("anchor", "sheet"));
        }

        @Test
        void copesWithADocumentThatHasNoGroupsAtAll() {
            assertThat(migrate(bag("name", "a")).document().get("version")).isEqualTo((double) CURRENT_VERSION);
        }
    }

    @Nested
    class MigrateAll {
        @Test
        void bringsAWholeFolderForward() {
            List<Map<String, ?>> out =
                    migrateAll(List.of(oldProfile(), bag("name", "b", "version", (double) CURRENT_VERSION)));
            assertThat(out).hasSize(2);
            assertThat(out.get(0).get("version")).isEqualTo((double) CURRENT_VERSION);
            assertThat(out.get(1).get("name")).isEqualTo("b");
        }
    }

    @Nested
    class MigrationNotes {
        @Test
        void saysNothingAboutACurrentDocument() {
            assertThat(migrationNotes(bag("name", "a", "version", (double) CURRENT_VERSION)))
                    .isEqualTo(List.of());
        }

        @Test
        void namesEachRenameItHadToMake() {
            List<String> notes = migrationNotes(oldProfile());
            assertThat(notes.get(0)).containsPattern("brought forward from version 1 through 1");
            assertThat(notes).contains("crop.margin is now crop.bleed");
            assertThat(notes).contains("qc.dpi is now qc.samplingPpi");
            assertThat(notes).contains("derivatives[].maxSide is now longestSide");
            assertThat(notes).contains("derivatives[].depth is written as text now");
        }

        @Test
        void mentionsOnlyWhatTheDocumentActuallyUsed() {
            List<String> notes = migrationNotes(bag("name", "a", "qc", bag("dpi", 400.0)));
            assertThat(notes).contains("qc.dpi is now qc.samplingPpi");
            assertThat(String.join(" ", notes)).doesNotContainPattern("crop.margin");
        }
    }

    @Nested
    class TheMigrationTable {
        @Test
        void hasAStepOffEveryVersionBelowTheCurrentOne() {
            for (int version = 1; version < CURRENT_VERSION; version += 1) {
                assertThat(MIGRATIONS.get(version)).isInstanceOf(Migrations.Migration.class);
            }
        }

        @Test
        void isFrozen() {
            assertThatThrownBy(() -> MIGRATIONS.put(2, document -> Map.of()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
