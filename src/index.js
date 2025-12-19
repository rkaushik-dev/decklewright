/**
 * decklewright, the crop and derivative production library for the oversize
 * reformatting lab. Everything the rest of the shop is allowed to import is
 * re-exported from here.
 *
 * The surface is deliberately narrow. Nine tenths of what is in src is worth
 * exporting from its own layer's barrel and is not worth promising to keep,
 * and a shop that reaches past this file is welcome to but is on its own when
 * a name moves. What is here is what a caller needs to take a capture and end
 * up with a delivery, plus the errors it will have to catch.
 */

export {
  BadArgumentError,
  CodecError,
  DeckleError,
  DetectionError,
  ERROR_CODES,
  GeometryError,
  ManifestError,
  PipelineError,
  PlanError,
  ProfileError,
  QcError,
  StoreError,
  UnsupportedError,
  isDeckleError,
  wrapError,
} from './errors.js';

export { Angle } from './geom/angle.js';
export { Insets } from './geom/insets.js';
export { Point } from './geom/point.js';
export { Rect } from './geom/rect.js';
export { Size } from './geom/size.js';

export { Length } from './measure/length.js';
export { Sampling } from './measure/resolution.js';
export { Tolerance } from './measure/tolerance.js';

export { Surface } from './raster/surface.js';
export { SurfaceView } from './raster/view.js';
export { readImage, writeImage, probeImage, formatOf } from './codec/registry.js';
export { inspect, describe as describeInspection } from './codec/inspect.js';

export { readCapture, isUsable, summarise as summariseCapture } from './detect/capture.js';

export { ProfileRegistry, registryWith } from './profile/registry.js';
export { BUILT_IN, builtInNamed, builtInNames } from './profile/builtin.js';
export { loadAll, loadProfile } from './profile/index.js';
export { settingsFor } from './profile/settings.js';
export { describeProfile } from './profile/diagnostics.js';

export { CropPlan } from './plan/plan.js';
export { planCrop } from './plan/crop.js';
export { explain, summarise as summarisePlan } from './plan/explain.js';

export { deriveFromPlan, preservationCopy } from './derive/index.js';
export { frequencyOf, textOf } from './derive/recipe.js';
export { nameFor, namesFor } from './derive/naming.js';

export { gradeCapture, reportOn, gradingToJSON, runQc, standardChecks } from './qc/index.js';

export { deliveryFor, describePayload, objectionsToPayload } from './manifest/index.js';
export { digest, digests, fixityOf, verifyAgainst } from './manifest/checksum.js';
export { parseSidecar, writeSidecar } from './manifest/sidecar.js';

export { Workspace, deliver, recordFailure, shiftReport } from './store/index.js';

export { process, processIncoming } from './pipeline/index.js';
export { policy, policyFor, decide } from './pipeline/policy.js';
export { runJob, runBatch, describeRun, standardStages } from './pipeline/runner.js';
export { stage } from './pipeline/stage.js';

export { main as cli } from './cli/main.js';

/** Version of the public surface, bumped by hand when something breaks. */
export const API_VERSION = 2;

/**
 * Every name this module promises, in one list.
 *
 * It is here so that a test can hold the surface still. A public API that
 * grows a name by accident is a public API somebody depends on by accident,
 * and the version number above stops meaning anything the first time that
 * happens without being noticed.
 */
export const PUBLIC_NAMES = Object.freeze([
  'API_VERSION',
  'Angle',
  'BUILT_IN',
  'BadArgumentError',
  'CodecError',
  'CropPlan',
  'DeckleError',
  'DetectionError',
  'ERROR_CODES',
  'GeometryError',
  'Insets',
  'Length',
  'ManifestError',
  'PUBLIC_NAMES',
  'PipelineError',
  'PlanError',
  'Point',
  'ProfileError',
  'ProfileRegistry',
  'QcError',
  'Rect',
  'Sampling',
  'Size',
  'StoreError',
  'Surface',
  'SurfaceView',
  'Tolerance',
  'UnsupportedError',
  'Workspace',
  'builtInNamed',
  'builtInNames',
  'cli',
  'decide',
  'deliver',
  'deliveryFor',
  'deriveFromPlan',
  'describeInspection',
  'describePayload',
  'describeProfile',
  'describeRun',
  'digest',
  'digests',
  'explain',
  'fixityOf',
  'formatOf',
  'frequencyOf',
  'gradeCapture',
  'gradingToJSON',
  'inspect',
  'isDeckleError',
  'isUsable',
  'loadAll',
  'loadProfile',
  'nameFor',
  'namesFor',
  'objectionsToPayload',
  'parseSidecar',
  'planCrop',
  'policy',
  'policyFor',
  'preservationCopy',
  'probeImage',
  'process',
  'processIncoming',
  'readCapture',
  'readImage',
  'recordFailure',
  'registryWith',
  'reportOn',
  'runBatch',
  'runJob',
  'runQc',
  'settingsFor',
  'shiftReport',
  'stage',
  'standardChecks',
  'standardStages',
  'summariseCapture',
  'summarisePlan',
  'textOf',
  'verifyAgainst',
  'writeImage',
  'writeSidecar',
  'wrapError',
]);
