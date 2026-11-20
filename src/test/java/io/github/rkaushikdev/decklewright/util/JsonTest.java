package io.github.rkaushikdev.decklewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTest {

    private static Map<String, Object> ordered(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private record Corner(double x, double y) implements JsonValue {
        @Override
        public Object toJson() {
            return ordered("x", x, "y", y);
        }
    }

    @Nested
    class Stringify {
        @Test
        void writesCompactJsonInInsertionOrder() {
            Map<String, Object> doc = ordered("name", "q17", "ppi", 400.0, "skew", -0.25, "ok", true, "none", null);
            assertThat(Json.stringify(doc))
                    .isEqualTo("{\"name\":\"q17\",\"ppi\":400,\"skew\":-0.25,\"ok\":true,\"none\":null}");
        }

        @Test
        void indentsOneMemberPerLine() {
            Map<String, Object> doc = ordered("a", 1, "b", List.of(1, 2), "c", Map.of(), "d", List.of());
            assertThat(Json.stringify(doc, 2))
                    .isEqualTo("{\n  \"a\": 1,\n  \"b\": [\n    1,\n    2\n  ],\n  \"c\": {},\n  \"d\": []\n}");
        }

        @Test
        void writesNonFiniteNumbersAsNull() {
            assertThat(Json.stringify(List.of(Double.NaN, Double.POSITIVE_INFINITY, 1.5f)))
                    .isEqualTo("[null,null,1.5]");
        }

        @Test
        void leavesOutAnAbsentMemberAndNullsAnAbsentSlot() {
            Map<String, Object> doc = ordered("gone", Optional.empty(), "kept", Optional.of("yes"));
            assertThat(Json.stringify(doc)).isEqualTo("{\"kept\":\"yes\"}");
            assertThat(Json.stringify(ordered("gone", Optional.empty()), 2)).isEqualTo("{}");
            List<Object> slots = new ArrayList<>();
            slots.add(Optional.empty());
            slots.add(2);
            assertThat(Json.stringify(slots)).isEqualTo("[null,2]");
            assertThat(Json.stringify(Optional.empty())).isEqualTo("null");
        }

        @Test
        void escapesQuotesBackslashesControlsAndLoneSurrogates() {
            assertThat(Json.quote("a\"b\\c\n\t\r\b\f\u0001/é")).isEqualTo("\"a\\\"b\\\\c\\n\\t\\r\\b\\f\\u0001/é\"");
            assertThat(Json.quote("\ud800")).isEqualTo("\"\\ud800\"");
            assertThat(Json.quote("\udc00x")).isEqualTo("\"\\udc00x\"");
            assertThat(Json.quote("\ud83d\ude00")).isEqualTo("\"\ud83d\ude00\"");
        }

        @Test
        void writesValuesThatKnowTheirOwnForm() {
            assertThat(Json.stringify(List.of(new Corner(1, 2.5)))).isEqualTo("[{\"x\":1,\"y\":2.5}]");
            assertThat(Json.stringify(Instant.parse("2025-12-18T12:00:00Z"))).isEqualTo("\"2025-12-18T12:00:00.000Z\"");
            assertThat(Json.stringify('c')).isEqualTo("\"c\"");
        }

        @Test
        void writesArraysOfEveryKind() {
            assertThat(Json.stringify(new double[] {1, 0.5})).isEqualTo("[1,0.5]");
            assertThat(Json.stringify(new int[] {3, 4})).isEqualTo("[3,4]");
            assertThat(Json.stringify(new long[] {5})).isEqualTo("[5]");
            assertThat(Json.stringify(new Object[] {"a", 1})).isEqualTo("[\"a\",1]");
        }

        @Test
        void capsTheIndentAtTen() {
            assertThat(Json.stringify(List.of(1), 40)).isEqualTo("[\n          1\n]");
        }

        @Test
        void refusesWhatHasNoJsonForm() {
            assertThatThrownBy(() -> Json.stringify(new Object()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot write");
        }
    }

    @Nested
    class Parse {
        @Test
        void readsEveryKindOfValue() {
            Object doc = Json.parse(" {\"a\": [1, 2.5, -3e2, true, false, null], \"b\": {\"c\": \"d\\u00e9\\n\"}} ");
            Map<String, Object> expected = ordered(
                    "a", java.util.Arrays.asList(1.0, 2.5, -300.0, true, false, null), "b", ordered("c", "dé\n"));
            assertThat(doc).isEqualTo(expected);
        }

        @Test
        void keepsTheKeyOrderAndLetsTheLastDuplicateWin() {
            @SuppressWarnings("unchecked")
            Map<String, Object> doc = (Map<String, Object>) Json.parse("{\"z\":1,\"a\":2,\"z\":3}");
            assertThat(new ArrayList<>(doc.keySet())).containsExactly("z", "a");
            assertThat(doc).containsEntry("z", 3.0);
        }

        @Test
        void readsEscapesAndEmptyContainers() {
            assertThat(Json.parse("\"\\\"\\\\\\/\\b\\f\\r\\t\"")).isEqualTo("\"\\/\b\f\r\t");
            assertThat(Json.parse("[]")).isEqualTo(List.of());
            assertThat(Json.parse("{}")).isEqualTo(Map.of());
            assertThat(Json.parse("0")).isEqualTo(0.0);
        }

        @Test
        void roundTripsWhatItWrites() {
            Map<String, Object> doc = ordered("name", "q17", "sizes", List.of(1.0, 0.5), "nested", ordered("ok", true));
            assertThat(Json.parse(Json.stringify(doc, 2))).isEqualTo(doc);
        }

        @ParameterizedTest(name = "refuses {0}")
        @ValueSource(
                strings = {
                    "",
                    "not json at all",
                    "{\"a\":1,}",
                    "[1,]",
                    "{a:1}",
                    "01",
                    "1.",
                    "1e",
                    "-",
                    "\"open",
                    "\"bad \\x escape\"",
                    "\"\\u12\"",
                    "\"\\u12zz\"",
                    "[1 2]",
                    "{\"a\" 1}",
                    "{\"a\":1 \"b\":2}",
                    "tru",
                    "1 2",
                    "\"tab\tinside\"",
                    "\"ends with \\"
                })
        void refusesWhatIsNotJson(String text) {
            assertThatThrownBy(() -> Json.parse(text))
                    .isInstanceOf(JsonSyntaxException.class)
                    .hasMessageContaining("json");
        }

        @Test
        void saysWhereItGaveUp() {
            assertThatThrownBy(() -> Json.parse("[1,]")).isInstanceOfSatisfying(JsonSyntaxException.class, err -> {
                assertThat(err.position()).isEqualTo(3);
                assertThat(err.getMessage()).isEqualTo("unexpected token ']' in json at position 3");
            });
        }
    }
}
