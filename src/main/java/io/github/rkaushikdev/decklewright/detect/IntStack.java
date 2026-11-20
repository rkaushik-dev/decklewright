package io.github.rkaushikdev.decklewright.detect;

import java.util.Arrays;

/**
 * A growable stack of ints for the flood fills.
 *
 * <p>A fill over a sheet visits millions of pixels, and a stack of boxed integers would allocate
 * an object for every coordinate it pushes. The fills push coordinates in pairs and pop them in
 * reverse, last pushed first, so a fill always visits pixels in the same order.
 */
final class IntStack {
    private static final int INITIAL_CAPACITY = 64;

    private int[] items = new int[INITIAL_CAPACITY];
    private int size;

    /** Pushes {@code first} then {@code second}, so {@code second} comes off first. */
    void push(int first, int second) {
        if (size + 2 > items.length) {
            items = Arrays.copyOf(items, Math.max(items.length * 2, size + 2));
        }
        items[size] = first;
        items[size + 1] = second;
        size += 2;
    }

    /** The value on top, removed. */
    int pop() {
        size -= 1;
        return items[size];
    }

    /** True when nothing is left to pop. */
    boolean isEmpty() {
        return size == 0;
    }
}
