package io.github.rkaushikdev.decklewright.profile;

import static io.github.rkaushikdev.decklewright.profile.Documents.bag;
import static io.github.rkaushikdev.decklewright.profile.Documents.group;
import static io.github.rkaushikdev.decklewright.profile.Documents.list;
import static io.github.rkaushikdev.decklewright.profile.Overlays.changedPaths;
import static io.github.rkaushikdev.decklewright.profile.Overlays.overlay;
import static io.github.rkaushikdev.decklewright.profile.Overlays.overlayAll;
import static io.github.rkaushikdev.decklewright.profile.Overlays.resolveExtends;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.ProfileError;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OverlayTest {

    @Nested
    class Overlay {
        @Test
        void takesThePatchOverTheBase() {
            assertThat(overlay(bag("a", 1.0, "b", 2.0), bag("b", 3.0))).isEqualTo(bag("a", 1.0, "b", 3.0));
        }

        @Test
        void mergesGroupsALevelDown() {
            Map<String, Object> out =
                    overlay(bag("crop", bag("anchor", "sheet", "ppi", 400.0)), bag("crop", bag("ppi", 600.0)));
            assertThat(out).isEqualTo(bag("crop", bag("anchor", "sheet", "ppi", 600.0)));
        }

        @Test
        void mergesAsDeepAsTheGroupsGo() {
            Map<String, Object> out =
                    overlay(bag("a", bag("b", bag("c", 1.0, "d", 2.0))), bag("a", bag("b", bag("d", 3.0))));
            assertThat(out).isEqualTo(bag("a", bag("b", bag("c", 1.0, "d", 3.0))));
        }

        @Test
        void replacesAListRatherThanJoiningIt() {
            Map<String, Object> out =
                    overlay(bag("derivatives", list(bag("name", "a"), bag("name", "b"))), bag("derivatives", list()));
            assertThat(out.get("derivatives")).isEqualTo(List.of());
        }

        @Test
        void removesASettingWrittenAsNull() {
            assertThat(overlay(bag("a", 1.0, "b", 2.0), bag("b", null))).isEqualTo(bag("a", 1.0));
        }

        @Test
        void replacesAGroupWithAPlainValueWhenThePatchSaysSo() {
            assertThat(overlay(bag("bleed", bag("top", 1.0)), bag("bleed", "3mm")))
                    .isEqualTo(bag("bleed", "3mm"));
        }

        @Test
        void leavesTheBaseUntouched() {
            Map<String, Object> base = bag("crop", bag("ppi", 400.0));
            overlay(base, bag("crop", bag("ppi", 600.0)));
            assertThat(group(base, "crop").get("ppi")).isEqualTo(400.0);
        }
    }

    @Nested
    class OverlayAll {
        @Test
        void foldsAListOldestFirst() {
            assertThat(overlayAll(List.of(bag("a", 1.0), bag("a", 2.0, "b", 1.0), bag("b", 2.0))))
                    .isEqualTo(bag("a", 2.0, "b", 2.0));
        }

        @Test
        void givesAnEmptyObjectForAnEmptyList() {
            assertThat(overlayAll(List.of())).isEqualTo(Map.of());
        }
    }

    @Nested
    class ResolveExtends {
        private final Map<String, Map<String, Object>> library = Map.of(
                "house",
                bag(
                        "name", "house",
                        "crop", bag("anchor", "sheet", "minimumPpi", 300.0),
                        "qc", bag("minimumStars", 3.0)),
                "maps",
                bag("name", "maps", "extends", "house", "crop", bag("anchor", "neatline")),
                "loops",
                bag("name", "loops", "extends", "alsoLoops"),
                "alsoLoops",
                bag("name", "alsoLoops", "extends", "loops"));
        private final Function<String, Optional<? extends Map<String, ?>>> lookup =
                name -> Optional.ofNullable(library.get(name));

        @Test
        void leavesAProfileWithNoParentAlone() {
            Overlays.Resolved resolved = resolveExtends(library.get("house"), lookup);
            assertThat(resolved.chain()).isEqualTo(List.of());
            assertThat(resolved.document()).isEqualTo(library.get("house"));
        }

        @Test
        void flattensOneLevel() {
            Overlays.Resolved resolved = resolveExtends(library.get("maps"), lookup);
            assertThat(resolved.chain()).isEqualTo(List.of("house"));
            assertThat(resolved.document().get("name")).isEqualTo("maps");
            assertThat(resolved.document().get("crop")).isEqualTo(bag("anchor", "neatline", "minimumPpi", 300.0));
            assertThat(resolved.document().get("qc")).isEqualTo(bag("minimumStars", 3.0));
            assertThat(resolved.document()).doesNotContainKey("extends");
        }

        @Test
        void flattensTwoLevelsInTheRightOrder() {
            Map<String, Object> deep = bag("name", "deep", "extends", "maps", "crop", bag("minimumPpi", 600.0));
            Overlays.Resolved resolved = resolveExtends(
                    deep, name -> Optional.ofNullable(name.equals("maps") ? library.get("maps") : library.get(name)));
            assertThat(resolved.chain()).isEqualTo(List.of("maps", "house"));
            assertThat(resolved.document().get("crop")).isEqualTo(bag("anchor", "neatline", "minimumPpi", 600.0));
        }

        @Test
        void refusesAParentThatIsNotThereAndSaysWhich() {
            Map<String, Object> orphan = bag("name", "orphan", "extends", "hoose");
            assertThatThrownBy(() -> resolveExtends(orphan, lookup))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("no profile called hoose to extend");
        }

        @Test
        void refusesAChainThatLoops() {
            assertThatThrownBy(() -> resolveExtends(library.get("loops"), lookup))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("loops back to loops");
        }

        @Test
        void refusesAChainThatIsTooDeep() {
            Function<Integer, Map<String, Object>> chainOf = n -> bag("name", "p" + n, "extends", "p" + (n - 1));
            Function<String, Optional<? extends Map<String, ?>>> lookupDeep =
                    name -> Optional.of(chainOf.apply(Integer.parseInt(name.substring(1))));
            assertThatThrownBy(() -> resolveExtends(chainOf.apply(20), lookupDeep, 3))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("too deep");
        }

        @Test
        void keepsTheChildNameRatherThanTheParentOne() {
            assertThat(resolveExtends(library.get("maps"), lookup).document().get("name"))
                    .isEqualTo("maps");
        }
    }

    @Nested
    class ChangedPaths {
        @Test
        void listsWhatTheChildActuallySet() {
            Map<String, Object> base = bag("crop", bag("anchor", "sheet", "minimumPpi", 300.0), "title", "house");
            Map<String, Object> child = bag("crop", bag("anchor", "neatline", "minimumPpi", 300.0));
            assertThat(changedPaths(base, child)).isEqualTo(List.of("crop.anchor"));
        }

        @Test
        void saysNothingWhenTheChildChangedNothing() {
            Map<String, Object> base = bag("crop", bag("anchor", "sheet"));
            assertThat(changedPaths(base, bag("crop", bag("anchor", "sheet")))).isEqualTo(List.of());
        }

        @Test
        void reportsAWholeListAsOnePath() {
            Map<String, Object> base = bag("derivatives", list(bag("name", "a")));
            Map<String, Object> child = bag("derivatives", list(bag("name", "a"), bag("name", "b")));
            assertThat(changedPaths(base, child)).isEqualTo(List.of("derivatives"));
        }

        @Test
        void reportsASettingTheBaseDidNotHave() {
            assertThat(changedPaths(bag(), bag("crop", bag("anchor", "sheet")))).isEqualTo(List.of("crop"));
        }
    }
}
