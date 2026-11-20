package io.github.rkaushikdev.decklewright.detect;

import java.util.ArrayList;
import java.util.List;

/** Masks drawn as rows of text, with {@code #} for set and anything else for unset, and back. */
final class MaskDrawing {
    private MaskDrawing() {}

    /** The mask the rows draw; every row must be the same length. */
    static Mask maskOf(String... rows) {
        Mask mask = new Mask(rows[0].length(), rows.length);
        for (int y = 0; y < rows.length; y += 1) {
            for (int x = 0; x < rows[y].length(); x += 1) {
                mask.set(x, y, rows[y].charAt(x) == '#' ? 1 : 0);
            }
        }
        return mask;
    }

    /** The mask drawn back as rows, with {@code #} for set and {@code .} for unset. */
    static List<String> draw(Mask mask) {
        List<String> out = new ArrayList<>();
        for (int y = 0; y < mask.height(); y += 1) {
            StringBuilder line = new StringBuilder();
            for (int value : mask.row(y).toArray()) {
                line.append(value != 0 ? '#' : '.');
            }
            out.add(line.toString());
        }
        return out;
    }
}
