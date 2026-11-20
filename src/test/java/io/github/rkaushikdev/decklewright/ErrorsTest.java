package io.github.rkaushikdev.decklewright;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ErrorsTest {

    @Nested
    class DeckleErrorBehaviour {
        @Test
        void carriesADefaultCodeAndAnEmptyDetailBag() {
            DeckleError err = new DeckleError("bed is empty");
            assertThat(err.code()).isEqualTo(ErrorCode.ASSERTION);
            assertThat(err.details()).isEmpty();
            assertThat(err.name()).isEqualTo("DeckleError");
            assertThat(err).isInstanceOf(RuntimeException.class);
        }

        @Test
        void copiesTheDetailsRatherThanAliasingThem() {
            Map<String, Object> details = new HashMap<>(Map.of("sheet", "q17"));
            DeckleError err = new DeckleError("nope", ErrorCode.ASSERTION, details);
            details.put("sheet", "q18");
            assertThat(err.details()).containsEntry("sheet", "q17");
        }

        @Test
        void keepsTheCauseWhenOneIsGiven() {
            Exception cause = new IllegalStateException("EACCES");
            DeckleError err = new DeckleError("cannot read capture", ErrorCode.ASSERTION, Map.of(), cause);
            assertThat(err.getCause()).isSameAs(cause);
        }

        @Test
        void annotatesInPlaceAndReturnsItself() {
            DeckleError err = new DeckleError("bad crop");
            DeckleError same = err.annotate(Map.of("stage", "plan")).annotate(Map.of("job", 7));
            assertThat(same).isSameAs(err);
            assertThat(err.details()).isEqualTo(Map.of("stage", "plan", "job", 7));
        }

        @Test
        void serialisesToSomethingABatchReportCanPrint() {
            BadArgumentError err = new BadArgumentError("width must be positive", Map.of("width", -3));
            assertThat(err.toJson())
                    .isEqualTo(Map.of(
                            "name",
                            "BadArgumentError",
                            "code",
                            ErrorCode.BAD_ARGUMENT.value(),
                            "message",
                            "width must be positive",
                            "details",
                            Map.of("width", -3)));
        }

        @Test
        @SuppressWarnings("unchecked") // the details slot of toJson is always a string keyed map
        void doesNotLeakTheInternalDetailObjectThroughToJson() {
            DeckleError err = new DeckleError("x", ErrorCode.ASSERTION, Map.of("a", 1));
            Map<String, Object> json = err.toJson();
            ((Map<String, Object>) json.get("details")).put("a", 2);
            assertThat(err.details()).containsEntry("a", 1);
        }
    }

    @Nested
    class TheSubclasses {
        static Stream<Arguments> cases() {
            return Stream.of(
                    Arguments.of(BadArgumentError.class, ErrorCode.BAD_ARGUMENT),
                    Arguments.of(CodecError.class, ErrorCode.CODEC),
                    Arguments.of(GeometryError.class, ErrorCode.GEOMETRY),
                    Arguments.of(DetectionError.class, ErrorCode.DETECTION),
                    Arguments.of(ProfileError.class, ErrorCode.PROFILE),
                    Arguments.of(PlanError.class, ErrorCode.PLAN),
                    Arguments.of(QcError.class, ErrorCode.QC),
                    Arguments.of(PipelineError.class, ErrorCode.PIPELINE),
                    Arguments.of(ManifestError.class, ErrorCode.MANIFEST),
                    Arguments.of(StoreError.class, ErrorCode.STORE),
                    Arguments.of(UnsupportedError.class, ErrorCode.UNSUPPORTED));
        }

        @ParameterizedTest(name = "{0} reports its own code and name")
        @MethodSource("cases")
        void reportsItsOwnCodeAndName(Class<? extends DeckleError> type, ErrorCode code) throws Exception {
            Constructor<? extends DeckleError> constructor = type.getConstructor(String.class, Map.class);
            DeckleError err = constructor.newInstance("boom", Map.of("where", "here"));
            assertThat(err.code()).isEqualTo(code);
            assertThat(err.name()).isEqualTo(type.getSimpleName());
            assertThat(err.details()).isEqualTo(Map.of("where", "here"));
            assertThat(DeckleError.isDeckleError(err)).isTrue();
        }

        @Test
        void everyCodeIsDistinct() {
            String[] codes =
                    Arrays.stream(ErrorCode.values()).map(ErrorCode::value).toArray(String[]::new);
            assertThat(new HashSet<>(Arrays.asList(codes))).hasSize(codes.length);
        }

        @Test
        void theCodeTableIsFrozen() {
            assertThat(ErrorCode.class.isEnum()).isTrue();
            ErrorCode[] table = ErrorCode.values();
            table[0] = ErrorCode.STORE;
            assertThat(ErrorCode.values()[0]).isEqualTo(ErrorCode.ASSERTION);
        }
    }

    @Nested
    class IsDeckleError {
        @Test
        void rejectsForeignErrorsAndPlainValues() {
            assertThat(DeckleError.isDeckleError(new IllegalStateException("plain")))
                    .isFalse();
            assertThat(DeckleError.isDeckleError(Map.of("code", "DW_CODEC"))).isFalse();
            assertThat(DeckleError.isDeckleError(null)).isFalse();
            assertThat(DeckleError.isDeckleError("DW_CODEC")).isFalse();
        }
    }

    @Nested
    class WrapError {
        @Test
        void annotatesAnErrorThatIsAlreadyOursInsteadOfNestingIt() {
            CodecError inner = new CodecError("bad png header");
            DeckleError out = DeckleError.wrap(inner, "reading q17.png");
            assertThat(out).isSameAs(inner);
            assertThat(out.code()).isEqualTo(ErrorCode.CODEC);
            assertThat(out.details()).containsEntry("context", "reading q17.png");
        }

        @Test
        void wrapsAForeignErrorAndKeepsItAsTheCause() {
            Exception inner = new IllegalStateException("ENOENT");
            DeckleError out = DeckleError.wrap(inner, "reading q17.png", ErrorCode.STORE);
            assertThat(out).isInstanceOf(DeckleError.class);
            assertThat(out.code()).isEqualTo(ErrorCode.STORE);
            assertThat(out.getMessage()).isEqualTo("reading q17.png: ENOENT");
            assertThat(out.getCause()).isSameAs(inner);
        }

        @Test
        void stringifiesAThrownNonError() {
            DeckleError out = DeckleError.wrap("just a string", "reading q17.png");
            assertThat(out.getMessage()).isEqualTo("reading q17.png: just a string");
            assertThat(out.code()).isEqualTo(ErrorCode.ASSERTION);
        }
    }
}
