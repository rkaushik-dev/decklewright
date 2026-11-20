package io.github.rkaushikdev.decklewright.measure;

import java.util.Locale;

/**
 * The blank space around a figure a curator typed.
 *
 * <p>Lengths and tolerances are typed into profiles or pasted from work orders and spreadsheets,
 * which bring non-breaking and other Unicode spaces with them. The readers skip exactly the
 * characters ECMAScript counts as white space or a line terminator, which is the set the profiles
 * have always been read with. {@link String#trim()}, {@link String#strip()} and the regex {@code \s}
 * each skip a different set, so nothing here uses them.
 */
final class Whitespace {
    /**
     * Every blank character, once each: tab, line feed, vertical tab, form feed, carriage return and
     * space, then the no-break space, the ogham space mark, the en quad through the hair space, the
     * line and paragraph separators, the narrow no-break space, the medium mathematical space, the
     * ideographic space and the byte order mark.
     */
    private static final char[] BLANKS = {
        '\t', '\n', 0x0B, '\f', '\r', ' ', 0xA0, 0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007,
        0x2008, 0x2009, 0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF
    };

    /** A regular expression character class matching one blank character. */
    static final String CLASS = characterClass();

    private Whitespace() {}

    /** True when the character is blank space. */
    static boolean isBlank(char c) {
        for (char blank : BLANKS) {
            if (blank == c) {
                return true;
            }
        }
        return false;
    }

    /** The text without the blank space at either end. */
    static String trim(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isBlank(text.charAt(start))) {
            start += 1;
        }
        while (end > start && isBlank(text.charAt(end - 1))) {
            end -= 1;
        }
        return text.substring(start, end);
    }

    private static String characterClass() {
        StringBuilder out = new StringBuilder("[");
        for (char blank : BLANKS) {
            out.append(String.format(Locale.ROOT, "\\u%04X", (int) blank));
        }
        return out.append(']').toString();
    }
}
