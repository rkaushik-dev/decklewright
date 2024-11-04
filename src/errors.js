/**
 * Error taxonomy for the lab tooling.
 *
 * Everything thrown on purpose carries a stable `code`. Operators read the code
 * off a batch report and look it up in the runbook, so codes are part of the
 * contract and never renamed once a release has shipped with them.
 */

/** Codes that may appear on a DeckleError. */
export const ERROR_CODES = Object.freeze({
  ASSERTION: 'DW_ASSERTION',
  BAD_ARGUMENT: 'DW_BAD_ARGUMENT',
  CODEC: 'DW_CODEC',
  DETECTION: 'DW_DETECTION',
  GEOMETRY: 'DW_GEOMETRY',
  PIPELINE: 'DW_PIPELINE',
  PLAN: 'DW_PLAN',
  PROFILE: 'DW_PROFILE',
  QC: 'DW_QC',
  STORE: 'DW_STORE',
  UNSUPPORTED: 'DW_UNSUPPORTED',
});

/**
 * Base class for every deliberate failure in the tree.
 */
export class DeckleError extends Error {
  /**
   * @param {string} message
   * @param {object} [options]
   * @param {string} [options.code]
   * @param {Record<string, unknown>} [options.details]
   * @param {unknown} [options.cause]
   */
  constructor(message, options = {}) {
    super(message);
    this.name = new.target.name;
    this.code = options.code ?? ERROR_CODES.ASSERTION;
    this.details = options.details ? { ...options.details } : {};
    if (options.cause !== undefined) this.cause = options.cause;
    if (Error.captureStackTrace) Error.captureStackTrace(this, new.target);
  }

  /**
   * Attach more context without losing the original stack. Used when an error
   * crosses a layer boundary and the outer layer knows which file it was.
   *
   * @param {Record<string, unknown>} extra
   * @returns {this}
   */
  annotate(extra) {
    Object.assign(this.details, extra);
    return this;
  }

  /** @returns {{name: string, code: string, message: string, details: Record<string, unknown>}} */
  toJSON() {
    return {
      name: this.name,
      code: this.code,
      message: this.message,
      details: { ...this.details },
    };
  }
}

/** A value handed to a public function does not meet its stated contract. */
export class BadArgumentError extends DeckleError {
  constructor(message, details) {
    super(message, { code: ERROR_CODES.BAD_ARGUMENT, details });
  }
}

/** A file could not be read or written as the format it claims to be. */
export class CodecError extends DeckleError {
  constructor(message, details) {
    super(message, { code: ERROR_CODES.CODEC, details });
  }
}

/** Geometry that cannot exist, such as a rect with negative extent. */
export class GeometryError extends DeckleError {
  constructor(message, details) {
    super(message, { code: ERROR_CODES.GEOMETRY, details });
  }
}

/** The capture does not contain a findable object. */
export class DetectionError extends DeckleError {
  constructor(message, details) {
    super(message, { code: ERROR_CODES.DETECTION, details });
  }
}

/** A collection profile is malformed or contradicts itself. */
export class ProfileError extends DeckleError {
  constructor(message, details) {
    super(message, { code: ERROR_CODES.PROFILE, details });
  }
}

/** No crop satisfies the rules the profile asked for. */
export class PlanError extends DeckleError {
  constructor(message, details) {
    super(message, { code: ERROR_CODES.PLAN, details });
  }
}

/** A quality check could not be evaluated at all, as opposed to failing. */
export class QcError extends DeckleError {
  constructor(message, details) {
    super(message, { code: ERROR_CODES.QC, details });
  }
}

/** A stage refused to run, or the graph itself is wrong. */
export class PipelineError extends DeckleError {
  constructor(message, details) {
    super(message, { code: ERROR_CODES.PIPELINE, details });
  }
}

/** The workspace on disk is not in the shape the run expects. */
export class StoreError extends DeckleError {
  constructor(message, details) {
    super(message, { code: ERROR_CODES.STORE, details });
  }
}

/** Something real but not implemented for this input. */
export class UnsupportedError extends DeckleError {
  constructor(message, details) {
    super(message, { code: ERROR_CODES.UNSUPPORTED, details });
  }
}

/**
 * @param {unknown} value
 * @returns {value is DeckleError}
 */
export function isDeckleError(value) {
  return value instanceof DeckleError;
}

/**
 * Wrap a foreign error so it carries a code without hiding what threw it.
 *
 * @param {unknown} cause
 * @param {string} message
 * @param {string} [code]
 * @returns {DeckleError}
 */
export function wrapError(cause, message, code = ERROR_CODES.ASSERTION) {
  if (isDeckleError(cause)) return cause.annotate({ context: message });
  const text = cause instanceof Error ? cause.message : String(cause);
  return new DeckleError(`${message}: ${text}`, { code, cause });
}
