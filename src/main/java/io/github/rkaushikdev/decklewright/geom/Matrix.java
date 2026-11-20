package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.GeometryError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A two by three affine transform laid out the way every drawing library does it, so a matrix can
 * be handed straight to anything that speaks that form.
 *
 * <pre>
 *     x' = a*x + c*y + e
 *     y' = b*x + d*y + f
 * </pre>
 *
 * <p>The lab only ever needs translate, scale, rotate and the odd flip, but it needs them composed,
 * because a deskew is a rotation about the sheet centre followed by a crop translation followed by
 * a scale to the service size. {@link #equals(Object)} is exact; {@link #approxEquals(Matrix,
 * double)} compares component by component within a tolerance.
 *
 * @param a the x scale and rotation term applied to x
 * @param b the y shear and rotation term applied to x
 * @param c the x shear and rotation term applied to y
 * @param d the y scale and rotation term applied to y
 * @param e the x translation
 * @param f the y translation
 */
public record Matrix(double a, double b, double c, double d, double e, double f) implements JsonValue {

    /**
     * A matrix of the given components.
     *
     * @throws BadArgumentError when a component is not finite
     */
    public Matrix {
        Guards.requireFinite(a, "a");
        Guards.requireFinite(b, "b");
        Guards.requireFinite(c, "c");
        Guards.requireFinite(d, "d");
        Guards.requireFinite(e, "e");
        Guards.requireFinite(f, "f");
    }

    /** The transform that changes nothing. */
    public static Matrix identity() {
        return new Matrix(1, 0, 0, 1, 0, 0);
    }

    /** A move by the given amounts. */
    public static Matrix translation(double dx, double dy) {
        return new Matrix(1, 0, 0, 1, dx, dy);
    }

    /** The same scale on both axes, about the origin. */
    public static Matrix scaling(double sx) {
        return scaling(sx, sx);
    }

    /** A scale per axis, about the origin. */
    public static Matrix scaling(double sx, double sy) {
        return new Matrix(sx, 0, 0, sy, 0, 0);
    }

    /** A rotation about the origin, clockwise on screen for a positive angle. */
    public static Matrix rotation(Angle angle) {
        double cos = StrictMath.cos(angle.radians());
        double sin = StrictMath.sin(angle.radians());
        return new Matrix(cos, sin, -sin, cos, 0, 0);
    }

    /** A rotation about the given point, clockwise on screen for a positive angle. */
    public static Matrix rotation(Angle angle, Point about) {
        return translation(-about.x(), -about.y()).then(rotation(angle)).then(translation(about.x(), about.y()));
    }

    /** A mirror across the vertical axis, the horizontal one, both or neither. */
    public static Matrix flip(boolean horizontal, boolean vertical) {
        return new Matrix(horizontal ? -1 : 1, 0, 0, vertical ? -1 : 1, 0, 0);
    }

    /**
     * The matrices composed in order, each applied after the one before it; the identity for none.
     */
    public static Matrix compose(List<Matrix> matrices) {
        Matrix acc = identity();
        for (Matrix m : matrices) {
            acc = acc.then(m);
        }
        return acc;
    }

    /**
     * Composes so that the argument is applied after this one. Reading a chain left to right then
     * matches the order the operations happen in.
     */
    public Matrix then(Matrix next) {
        return new Matrix(
                next.a * a + next.c * b,
                next.b * a + next.d * b,
                next.a * c + next.c * d,
                next.b * c + next.d * d,
                next.a * e + next.c * f + next.e,
                next.b * e + next.d * f + next.f);
    }

    /** Where the transform takes the point. */
    public Point apply(Point point) {
        return new Point(a * point.x() + c * point.y() + e, b * point.x() + d * point.y() + f);
    }

    /**
     * The axis aligned box that holds the transformed corners. A rotation makes this larger than
     * the source, which is exactly the growth a deskew has to budget for.
     */
    public Rect applyToRect(Rect rect) {
        return Rect.bounding(rect.corners().stream().map(this::apply).toList());
    }

    /** How the transform scales area; zero or near it means the plane collapses. */
    public double determinant() {
        return a * d - b * c;
    }

    /** True when every component is within a billionth of the identity's. */
    public boolean isIdentity() {
        return approxEquals(identity());
    }

    /** True when the transform can be undone. */
    public boolean isInvertible() {
        return Math.abs(determinant()) > 1e-12;
    }

    /**
     * The transform that undoes this one.
     *
     * @throws GeometryError when the transform collapses the plane
     */
    public Matrix inverse() {
        double det = determinant();
        if (Math.abs(det) <= 1e-12) {
            throw new GeometryError(
                    "this transform collapses the plane and cannot be undone", Map.of("determinant", det));
        }
        double ia = d / det;
        double ib = -b / det;
        double ic = -c / det;
        double id = a / det;
        return new Matrix(ia, ib, ic, id, -(ia * e + ic * f), -(ib * e + id * f));
    }

    /**
     * Pulls apart into the pieces an operator understands. Shear is reported but nothing in the tree
     * produces one, so a non zero shear means a matrix came from somewhere unexpected.
     */
    public Decomposition decompose() {
        double scaleX = Num.hypot(a, b);
        Angle rotation = Angle.radians(StrictMath.atan2(b, a));
        double squared = scaleX * scaleX;
        double shear = (a * c + b * d) / (squared == 0 ? 1 : squared);
        double scaleY = Num.hypot(c - shear * a, d - shear * b);
        return new Decomposition(new Point(e, f), rotation, scaleX, scaleY, shear);
    }

    /** True when every component is within a billionth of the other's. */
    public boolean approxEquals(Matrix other) {
        return approxEquals(other, Num.DEFAULT_EPSILON);
    }

    /** True when every component is within the tolerance of the other's; never for null. */
    public boolean approxEquals(Matrix other, double tolerance) {
        return other != null
                && Num.approxEqual(a, other.a, tolerance)
                && Num.approxEqual(b, other.b, tolerance)
                && Num.approxEqual(c, other.c, tolerance)
                && Num.approxEqual(d, other.d, tolerance)
                && Num.approxEqual(e, other.e, tolerance)
                && Num.approxEqual(f, other.f, tolerance);
    }

    /** The six components in the usual order, a through f, in a new array. */
    public double[] toArray() {
        return new double[] {a, b, c, d, e, f};
    }

    /** {@code {a, b, c, d, e, f}}, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("a", a);
        json.put("b", b);
        json.put("c", c);
        json.put("d", d);
        json.put("e", e);
        json.put("f", f);
        return json;
    }

    /** The css form to six places, such as {@code matrix(1, 0, 0, 1, 0, 0)}. */
    @Override
    public String toString() {
        return Arrays.stream(toArray())
                .mapToObj(v -> NumberText.of(Num.roundTo(v, 6)))
                .collect(Collectors.joining(", ", "matrix(", ")"));
    }

    /**
     * A transform taken apart into the pieces an operator understands.
     *
     * @param translation where the origin goes
     * @param rotation the turn of the x axis
     * @param scaleX the length the x axis is stretched to
     * @param scaleY the length the y axis is stretched to, once the shear is taken out
     * @param shear how far the y axis leans; zero for anything the tree builds
     */
    public record Decomposition(Point translation, Angle rotation, double scaleX, double scaleY, double shear) {
        /** A decomposition of the given parts. */
        public Decomposition {
            Objects.requireNonNull(translation, "translation");
            Objects.requireNonNull(rotation, "rotation");
        }
    }
}
