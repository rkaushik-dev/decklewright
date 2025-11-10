import { ManifestError } from '../errors.js';
import { agentName, chainFor } from './provenance.js';
import { objectionsToPayload, payloadFor } from './payload.js';
import { sidecarFor } from './sidecar.js';

/**
 * The manifest layer as the rest of the tree sees it.
 *
 * One call turns a finished run into a delivery that is ready to be written,
 * and refuses to hand one back that does not check out against itself. The
 * pieces are exported because a repository that already has its own sidecar
 * format wants the fixity and the provenance without ours.
 *
 * Like the derivative layer, nothing here touches a disk.
 */

export {
  ALGORITHMS,
  DEFAULT_ALGORITHM,
  digest,
  digestOfSet,
  digests,
  fixityOf,
  fromSumFile,
  sameDigest,
  toSumFile,
  verifyAgainst,
} from './checksum.js';
export {
  SIDECAR_VERSION,
  describeSidecar,
  objectionsToSidecar,
  parseSidecar,
  qualityOf,
  sidecarFor,
  writeSidecar,
} from './sidecar.js';
export {
  EVENT_TYPES,
  agentName,
  breaksIn,
  chainDigest,
  chainFor,
  describeChain,
  digestableForm,
  parseChain,
  recordEvent,
} from './provenance.js';
export {
  PROVENANCE_NAME,
  SIDECAR_NAME,
  SUMS_NAME,
  describePayload,
  entryNamed,
  objectionsToPayload,
  payloadFor,
  payloadSize,
} from './payload.js';

/**
 * Build the whole delivery from a finished run, and check it.
 *
 * The check is not optional and it is not a flag. A delivery that does not
 * agree with its own sidecar is not a delivery with a small problem, and the
 * only moment at which it is cheap to find that out is before it has been
 * written to a folder somebody else is going to trust.
 *
 * @param {object} args
 * @param {string} args.accession
 * @param {Record<string, any>} args.profile
 * @param {import('../raster/surface.js').Surface} args.surface
 * @param {import('../detect/capture.js').CaptureReading} args.reading
 * @param {import('../plan/plan.js').CropPlan} args.plan
 * @param {readonly import('../derive/oneshot.js').DerivedFile[]} args.files
 * @param {Record<string, any>} [args.quality]
 * @param {readonly string[]} [args.warnings]
 * @param {string} [args.version] what to record as the agent
 * @param {string} [args.host]
 * @param {Date} [args.at]
 * @returns {import('./payload.js').Payload}
 */
export function deliveryFor(args) {
  const at = args.at ?? new Date();
  const sidecar = sidecarFor({ ...args, created: at });
  const provenance = chainFor({
    agent: agentName({ version: args.version ?? '0.0.0', host: args.host }),
    reading: args.reading,
    plan: args.plan,
    files: args.files,
    quality: args.quality,
    at,
  });

  const payload = payloadFor({
    accession: args.accession,
    files: args.files,
    sidecar,
    provenance,
  });

  const objections = objectionsToPayload(payload);
  if (objections.length > 0) {
    throw new ManifestError(objections[0], {
      accession: args.accession,
      objections: objections.length,
    });
  }
  return payload;
}
