package io.github.rkaushikdev.decklewright.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The json the lab writes and reads: sidecars, provenance chains, the ledger and batch reports.
 *
 * <p>Output is byte for byte what the lab's files have always held. Keys keep their insertion
 * order, numbers are written by {@link NumberText#of(double)}, a non finite number is written as
 * {@code null}, and an indented document puts {@code ": "} between a key and its value, one member
 * per line, with empty objects and arrays written as {@code {}} and {@code []}. Those files are
 * digested and the digests are delivered, so a formatting difference is a fixity failure.
 *
 * <p>What can be written: null, booleans, numbers, strings and characters, {@link Instant}s (as
 * {@link IsoTime#format(Instant)} writes them), {@link JsonValue}s (through their {@link
 * JsonValue#toJson()}), maps (keys written with {@link String#valueOf(Object)}), iterables, object
 * arrays and primitive number arrays, and {@link Optional}s. An empty optional stands for a value
 * that is not there at all: a map entry holding one is left out, and an array slot holding one is
 * written as {@code null}. Anything else is refused rather than guessed at.
 *
 * <p>What reading gives back: a {@link LinkedHashMap} for an object, an {@link ArrayList} for an
 * array, a {@link Double} for every number, a {@link String}, a {@link Boolean}, or null. The
 * collections are mutable, because callers migrate and overlay what they read.
 */
public final class Json {
    private static final int MAX_INDENT = 10;

    private Json() {}

    /** The value as compact json, with no whitespace between tokens. */
    public static String stringify(Object value) {
        return stringify(value, 0);
    }

    /**
     * The value as json, indented by {@code indent} spaces per level.
     *
     * @param indent spaces per level; zero or less writes compact json, more than ten counts as ten
     * @throws IllegalArgumentException when the value holds something that has no json form
     */
    public static String stringify(Object value, int indent) {
        String gap = " ".repeat(Math.max(0, Math.min(MAX_INDENT, indent)));
        StringBuilder out = new StringBuilder();
        if (!write(out, value, gap, "")) {
            out.append("null");
        }
        return out.toString();
    }

    /**
     * Read one json document.
     *
     * @throws JsonSyntaxException when the text is not exactly one well formed json value
     */
    public static Object parse(String text) {
        return new Reader(text).document();
    }

    /** The text as a quoted json string, escaped exactly as the lab's files escape it. */
    public static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2);
        quote(out, text);
        return out.toString();
    }

    /** Writes the value; false means it is absent and was not written. */
    private static boolean write(StringBuilder out, Object value, String gap, String indent) {
        Object plain = value;
        while (plain instanceof JsonValue json) {
            plain = json.toJson();
        }
        switch (plain) {
            case null -> out.append("null");
            case Optional<?> optional -> {
                if (optional.isEmpty()) {
                    return false;
                }
                return write(out, optional.get(), gap, indent);
            }
            case Boolean b -> out.append(b);
            case Double d -> number(out, d);
            case Float f -> number(out, f.doubleValue());
            case Number n -> out.append(NumberText.of(n));
            case CharSequence text -> quote(out, text.toString());
            case Character ch -> quote(out, ch.toString());
            case Instant instant -> quote(out, IsoTime.format(instant));
            case Map<?, ?> map -> object(out, map, gap, indent);
            case Iterable<?> items -> array(out, items.iterator(), gap, indent);
            case Object[] items -> array(out, List.of(items).iterator(), gap, indent);
            case double[] items -> array(out, boxed(items), gap, indent);
            case int[] items -> array(out, boxed(items), gap, indent);
            case long[] items -> array(out, boxed(items), gap, indent);
            default ->
                throw new IllegalArgumentException(
                        "cannot write a " + plain.getClass().getName() + " as json");
        }
        return true;
    }

    private static void number(StringBuilder out, double value) {
        out.append(Double.isFinite(value) ? NumberText.of(value) : "null");
    }

    private static void object(StringBuilder out, Map<?, ?> map, String gap, String indent) {
        String inner = indent + gap;
        int start = out.length();
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            int mark = out.length();
            if (!first) {
                out.append(',');
            }
            if (!gap.isEmpty()) {
                out.append('\n').append(inner);
            }
            quote(out, String.valueOf(entry.getKey()));
            out.append(gap.isEmpty() ? ":" : ": ");
            if (write(out, entry.getValue(), gap, inner)) {
                first = false;
            } else {
                out.setLength(mark);
            }
        }
        if (first) {
            out.setLength(start);
            out.append("{}");
            return;
        }
        if (!gap.isEmpty()) {
            out.append('\n').append(indent);
        }
        out.append('}');
    }

    private static void array(StringBuilder out, Iterator<?> items, String gap, String indent) {
        if (!items.hasNext()) {
            out.append("[]");
            return;
        }
        String inner = indent + gap;
        out.append('[');
        boolean first = true;
        while (items.hasNext()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            if (!gap.isEmpty()) {
                out.append('\n').append(inner);
            }
            if (!write(out, items.next(), gap, inner)) {
                out.append("null");
            }
        }
        if (!gap.isEmpty()) {
            out.append('\n').append(indent);
        }
        out.append(']');
    }

    private static Iterator<Object> boxed(double[] values) {
        List<Object> list = new ArrayList<>(values.length);
        for (double value : values) {
            list.add(value);
        }
        return list.iterator();
    }

    private static Iterator<Object> boxed(int[] values) {
        List<Object> list = new ArrayList<>(values.length);
        for (int value : values) {
            list.add(value);
        }
        return list.iterator();
    }

    private static Iterator<Object> boxed(long[] values) {
        List<Object> list = new ArrayList<>(values.length);
        for (long value : values) {
            list.add(value);
        }
        return list.iterator();
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i += 1) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20 || isLoneSurrogate(text, i)) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }

    private static boolean isLoneSurrogate(String text, int at) {
        char ch = text.charAt(at);
        if (Character.isHighSurrogate(ch)) {
            return at + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(at + 1));
        }
        if (Character.isLowSurrogate(ch)) {
            return at == 0 || !Character.isHighSurrogate(text.charAt(at - 1));
        }
        return false;
    }

    /** A strict recursive descent reader: no comments, no trailing commas, nothing after the value. */
    private static final class Reader {
        private final String text;
        private int at;

        Reader(String text) {
            this.text = text;
        }

        Object document() {
            skipSpace();
            Object value = value();
            skipSpace();
            if (at < text.length()) {
                throw unexpected();
            }
            return value;
        }

        private Object value() {
            if (at >= text.length()) {
                throw new JsonSyntaxException("unexpected end of json input", at);
            }
            char ch = text.charAt(at);
            return switch (ch) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (ch == '-' || (ch >= '0' && ch <= '9')) {
                        yield number();
                    }
                    throw unexpected();
                }
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            at += 1;
            skipSpace();
            if (peek() == '}') {
                at += 1;
                return map;
            }
            while (true) {
                skipSpace();
                if (peek() != '"') {
                    throw unexpected();
                }
                String key = string();
                skipSpace();
                expect(':');
                skipSpace();
                map.put(key, value());
                skipSpace();
                char next = peek();
                at += 1;
                if (next == '}') {
                    return map;
                }
                if (next != ',') {
                    at -= 1;
                    throw unexpected();
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            at += 1;
            skipSpace();
            if (peek() == ']') {
                at += 1;
                return list;
            }
            while (true) {
                skipSpace();
                list.add(value());
                skipSpace();
                char next = peek();
                at += 1;
                if (next == ']') {
                    return list;
                }
                if (next != ',') {
                    at -= 1;
                    throw unexpected();
                }
            }
        }

        private String string() {
            at += 1;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (at >= text.length()) {
                    throw new JsonSyntaxException("unterminated string in json", at);
                }
                char ch = text.charAt(at);
                at += 1;
                if (ch == '"') {
                    return out.toString();
                }
                if (ch < 0x20) {
                    at -= 1;
                    throw new JsonSyntaxException("bad control character in string literal in json", at);
                }
                if (ch != '\\') {
                    out.append(ch);
                    continue;
                }
                if (at >= text.length()) {
                    throw new JsonSyntaxException("unterminated string in json", at);
                }
                char escape = text.charAt(at);
                at += 1;
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(unicode());
                    default -> {
                        at -= 1;
                        throw new JsonSyntaxException("bad escaped character in json", at);
                    }
                }
            }
        }

        private char unicode() {
            if (at + 4 > text.length()) {
                throw new JsonSyntaxException("bad unicode escape in json", at);
            }
            int code = 0;
            for (int i = 0; i < 4; i += 1) {
                int digit = Character.digit(text.charAt(at + i), 16);
                if (digit < 0) {
                    throw new JsonSyntaxException("bad unicode escape in json", at);
                }
                code = code * 16 + digit;
            }
            at += 4;
            return (char) code;
        }

        private Double number() {
            int start = at;
            if (peek() == '-') {
                at += 1;
            }
            if (peek() == '0') {
                at += 1;
            } else if (isDigit(peek())) {
                digits();
            } else {
                throw unexpected();
            }
            if (peek() == '.') {
                at += 1;
                if (!isDigit(peek())) {
                    throw unexpected();
                }
                digits();
            }
            if (peek() == 'e' || peek() == 'E') {
                at += 1;
                if (peek() == '+' || peek() == '-') {
                    at += 1;
                }
                if (!isDigit(peek())) {
                    throw unexpected();
                }
                digits();
            }
            return Double.valueOf(text.substring(start, at));
        }

        private void digits() {
            while (isDigit(peek())) {
                at += 1;
            }
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, at)) {
                throw unexpected();
            }
            at += word.length();
            return value;
        }

        private void expect(char ch) {
            if (peek() != ch) {
                throw unexpected();
            }
            at += 1;
        }

        private char peek() {
            return at < text.length() ? text.charAt(at) : '\0';
        }

        private void skipSpace() {
            while (at < text.length()) {
                char ch = text.charAt(at);
                if (ch != ' ' && ch != '\t' && ch != '\n' && ch != '\r') {
                    return;
                }
                at += 1;
            }
        }

        private JsonSyntaxException unexpected() {
            if (at >= text.length()) {
                return new JsonSyntaxException("unexpected end of json input", at);
            }
            return new JsonSyntaxException("unexpected token '" + text.charAt(at) + "' in json", at);
        }

        private static boolean isDigit(char ch) {
            return ch >= '0' && ch <= '9';
        }
    }
}
