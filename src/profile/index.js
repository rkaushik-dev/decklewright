import { migrate } from './migrate.js';
import { ProfileRegistry } from './registry.js';

/**
 * Collection profiles as the rest of the tree sees them.
 *
 * A stage takes a resolved profile or one of the option bags settings.js makes
 * from it. Nothing above this reads a written document, and nothing below it
 * knows profiles exist.
 */

export { ProfileRegistry, registryWith } from './registry.js';
export {
  ANCHORS,
  OUTPUT_FORMATS,
  PROFILE_FIELDS,
  parseProfile,
  settingAt,
  settingPaths,
} from './fields.js';
export { BUILT_IN, builtInNamed, builtInNames } from './builtin.js';
export { changedPaths, overlay, overlayAll, resolveExtends } from './overlay.js';
export {
  assertUsable,
  describeProblems,
  fault,
  faultsOnly,
  inspectDerivatives,
  inspectProfile,
  note,
} from './validate.js';
export {
  CURRENT_VERSION,
  migrate,
  migrateAll,
  migrationNotes,
  needsMigration,
  versionOf,
} from './migrate.js';
export {
  captureOptions,
  claimedSampling,
  cropOptions,
  derivativeRecipes,
  edgesInPixels,
  fillName,
  qcLimits,
  settingsFor,
} from './settings.js';
export { describeDerivative, describeInPixels, describeProfile, report } from './diagnostics.js';
export {
  choice,
  edges,
  edgesToInsets,
  field,
  fieldError,
  flag,
  group,
  length,
  list,
  number,
  readGroup,
  text,
  tolerance,
} from './schema.js';

/**
 * Read a written document, bring it forward, flatten its chain, check it and
 * hand back what the stages take. This is the whole profile layer in one call,
 * and it is what the cli uses when somebody points at a file.
 *
 * @param {Record<string, any>} document
 * @param {object} [options]
 * @param {ProfileRegistry} [options.registry] where the parents live
 * @returns {Record<string, any>}
 */
export function loadProfile(document, { registry } = {}) {
  const brought = migrate(document).document;
  const store = registry ?? new ProfileRegistry();
  store.add(brought);
  return store.get(brought.name);
}

/**
 * @param {readonly Record<string, any>[]} documents
 * @param {object} [options]
 * @param {ProfileRegistry} [options.registry]
 * @returns {ProfileRegistry}
 */
export function loadAll(documents, { registry } = {}) {
  const store = registry ?? new ProfileRegistry();
  for (const document of documents) store.add(migrate(document).document);
  return store;
}
