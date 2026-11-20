package io.github.rkaushikdev.decklewright.plan;

import static io.github.rkaushikdev.decklewright.plan.Corpus.CASES;
import static io.github.rkaushikdev.decklewright.plan.Corpus.describeResults;
import static io.github.rkaushikdev.decklewright.plan.Corpus.runCase;
import static io.github.rkaushikdev.decklewright.plan.Corpus.runCorpus;
import static io.github.rkaushikdev.decklewright.plan.Corpus.tally;
import static io.github.rkaushikdev.decklewright.profile.ProfileRegistry.registryWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CorpusTest {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();

    /** The shipped profiles with house written over to keep nine millimetres of bleed. */
    private static ProfileRegistry wideBleed() {
        return registryWith(List.of(Map.of("name", "house", "crop", Map.of("bleed", "9mm"))));
    }

    /** The shipped profiles with the named one cropping to a neatline its captures do not have. */
    private static ProfileRegistry wantingANeatline(String name) {
        return registryWith(List.of(
                Map.of("name", name, "crop", Map.of("anchor", "neatline"), "capture", Map.of("expectNeatline", true))));
    }

    @Nested
    class TheCorpusItself {
        @Test
        void coversEveryShippedProfile() {
            Set<String> covered = CASES.stream().map(Corpus.Case::profile).collect(Collectors.toSet());
            for (String name : REGISTRY.names()) {
                if (name.equals("house")) {
                    continue;
                }
                assertThat(covered.contains(name)).as(name).isTrue();
            }
        }

        @Test
        void isFrozenAndEveryCaseHasAName() {
            Corpus.Case any = CASES.get(0);
            assertThatThrownBy(() -> CASES.add(any)).isInstanceOf(UnsupportedOperationException.class);
            assertThat(CASES.stream().map(Corpus.Case::name).collect(Collectors.toSet()))
                    .hasSize(CASES.size());
        }

        @Test
        void holdsACaseForEachThingThatHasGoneWrong() {
            String names = CASES.stream().map(Corpus.Case::name).collect(Collectors.joining(" "));
            assertThat(names).containsPattern("neatline");
            assertThat(names).containsPattern("edge of the bed");
            assertThat(names).containsPattern("under the floor");
        }
    }

    @Nested
    class RunCorpus {
        @Test
        void reproducesEveryRecordedAnswer() {
            List<Corpus.CaseResult> results = runCorpus(REGISTRY);
            List<Corpus.CaseResult> changed =
                    results.stream().filter(r -> !r.matched()).toList();
            assertThat(describeResults(changed)).isEqualTo(List.of());
        }

        @Test
        void talliesWhatItFound() {
            assertThat(tally(runCorpus(REGISTRY))).isEqualTo(new Corpus.Tally(CASES.size(), CASES.size(), 0, 0));
        }

        @Test
        void noticesWhenAProfileChangesUnderneathIt() {
            List<Corpus.CaseResult> results = runCorpus(wideBleed());
            assertThat(tally(results).changed()).isGreaterThan(0);
        }

        @Test
        void reportsACaseThatCouldNotBePlannedRatherThanThrowing() {
            List<Corpus.CaseResult> results = runCorpus(wantingANeatline("microfilm"));
            Optional<Corpus.CaseResult> film =
                    results.stream().filter(r -> r.name().contains("microfilm")).findFirst();
            assertThat(film.flatMap(Corpus.CaseResult::error))
                    .hasValueSatisfying(error -> assertThat(error).containsPattern("none was found"));
            assertThat(tally(results).failed()).isEqualTo(1);
        }

        @Test
        void takesASetOfCasesOfItsOwn() {
            List<Corpus.CaseResult> results = runCorpus(REGISTRY, List.of(CASES.get(0)));
            assertThat(results).hasSize(1);
            assertThat(results.get(0).matched()).isTrue();
        }
    }

    @Nested
    class RunCase {
        @Test
        void givesBackAPlanWithATrail() {
            CropPlan plan = runCase(CASES.get(1), REGISTRY);
            assertThat(plan.trail()).contains("bleed");
            assertThat(plan.rect().width()).isEqualTo(CASES.get(1).expected().width());
        }

        @Test
        void warnsOnTheCaseThatWasRecordedAsWarning() {
            Corpus.Case edge = CASES.stream()
                    .filter(c -> c.name().contains("edge of the bed"))
                    .findFirst()
                    .orElseThrow();
            assertThat(runCase(edge, REGISTRY).warnings()).hasSize(1);
        }
    }

    @Nested
    class DescribeResults {
        @Test
        void saysACaseIsAsRecordedWhenItIs() {
            List<String> lines = describeResults(runCorpus(REGISTRY, List.of(CASES.get(0))));
            assertThat(lines.get(0)).containsPattern("as recorded$");
        }

        @Test
        void showsBothCropsWhenACaseChanged() {
            List<String> lines = describeResults(runCorpus(wideBleed(), List.of(CASES.get(0))));
            assertThat(lines.get(0)).containsPattern("was 6800x5600\\+700\\+600, now ");
        }

        @Test
        void saysWhatWentWrongWhenACaseFailed() {
            assertThat(describeResults(runCorpus(wantingANeatline("house"), List.of(CASES.get(0))))
                            .get(0))
                    .containsPattern("failed, ");
        }
    }
}
