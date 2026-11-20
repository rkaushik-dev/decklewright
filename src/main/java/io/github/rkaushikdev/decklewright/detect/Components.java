package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Point;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Connected components.
 *
 * <p>The mask usually holds the sheet, the colour target, the scale bar and a handful of dust.
 * Telling them apart by size and position is the cheapest reliable way through, and every later
 * stage wants the pieces separately anyway.
 *
 * <p>Labels start at one. Zero means background, which lets the label buffer double as a mask.
 * Diagonal neighbours join unless the caller asks for {@linkplain Connectivity#FOUR four}
 * connectivity.
 */
public final class Components {
    private Components() {}

    /**
     * Labels every blob, joining diagonal neighbours.
     *
     * @see #labelComponents(Mask, Connectivity)
     */
    public static Labelling labelComponents(Mask mask) {
        return labelComponents(mask, Connectivity.EIGHT);
    }

    /**
     * Labels every blob, numbering them from one in the order a row by row scan first meets them,
     * and summarises each. The fill keeps its own stack, so a blob of any size or shape is safe.
     */
    public static Labelling labelComponents(Mask mask, Connectivity connectivity) {
        Objects.requireNonNull(connectivity, "connectivity");
        boolean diagonals = connectivity == Connectivity.EIGHT;
        int width = mask.width();
        int height = mask.height();
        byte[] data = mask.data;
        int[] labels = new int[data.length];
        List<Component> components = new ArrayList<>();
        IntStack stack = new IntStack();
        int nextId = 0;

        for (int startY = 0; startY < height; startY += 1) {
            for (int startX = 0; startX < width; startX += 1) {
                int at = startY * width + startX;
                if (data[at] == 0 || labels[at] != 0) {
                    continue;
                }

                nextId += 1;
                labels[at] = nextId;
                stack.push(startX, startY);

                int area = 0;
                int left = startX;
                int right = startX;
                int top = startY;
                int bottom = startY;
                long sumX = 0;
                long sumY = 0;
                boolean touchesEdge = false;

                while (!stack.isEmpty()) {
                    int y = stack.pop();
                    int x = stack.pop();
                    area += 1;
                    sumX += x;
                    sumY += y;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                        touchesEdge = true;
                    }

                    for (int dy = -1; dy <= 1; dy += 1) {
                        for (int dx = -1; dx <= 1; dx += 1) {
                            if ((dx == 0 && dy == 0) || (!diagonals && dx != 0 && dy != 0)) {
                                continue;
                            }
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                                continue;
                            }
                            int index = ny * width + nx;
                            if (data[index] == 0 || labels[index] != 0) {
                                continue;
                            }
                            labels[index] = nextId;
                            stack.push(nx, ny);
                        }
                    }
                }

                components.add(new Component(
                        nextId,
                        area,
                        Rect.fromEdges(left, top, right + 1, bottom + 1),
                        new Point((double) sumX / area, (double) sumY / area),
                        touchesEdge));
            }
        }

        return new Labelling(labels, components);
    }

    /** A mask set exactly where the labelling carries the id; empty for an id nothing carries. */
    public static Mask maskOfComponent(Mask mask, Labelling labelling, int id) {
        int[] labels = labelling.labels();
        Mask out = new Mask(mask.width(), mask.height());
        int shared = Math.min(labels.length, out.data.length);
        for (int i = 0; i < shared; i += 1) {
            out.data[i] = (byte) (labels[i] == id ? 1 : 0);
        }
        return out;
    }

    /**
     * The biggest blob, joining diagonal neighbours.
     *
     * @see #largestComponent(Mask, Connectivity)
     */
    public static Optional<ComponentMask> largestComponent(Mask mask) {
        return largestComponent(mask, Connectivity.EIGHT);
    }

    /**
     * The biggest blob on its own, with its summary; the first one met wins a tie.
     *
     * @return empty when nothing in the mask is set
     */
    public static Optional<ComponentMask> largestComponent(Mask mask, Connectivity connectivity) {
        Labelling labelled = labelComponents(mask, connectivity);
        List<Component> components = labelled.components();
        if (components.isEmpty()) {
            return Optional.empty();
        }
        Component biggest = components.getFirst();
        for (Component item : components) {
            if (item.area() > biggest.area()) {
                biggest = item;
            }
        }
        return Optional.of(new ComponentMask(maskOfComponent(mask, labelled, biggest.id()), biggest));
    }

    /**
     * The largest few blobs, joining diagonal neighbours.
     *
     * @see #keepLargest(Mask, int, Connectivity)
     */
    public static Mask keepLargest(Mask mask, int count) {
        return keepLargest(mask, count, Connectivity.EIGHT);
    }

    /**
     * Keeps the largest few blobs and drops the rest, which is how the dust comes off without an
     * area threshold anybody has to maintain. Blobs of equal area keep their label order.
     *
     * @param count how many blobs to keep
     * @throws BadArgumentError when the count is not a whole number above zero
     */
    public static Mask keepLargest(Mask mask, int count, Connectivity connectivity) {
        Guards.requirePositiveInteger(count, "count");
        Labelling labelled = labelComponents(mask, connectivity);
        List<Component> bySize = new ArrayList<>(labelled.components());
        bySize.sort(Comparator.comparingInt(Component::area).reversed());
        boolean[] keep = new boolean[bySize.size() + 1];
        for (Component item : bySize.subList(0, Math.min(count, bySize.size()))) {
            keep[item.id()] = true;
        }
        return keeping(mask, labelled, keep);
    }

    /**
     * Drops every blob smaller than the area, joining diagonal neighbours.
     *
     * @see #dropSmaller(Mask, double, Connectivity)
     */
    public static Mask dropSmaller(Mask mask, double minimumArea) {
        return dropSmaller(mask, minimumArea, Connectivity.EIGHT);
    }

    /**
     * Drops every blob smaller than the area and keeps the rest.
     *
     * @param minimumArea in pixels; a blob of exactly this area is kept
     */
    public static Mask dropSmaller(Mask mask, double minimumArea, Connectivity connectivity) {
        Labelling labelled = labelComponents(mask, connectivity);
        List<Component> components = labelled.components();
        boolean[] keep = new boolean[components.size() + 1];
        for (Component item : components) {
            keep[item.id()] = item.area() >= minimumArea;
        }
        return keeping(mask, labelled, keep);
    }

    /**
     * How well a component fills its own bounding box. A sheet fills nearly all of it even when
     * skewed. A scale bar does not, and neither does a shadow.
     *
     * @return between zero and one; zero for a component with no bounds
     */
    public static double fillRatio(Component component) {
        double area = component.bounds().area();
        return area == 0 ? 0 : component.area() / area;
    }

    /** A mask of the pixels whose label is marked to keep; background, label zero, never is. */
    private static Mask keeping(Mask mask, Labelling labelled, boolean[] keep) {
        int[] labels = labelled.labels();
        Mask out = new Mask(mask.width(), mask.height());
        for (int i = 0; i < labels.length; i += 1) {
            out.data[i] = (byte) (keep[labels[i]] ? 1 : 0);
        }
        return out;
    }
}
