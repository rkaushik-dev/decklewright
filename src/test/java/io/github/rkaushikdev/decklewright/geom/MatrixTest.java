package io.github.rkaushikdev.decklewright.geom;

import static io.github.rkaushikdev.decklewright.geom.Matrix.compose;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.GeometryError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MatrixTest {

    @Nested
    class TheBasicTransforms {
        @Test
        void hasAnIdentityThatChangesNothing() {
            Matrix m = Matrix.identity();
            assertThat(m.isIdentity()).isTrue();
            assertThat(m.apply(Point.of(3, 4)).toJson()).isEqualTo(Map.of("x", 3.0, "y", 4.0));
            // A record's components are final and these are primitives, so nothing can change them.
            assertThat(Matrix.class.isRecord()).isTrue();
        }

        @Test
        void translates() {
            assertThat(Matrix.translation(10, -5).apply(Point.of(1, 1)).toJson())
                    .isEqualTo(Map.of("x", 11.0, "y", -4.0));
        }

        @Test
        void scalesUniformlyAndPerAxis() {
            assertThat(Matrix.scaling(2).apply(Point.of(3, 4)).toJson()).isEqualTo(Map.of("x", 6.0, "y", 8.0));
            assertThat(Matrix.scaling(2, 0.5).apply(Point.of(3, 4)).toJson()).isEqualTo(Map.of("x", 6.0, "y", 2.0));
        }

        @Test
        void rotatesClockwiseAboutTheOrigin() {
            Point p = Matrix.rotation(Angle.degrees(90)).apply(Point.of(1, 0));
            assertThat(p.x()).isCloseTo(0, byLessThan(5e-13));
            assertThat(p.y()).isCloseTo(1, byLessThan(5e-13));
        }

        @Test
        void rotatesAboutAGivenCentre() {
            Point centre = Point.of(10, 10);
            Matrix m = Matrix.rotation(Angle.degrees(180), centre);
            assertThat(m.apply(centre).approxEquals(centre)).isTrue();
            assertThat(m.apply(Point.of(11, 10)).approxEquals(Point.of(9, 10), 1e-9))
                    .isTrue();
        }

        @Test
        void flipsOnEitherAxis() {
            assertThat(Matrix.flip(true, false).apply(Point.of(3, 4)).toJson()).isEqualTo(Map.of("x", -3.0, "y", 4.0));
            assertThat(Matrix.flip(false, true).apply(Point.of(3, 4)).toJson()).isEqualTo(Map.of("x", 3.0, "y", -4.0));
            assertThat(Matrix.flip(false, false).isIdentity()).isTrue();
        }

        @Test
        void refusesAComponentThatIsNotFinite() {
            assertThatThrownBy(() -> new Matrix(Double.NaN, 0, 0, 1, 0, 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("a must be a finite number");
        }
    }

    @Nested
    class Composition {
        @Test
        void appliesTheArgumentAfterTheReceiver() {
            Matrix m = Matrix.translation(10, 0).then(Matrix.scaling(2));
            assertThat(m.apply(Point.of(1, 0)).toJson()).isEqualTo(Map.of("x", 22.0, "y", 0.0));
        }

        @Test
        void isNotCommutative() {
            Matrix a = Matrix.translation(10, 0).then(Matrix.scaling(2));
            Matrix b = Matrix.scaling(2).then(Matrix.translation(10, 0));
            assertThat(a.approxEquals(b)).isFalse();
            assertThat(b.apply(Point.of(1, 0)).toJson()).isEqualTo(Map.of("x", 12.0, "y", 0.0));
        }

        @Test
        void foldsAListLeftToRight() {
            Matrix folded = compose(List.of(Matrix.translation(1, 1), Matrix.scaling(2), Matrix.translation(3, 3)));
            assertThat(folded.apply(Point.of(0, 0)).toJson()).isEqualTo(Map.of("x", 5.0, "y", 5.0));
            assertThat(compose(List.of()).isIdentity()).isTrue();
        }

        @Test
        void keepsIdentityAsTheNeutralElement() {
            Matrix m = Matrix.rotation(Angle.degrees(17));
            assertThat(m.then(Matrix.identity()).approxEquals(m)).isTrue();
            assertThat(Matrix.identity().then(m).approxEquals(m)).isTrue();
        }
    }

    @Nested
    class ApplyToRect {
        @Test
        void leavesAnAxisAlignedRectAloneUnderTranslation() {
            Rect r = Rect.of(0, 0, 10, 20);
            assertThat(Matrix.translation(5, 5).applyToRect(r).toJson())
                    .isEqualTo(Map.of("x", 5.0, "y", 5.0, "width", 10.0, "height", 20.0));
        }

        @Test
        void growsTheBoxWhenTheRectIsRotated() {
            Rect r = Rect.of(0, 0, 10, 10);
            Rect box = Matrix.rotation(Angle.degrees(45), r.centre()).applyToRect(r);
            assertThat(box.width()).isCloseTo(Math.sqrt(2) * 10, byLessThan(5e-10));
            assertThat(box.centre().approxEquals(r.centre(), 1e-9)).isTrue();
        }

        @Test
        void swapsTheExtentsOnAQuarterTurn() {
            Rect r = Rect.of(0, 0, 10, 20);
            Rect box = Matrix.rotation(Angle.degrees(90), r.centre()).applyToRect(r);
            assertThat(box.width()).isCloseTo(20, byLessThan(5e-10));
            assertThat(box.height()).isCloseTo(10, byLessThan(5e-10));
        }
    }

    @Nested
    class Inverse {
        @Test
        void undoesAComposedTransform() {
            Matrix m = Matrix.rotation(Angle.degrees(13), Point.of(4, 4)).then(Matrix.scaling(3, 2));
            Matrix back = m.then(m.inverse());
            assertThat(back.approxEquals(Matrix.identity(), 1e-9)).isTrue();
        }

        @Test
        void bringsAPointHomeAgain() {
            Matrix m = Matrix.translation(100, 50).then(Matrix.rotation(Angle.degrees(30)));
            Point p = Point.of(7, 9);
            assertThat(m.inverse().apply(m.apply(p)).approxEquals(p, 1e-9)).isTrue();
        }

        @Test
        void refusesADegenerateTransform() {
            Matrix flat = Matrix.scaling(1, 0);
            assertThat(flat.isInvertible()).isFalse();
            assertThatThrownBy(flat::inverse)
                    .isInstanceOf(GeometryError.class)
                    .hasMessageFindingMatch("collapses the plane");
        }

        @Test
        void reportsTheDeterminant() {
            assertThat(Matrix.scaling(2, 3).determinant()).isEqualTo(6);
            assertThat(Matrix.rotation(Angle.degrees(37)).determinant()).isCloseTo(1, byLessThan(5e-13));
        }
    }

    @Nested
    class Decompose {
        @Test
        void pullsBackThePiecesThatWentIn() {
            Matrix m = compose(
                    List.of(Matrix.scaling(3, 2), Matrix.rotation(Angle.degrees(20)), Matrix.translation(40, 10)));
            Matrix.Decomposition parts = m.decompose();
            assertThat(parts.translation().toJson()).isEqualTo(Map.of("x", 40.0, "y", 10.0));
            assertThat(parts.rotation().degrees()).isCloseTo(20, byLessThan(5e-10));
            assertThat(parts.scaleX()).isCloseTo(3, byLessThan(5e-10));
            assertThat(parts.scaleY()).isCloseTo(2, byLessThan(5e-10));
            assertThat(parts.shear()).isCloseTo(0, byLessThan(5e-10));
        }

        @Test
        void reportsNoRotationAndNoShearForAPlainScale() {
            Matrix.Decomposition parts = Matrix.scaling(2, 5).decompose();
            assertThat(parts.rotation().degrees()).isCloseTo(0, byLessThan(5e-13));
            assertThat(parts.shear()).isCloseTo(0, byLessThan(5e-13));
        }
    }

    @Nested
    class Serialisation {
        @Test
        void listsTheSixComponentsInTheUsualOrder() {
            assertThat(Matrix.scaling(2, 3).toArray()).containsExactly(2, 0, 0, 3, 0, 0);
            assertThat(Matrix.translation(1, 2).toJson())
                    .isEqualTo(Map.of("a", 1.0, "b", 0.0, "c", 0.0, "d", 1.0, "e", 1.0, "f", 2.0));
            assertThat(String.valueOf(Matrix.identity())).isEqualTo("matrix(1, 0, 0, 1, 0, 0)");
        }

        @Test
        void isNotEqualToABareArray() {
            // approxEquals only takes a matrix, so the bare array is compared through equals instead.
            Object bare = new double[] {1, 0, 0, 1, 0, 0};
            assertThat(Matrix.identity().equals(bare)).isFalse();
        }
    }
}
