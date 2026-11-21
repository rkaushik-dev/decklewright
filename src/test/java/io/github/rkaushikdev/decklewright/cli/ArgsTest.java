package io.github.rkaushikdev.decklewright.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DeckleError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArgsTest {

    private static final Map<String, OptionSpec> SPEC = Args.spec(
            Map.entry("profile", OptionSpec.string("which collection profile").withLetter('p')),
            Map.entry("ppi", OptionSpec.number("the sampling frequency").withLetter('r')),
            Map.entry("force", OptionSpec.flag("replace what is there").withLetter('f')),
            Map.entry("quiet", OptionSpec.flag("say less").withLetter('q')),
            Map.entry("exclude", OptionSpec.string("leave a derivative out").repeated()),
            Map.entry("stars", OptionSpec.number("the level to deliver at").withFallback(3)));

    private static ParsedArgs parse(String... argv) {
        return Args.parseArgs(List.of(argv), SPEC);
    }

    @Nested
    class ParseArgs {
        @Test
        void readsALongOptionWithTheValueAfterASpace() {
            assertThat(parse("--profile", "quadrangles").options().get("profile"))
                    .isEqualTo("quadrangles");
        }

        @Test
        void readsALongOptionWithTheValueAfterAnEqualsSign() {
            assertThat(parse("--profile=quadrangles").options().get("profile")).isEqualTo("quadrangles");
        }

        @Test
        void readsAShortOptionWithTheValueAfterASpace() {
            assertThat(parse("-p", "atlas-plates").options().get("profile")).isEqualTo("atlas-plates");
        }

        @Test
        void readsAShortOptionWithTheValueStuckToIt() {
            assertThat(parse("-patlas-plates").options().get("profile")).isEqualTo("atlas-plates");
        }

        @Test
        void turnsANumberOptionIntoANumber() {
            Map<String, Object> out = parse("--ppi", "600").options();
            assertThat(out.get("ppi")).isEqualTo(600.0);
            assertThat(out.get("ppi")).isInstanceOf(Double.class);
        }

        @Test
        void refusesANumberOptionThatIsNotANumber() {
            assertThatThrownBy(() -> parse("--ppi", "six hundred"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wants a number");
        }

        @Test
        void readsAFlagAsTrue() {
            assertThat(parse("--force").options().get("force")).isEqualTo(true);
            assertThat(parse("--quiet").options().get("quiet")).isEqualTo(true);
        }

        @Test
        void readsAnExplicitFalseOnAFlag() {
            assertThat(parse("--force=false").options().get("force")).isEqualTo(false);
        }

        @Test
        void bundlesShortFlags() {
            Map<String, Object> out = parse("-fq").options();
            assertThat(out.get("force")).isEqualTo(true);
            assertThat(out.get("quiet")).isEqualTo(true);
        }

        @Test
        void takesAValueAtTheEndOfABundle() {
            Map<String, Object> out = parse("-fr", "400").options();
            assertThat(out.get("force")).isEqualTo(true);
            assertThat(out.get("ppi")).isEqualTo(400.0);
        }

        @Test
        void collectsARepeatedOptionIntoAList() {
            Map<String, Object> out =
                    parse("--exclude", "thumb", "--exclude", "service").options();
            assertThat(out.get("exclude")).isEqualTo(List.of("thumb", "service"));
        }

        @Test
        void givesAnEmptyListForARepeatedOptionNobodyUsed() {
            assertThat(parse().options().get("exclude")).isEqualTo(List.of());
        }

        @Test
        void fillsInAFallback() {
            assertThat(parse().options().get("stars")).isEqualTo(3.0);
            assertThat(parse("--stars", "4").options().get("stars")).isEqualTo(4.0);
        }

        @Test
        void keepsThePositionalsInOrder() {
            ParsedArgs out = parse("crop", "g4181.png", "--force", "g4182.png");
            assertThat(out.positional()).containsExactly("crop", "g4181.png", "g4182.png");
        }

        @Test
        void stopsReadingOptionsAtALoneDoubleDash() {
            ParsedArgs out = parse("--force", "--", "--profile", "x");
            assertThat(out.options().get("force")).isEqualTo(true);
            assertThat(out.rest()).containsExactly("--profile", "x");
            assertThat(out.options()).doesNotContainKey("profile");
        }

        @Test
        void treatsALoneDashAsAPositionalBecauseThatIsWhatItMeans() {
            assertThat(parse("-").positional()).containsExactly("-");
        }

        @Test
        void refusesAnOptionTheCommandDoesNotHave() {
            assertThatThrownBy(() -> parse("--colour"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no such option");
            assertThatThrownBy(() -> parse("-z"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no such option");
        }

        @Test
        void namesTheOptionItDidNotRecognise() {
            assertThatThrownBy(() -> parse("--colour"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no such option")
                    .satisfies(thrown ->
                            assertThat(((DeckleError) thrown).details()).containsEntry("option", "--colour"));
        }

        @Test
        void refusesAnOptionThatWantsAValueAndIsLast() {
            assertThatThrownBy(() -> parse("--profile"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wants a value");
            assertThatThrownBy(() -> parse("-p"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wants a value");
        }

        @Test
        void refusesAnOptionThatWantsAValueAndGotAnotherOption() {
            assertThatThrownBy(() -> parse("--profile", "--force"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wants a value");
        }

        @Test
        void takesAValueThatLooksLikeANegativeNumber() {
            Map<String, OptionSpec> spec = Args.spec(Map.entry("skew", OptionSpec.number("degrees")));
            assertThat(Args.parseArgs(List.of("--skew", "-2"), spec).options().get("skew"))
                    .isEqualTo(-2.0);
        }

        @Test
        void isHappyWithNothingAtAll() {
            ParsedArgs out = Args.parseArgs(List.of(), Map.of());
            assertThat(out).isEqualTo(new ParsedArgs(Map.of(), List.of(), List.of()));
        }

        @Test
        void refusesASpecWithTwoOptionsWantingTheSameLetter() {
            Map<String, OptionSpec> clashing = Args.spec(
                    Map.entry("force", OptionSpec.flag("one").withLetter('f')),
                    Map.entry("fast", OptionSpec.flag("two").withLetter('f')));
            assertThatThrownBy(() -> Args.parseArgs(List.of(), clashing))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("same letter");
        }
    }

    @Nested
    class DescribeOptions {
        @Test
        void writesALinePerOption() {
            assertThat(Args.describeOptions(SPEC)).hasSize(SPEC.size());
        }

        @Test
        void showsTheShortLetterAndTheLongName() {
            assertThat(Args.describeOptions(SPEC).get(0)).containsPattern("-p, --profile <string>");
        }

        @Test
        void linesTheDescriptionsUp() {
            List<String> lines = Args.describeOptions(SPEC);
            List<Integer> at = new ArrayList<>();
            int i = 0;
            for (OptionSpec one : SPEC.values()) {
                at.add(lines.get(i).indexOf(one.describe()));
                i += 1;
            }
            Set<Integer> distinct = new HashSet<>(at);
            assertThat(distinct).hasSize(1);
            assertThat(at.get(0)).isGreaterThan(0);
        }

        @Test
        void showsAFallbackWhereThereIsOne() {
            assertThat(String.join("\n", Args.describeOptions(SPEC)))
                    .containsPattern("the level to deliver at \\(3\\)");
        }

        @Test
        void leavesTheTypeOffAFlag() {
            assertThat(Args.describeOptions(Args.spec(Map.entry("quiet", OptionSpec.flag("say less"))))
                            .get(0))
                    .doesNotContain("<");
        }

        @Test
        void hasNothingToSayAboutACommandWithNoOptions() {
            assertThat(Args.describeOptions(Map.of())).isEmpty();
        }
    }

    @Nested
    class MissingOptions {
        @Test
        void namesWhatACommandNeedsAndDidNotGet() {
            assertThat(Args.missingOptions(Map.of("profile", "house"), List.of("profile", "ppi")))
                    .containsExactly("ppi");
        }

        @Test
        void countsAFalseFlagAsGiven() {
            assertThat(Args.missingOptions(Map.of("force", false), List.of("force")))
                    .isEmpty();
        }

        @Test
        void hasNothingToSayWhenEverythingIsThere() {
            assertThat(Args.missingOptions(Map.of("a", 1.0, "b", 2.0), List.of("a", "b")))
                    .isEmpty();
        }
    }
}
