package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for every deliberate failure in the tree.
 *
 * <p>Everything thrown on purpose carries a stable {@link ErrorCode} and a bag of details that a
 * batch report can print. The details are copied on the way in, so the caller's map is never
 * aliased, and they can be added to with {@link #annotate(Map)} when an error crosses a layer
 * boundary and the outer layer knows which file it was.
 */
public class DeckleError extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    /** Mutable on purpose: {@link #annotate(Map)} adds to it in place. Values may be null. */
    @SuppressWarnings("serial") // the detail values are plain report data, never serialised
    private final Map<String, Object> details;

    /** An assertion failure with no details. */
    public DeckleError(String message) {
        this(message, ErrorCode.ASSERTION, Map.of(), null);
    }

    /** A failure with its own code and no details. */
    public DeckleError(String message, ErrorCode code) {
        this(message, code, Map.of(), null);
    }

    /** A failure with its own code and a copy of the given details. */
    public DeckleError(String message, ErrorCode code, Map<String, ?> details) {
        this(message, code, details, null);
    }

    /**
     * The full form.
     *
     * @param message what went wrong, in words an operator can act on
     * @param code the runbook code; {@link ErrorCode#ASSERTION} when null
     * @param details context for the report; copied, never aliased; empty when null
     * @param cause the foreign error that started this, or null
     */
    public DeckleError(String message, ErrorCode code, Map<String, ?> details, Throwable cause) {
        super(message, cause);
        this.code = code == null ? ErrorCode.ASSERTION : code;
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }

    /** The runbook code this error carries. */
    public ErrorCode code() {
        return code;
    }

    /** The error's class name, which is what a report prints beside the code. */
    public String name() {
        return getClass().getSimpleName();
    }

    /** A read only, live view of the details, including anything added by {@link #annotate(Map)}. */
    public Map<String, Object> details() {
        return Collections.unmodifiableMap(details);
    }

    /**
     * Attach more context without losing the stack trace of where this error was thrown.
     *
     * @param extra keys to add or overwrite
     * @return this same error, so the call can be thrown directly
     */
    public DeckleError annotate(Map<String, ?> extra) {
        details.putAll(Objects.requireNonNull(extra, "extra"));
        return this;
    }

    /**
     * Something a batch report can print: the name, the code, the message and a copy of the
     * details. Changing the returned map never reaches back into this error.
     */
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", name());
        json.put("code", code.value());
        json.put("message", getMessage());
        json.put("details", new LinkedHashMap<>(details));
        return json;
    }

    /** True when the value is one of ours, as opposed to a foreign exception or anything else. */
    public static boolean isDeckleError(Object value) {
        return value instanceof DeckleError;
    }

    /**
     * Wrap a foreign failure so it carries a code without hiding what threw it, using {@link
     * ErrorCode#ASSERTION}.
     *
     * @see #wrap(Object, String, ErrorCode)
     */
    public static DeckleError wrap(Object cause, String message) {
        return wrap(cause, message, ErrorCode.ASSERTION);
    }

    /**
     * Wrap a foreign failure so it carries a code without hiding what threw it.
     *
     * <p>An error that is already ours is annotated with the message as its {@code context} and
     * handed back as it is, rather than nested. Anything else becomes a new error whose message is
     * the given one followed by the cause's own message, and whose cause is the caught value when
     * it is a {@link Throwable}.
     *
     * @param cause what was caught; usually a {@link Throwable}, but any value is described
     * @param message what was being attempted, such as {@code reading q17.png}
     * @param code the code to give a foreign failure
     */
    public static DeckleError wrap(Object cause, String message, ErrorCode code) {
        if (cause instanceof DeckleError ours) {
            return ours.annotate(Map.of("context", message));
        }
        if (cause instanceof Throwable thrown) {
            String text = Objects.toString(thrown.getMessage(), "");
            return new DeckleError(message + ": " + text, code, Map.of(), thrown);
        }
        return new DeckleError(message + ": " + cause, code, Map.of(), null);
    }
}
