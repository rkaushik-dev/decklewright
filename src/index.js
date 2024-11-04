/**
 * decklewright, the crop and derivative production library for the oversize
 * reformatting lab. Everything the rest of the shop is allowed to import is
 * re-exported from here.
 */

export {
  BadArgumentError,
  CodecError,
  DeckleError,
  DetectionError,
  ERROR_CODES,
  GeometryError,
  PipelineError,
  PlanError,
  ProfileError,
  QcError,
  StoreError,
  UnsupportedError,
  isDeckleError,
  wrapError,
} from './errors.js';

/** Version of the public surface, bumped by hand when something breaks. */
export const API_VERSION = 1;
