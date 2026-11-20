package io.github.rkaushikdev.decklewright.manifest;

import static io.github.rkaushikdev.decklewright.manifest.Provenance.EVENT_TYPES;
import static io.github.rkaushikdev.decklewright.manifest.Provenance.agentName;
import static io.github.rkaushikdev.decklewright.manifest.Provenance.breaksIn;
import static io.github.rkaushikdev.decklewright.manifest.Provenance.chainDigest;
import static io.github.rkaushikdev.decklewright.manifest.Provenance.chainFor;
import static io.github.rkaushikdev.decklewright.manifest.Provenance.describeChain;
import static io.github.rkaushikdev.decklewright.manifest.Provenance.digestableForm;
import static io.github.rkaushikdev.decklewright.manifest.Provenance.parseChain;
import static io.github.rkaushikdev.decklewright.manifest.Provenance.recordEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.ManifestError;
import io.github.rkaushikdev.decklewright.testing.Accession;
import io.github.rkaushikdev.decklewright.util.Json;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The provenance chain: events appended in order, each pointing at the one before it. */
class ProvenanceTest {

    private static final Instant AT = Instant.parse("2025-11-11T08:00:00Z");

    private static final EventOptions STAMPED = EventOptions.defaults().withAt(AT);

    /** The accession every chain here is built from. */
    private static Accession.Options options() {
        return Accession.Options.defaults().withId("g4181");
    }

    private static List<ProvenanceEvent> chain(Accession.Options options) {
        return chainFor(agentName("1.4.0"), Accession.worked(options).run(), AT);
    }

    private static List<ProvenanceEvent> chain() {
        return chain(options());
    }

    private static ProvenanceEvent handMade(Map<String, Object> values) {
        return new ProvenanceEvent("note", "x", "a", "d", Optional.of(values), "p");
    }

