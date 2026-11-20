package io.github.rkaushikdev.decklewright.profile;

import static io.github.rkaushikdev.decklewright.profile.Documents.bag;
import static io.github.rkaushikdev.decklewright.profile.Documents.list;
import static io.github.rkaushikdev.decklewright.profile.ProfileRegistry.registryWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import io.github.rkaushikdev.decklewright.ProfileError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegistryTest {

    @Nested
    class AFreshRegistry {
        @Test
        void holdsTheShippedProfiles() {
            ProfileRegistry registry = new ProfileRegistry();
            assertThat(registry.names()).contains("house");
            assertThat(registry.names()).contains("quadrangles");
            assertThat(registry.has("microfilm")).isTrue();
            assertThat(registry.has("plat-books")).isFalse();
        }

        @Test
        void canBeBuiltWithNothingInIt() {
            assertThat(new ProfileRegistry(false).names()).isEqualTo(List.of());
        }

        @Test
        void namesWhatItKnowsWhenALookupMisses() {
            ProfileError err = catchThrowableOfType(ProfileError.class, () -> new ProfileRegistry().get("plat-books"));
            assertThat(err.getMessage()).isEqualTo("there is no profile called plat-books");
            assertThat(err.details().get("known")).asInstanceOf(LIST).contains("house");
        }
    }

    @Nested
    class Get {
        private final ProfileRegistry registry = new ProfileRegistry();

        @Test
        void flattensTheExtendsChain() {
            Profile profile = registry.get("quadrangles");
            assertThat(profile.crop().anchor()).isEqualTo(Anchor.NEATLINE);
            assertThat(profile.naming().pattern()).isEqualTo("{accession}_{derivative}");
            assertThat(profile.derivatives()).hasSize(3);
        }

        @Test
        void handsBackTheSameObjectTwice() {
            assertThat(registry.get("atlas-plates")).isSameAs(registry.get("atlas-plates"));
        }

        @Test
        void refusesAProfileThatCannotBeUsed() {
            ProfileRegistry broken =
                    registryWith(List.of(bag("name", "broken", "extends", "house", "crop", bag("anchor", "neatline"))));
            assertThatThrownBy(() -> broken.get("broken"))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("the broken profile cannot be used");
        }
    }

    @Nested
    class Add {
        @Test
        void takesASiteProfileThatExtendsAShippedOne() {
            ProfileRegistry registry = registryWith(
                    List.of(bag("name", "plat-books", "extends", "atlas-plates", "crop", bag("bleed", "5mm"))));
            Profile profile = registry.get("plat-books");
            assertThat(profile.crop().bleed().top().value()).isEqualTo(5);
            assertThat(profile.crop().minimumPpi()).isEqualTo(400);
            assertThat(profile.qc().samplingPpi()).isEqualTo(600);
        }

        @Test
        void letsASiteReplaceAShippedProfile() {
            ProfileRegistry registry = registryWith(List.of(bag("name", "house", "crop", bag("minimumPpi", 150.0))));
            assertThat(registry.get("house").crop().minimumPpi()).isEqualTo(150);
            assertThat(registry.get("atlas-plates").crop().minimumPpi()).isEqualTo(400);
        }

        @Test
        void clearsWhatItHadAlreadyWorkedOut() {
            ProfileRegistry registry = new ProfileRegistry();
            assertThat(registry.get("house").crop().minimumPpi()).isEqualTo(300);
            registry.add(bag("name", "house", "crop", bag("minimumPpi", 150.0)));
            assertThat(registry.get("house").crop().minimumPpi()).isEqualTo(150);
        }

        @Test
        void refusesADocumentWithNoName() {
            assertThatThrownBy(() -> new ProfileRegistry().add(bag("crop", bag())))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("needs a name");
        }

        @Test
        void returnsItselfSoCallsChain() {
            ProfileRegistry registry = new ProfileRegistry(false);
            assertThat(registry.add(bag("name", "a")).add(bag("name", "b")).names())
                    .isEqualTo(List.of("a", "b"));
        }
    }

    @Nested
    class Document {
        @Test
        void handsBackWhatWasWrittenNotWhatWasWorkedOut() {
            ProfileRegistry registry = new ProfileRegistry();
            assertThat(registry.document("quadrangles").get("extends")).isEqualTo("house");
            assertThat(registry.get("quadrangles").parent()).isEmpty();
        }
    }

    @Nested
    class Inspect {
        @Test
        void saysNothingAboutAShippedProfile() {
            assertThat(new ProfileRegistry().inspect("quadrangles")).isEqualTo(List.of());
        }

        @Test
        void reportsTheProblemsOfASiteProfileWithoutThrowing() {
            ProfileRegistry registry =
                    registryWith(List.of(bag("name", "odd", "extends", "house", "crop", bag("anchor", "neatline"))));
            List<Problem> problems = registry.inspect("odd");
            assertThat(problems).hasSize(1);
            assertThat(problems.get(0).severity()).isEqualTo(Severity.FAULT);
        }
    }

    @Nested
    class Explain {
        private final ProfileRegistry registry = new ProfileRegistry();

        @Test
        void saysWhatAProfileInheritedAndWhatItChanged() {
            ProfileRegistry.Explanation out = registry.explain("atlas-plates");
            assertThat(out.chain()).isEqualTo(List.of("house"));
            assertThat(out.changed()).contains("crop.minimumPpi");
            assertThat(out.changed()).contains("qc.samplingPpi");
            assertThat(out.changed()).doesNotContain("naming.pattern");
        }

        @Test
        void saysNothingWasInheritedForARootProfile() {
            ProfileRegistry.Explanation out = registry.explain("house");
            assertThat(out.chain()).isEqualTo(List.of());
            assertThat(out.changed().size()).isGreaterThan(0);
        }

        @Test
        void countsTheProblemsItFound() {
            ProfileRegistry registry2 =
                    registryWith(List.of(bag("name", "noisy", "extends", "house", "derivatives", list())));
            assertThat(registry2.explain("noisy").problems()).isGreaterThan(0);
        }
    }

    @Nested
    class InspectAll {
        @Test
        void saysNothingAboutARegistryOfShippedProfiles() {
            assertThat(new ProfileRegistry().inspectAll()).isEqualTo(Map.of());
        }

        @Test
        void collectsTheProblemsOfEveryProfileThatHasAny() {
            ProfileRegistry registry = registryWith(List.of(
                    bag("name", "bad", "extends", "house", "crop", bag("anchor", "neatline")),
                    bag("name", "quiet", "extends", "house")));
            Map<String, List<String>> out = registry.inspectAll();
            assertThat(out.keySet()).containsExactly("bad");
            assertThat(out.get("bad").get(0)).containsPattern("crop.anchor");
        }

        @Test
        void catchesAProfileWhoseParentIsMissing() {
            ProfileRegistry registry = registryWith(List.of(bag("name", "orphan", "extends", "nowhere")));
            assertThat(registry.inspectAll().get("orphan").get(0)).containsPattern("no profile called nowhere");
        }
    }
}