    private static Map<String, Object> ordered(String first, Object one, String second, Object two) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(first, one);
        out.put(second, two);
        return out;
    }

    @Nested
    class AgentName {
        @Test
        void namesTheSoftwareAndTheVersion() {
            assertThat(agentName("1.4.0")).isEqualTo("decklewright 1.4.0");
        }

        @Test
        void namesTheStandWhenALabHasSeveral() {
            assertThat(agentName("1.4.0", "stand-2")).isEqualTo("decklewright 1.4.0 on stand-2");
        }
    }

    @Nested
    class RecordEvent {
        @Test
        void appendsWithoutTouchingTheChainItWasGiven() {
            List<ProvenanceEvent> first = recordEvent(List.of(), "note", "a", "one", STAMPED);
            List<ProvenanceEvent> second = recordEvent(first, "note", "a", "two", STAMPED);
            assertThat(first).hasSize(1);
            assertThat(second).hasSize(2);
        }

        @Test
        void pointsEachEventAtTheOneBeforeIt() {
            List<ProvenanceEvent> chain =
                    recordEvent(recordEvent(List.of(), "note", "a", "one", STAMPED), "note", "a", "two", STAMPED);
            assertThat(chain.get(1).previous()).isEqualTo(chainDigest(chain.subList(0, 1)));
        }

        @Test
        void copiesTheValuesRatherThanSharingThem() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("degrees", 2.0);
            List<ProvenanceEvent> chain = recordEvent(List.of(), "note", "a", "x", STAMPED.withValues(values));
            values.put("degrees", 9.0);
            assertThat(chain.get(0).values())
                    .hasValueSatisfying(held -> assertThat(held.get("degrees")).isEqualTo(2.0));
        }

        @Test
        void leavesTheValuesOutWhenThereAreNone() {
            assertThat(recordEvent(List.of(), "note", "a", "x", STAMPED).get(0).values())
                    .isEmpty();
        }

        @Test
        void takesATimeRatherThanAlwaysReadingTheClock() {
            assertThat(recordEvent(List.of(), "note", "a", "x", STAMPED).get(0).at())
                    .isEqualTo("2025-11-11T08:00:00.000Z");
        }

        @Test
        void refusesAKindOfEventNothingRecords() {
            assertThatThrownBy(() -> recordEvent(List.of(), "reticulated", "a", "x", STAMPED))
                    .isInstanceOf(ManifestError.class)
                    .hasMessageFindingMatch("nothing records an event of that kind");
        }
    }

    @Nested
    class DigestableForm {
        @Test
        void sortsTheValuesSoKeyOrderCannotChangeTheDigest() {
            String one = digestableForm(handMade(ordered("b", 2.0, "a", 1.0)));
            String two = digestableForm(handMade(ordered("a", 1.0, "b", 2.0)));
            assertThat(one).isEqualTo(two);
        }

        @Test
        void includesTheValuesSoAChangedOneBreaksTheChain() {
            assertThat(digestableForm(handMade(Map.of("a", 1.0))))
                    .isNotEqualTo(digestableForm(handMade(Map.of("a", 2.0))));
        }
    }

    @Nested
    class BreaksIn {
        private final List<ProvenanceEvent> good = chain();

        @Test
        void findsNothingWrongWithAChainItBuiltItself() {
            assertThat(breaksIn(good)).isEqualTo(List.of());
        }

        @Test
        void findsNothingWrongWithAnEmptyChain() {
            assertThat(breaksIn(List.of())).isEqualTo(List.of());
        }

        @Test
        void catchesAnEventEditedAfterTheFact() {
            List<ProvenanceEvent> tampered = IntStream.range(0, good.size())
                    .mapToObj(i -> {
                        ProvenanceEvent event = good.get(i);
                        return i == 1
                                ? new ProvenanceEvent(
                                        event.type(),
                                        event.at(),
                                        event.agent(),
                                        "nothing to see",
                                        event.values(),
                                        event.previous())
                                : event;
                    })
                    .toList();
            assertThat(breaksIn(tampered).size()).isGreaterThan(0);
        }

        @Test
        void catchesAnEventDroppedOutOfTheMiddle() {
            List<ProvenanceEvent> shortened = Stream.concat(
                            good.subList(0, 2).stream(), good.subList(3, good.size()).stream())
                    .toList();
            assertThat(breaksIn(shortened).get(0).at()).isEqualTo(2);
        }

        @Test
        void catchesAKindOfEventNothingRecords() {
            List<ProvenanceEvent> wrong = new ArrayList<>(good);
            ProvenanceEvent first = wrong.get(0);
            wrong.set(
                    0,
                    new ProvenanceEvent(
                            "invented", first.at(), first.agent(), first.detail(), first.values(), first.previous()));
            assertThat(breaksIn(wrong).stream().anyMatch(found -> found.reason().contains("nothing records")))
                    .isTrue();
        }
    }

    @Nested
    class ChainFor {
        @Test
        void recordsTheRunInTheOrderItHappened() {
            List<String> types = chain().stream().map(ProvenanceEvent::type).toList();
            assertThat(types.subList(0, 3)).isEqualTo(List.of("capture-read", "sheet-found", "crop-planned"));
            assertThat(types.get(types.size() - 1)).isEqualTo("quality-checked");
        }

        @Test
        void recordsOneEventPerFile() {
            List<ProvenanceEvent> made = chain().stream()
                    .filter(event -> event.type().equals("derivative-made"))
                    .toList();
            assertThat(made).hasSize(3);
            assertThat(made.get(0).values()).hasValueSatisfying(values -> assertThat((String) values.get("file"))
                    .containsPattern("_master\\.png\\z"));
        }

        @Test
        void recordsTheDeskewOnlyWhenThereWasOne() {
            assertThat(chain().stream().anyMatch(event -> event.type().equals("deskewed")))
                    .isFalse();
            assertThat(chain(options().withDegrees(3)).stream()
                            .anyMatch(event -> event.type().equals("deskewed")))
                    .isTrue();
        }

        @Test
        void leavesTheGradingOutWhenNothingWasGraded() {
            assertThat(chain(options().withGraded(false)).stream()
                            .anyMatch(event -> event.type().equals("quality-checked")))
                    .isFalse();
        }

        @Test
        void namesTheSameAgentThroughout() {
            Set<String> agents = chain().stream().map(ProvenanceEvent::agent).collect(Collectors.toSet());
            assertThat(agents).hasSize(1);
        }

        @Test
        void recordsEveryKindItUsesAsOneTheReaderKnows() {
            for (ProvenanceEvent event : chain(options().withDegrees(3))) {
                assertThat(EVENT_TYPES).contains(event.type());
            }
        }
    }

    @Nested
    class DescribeChain {
        @Test
        void writesOneLinePerEvent() {
            List<String> lines = describeChain(chain());
            assertThat(lines).hasSize(chain().size());
            assertThat(lines.get(0)).containsPattern("^2025-11-11T08:00:00\\.000Z capture-read: ");
        }
    }

    @Nested
    class ParseChain {
        @Test
        void readsAChainBackThroughJson() {
            List<ProvenanceEvent> chain = chain();
            assertThat(parseChain(Json.parse(Json.stringify(chain)))).hasSize(chain.size());
        }

        @Test
        void refusesAnythingThatIsNotAList() {
            assertThatThrownBy(() -> parseChain(Map.of("events", List.of())))
                    .isInstanceOf(ManifestError.class)
                    .hasMessageFindingMatch("has to be a list");
        }

        @Test
        void refusesAChainWithABreakInIt() {
            List<ProvenanceEvent> chain = chain();
            List<ProvenanceEvent> tampered = Stream.concat(
                            chain.subList(0, 1).stream(), chain.subList(2, chain.size()).stream())
                    .toList();
            assertThatThrownBy(() -> parseChain(tampered))
                    .isInstanceOf(ManifestError.class)
                    .hasMessageFindingMatch("does not follow");
        }

        @Test
        void readsAnEmptyChain() {
            assertThat(parseChain(List.of())).isEqualTo(List.of());
        }
    }
}
