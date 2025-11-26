import { deliver, recordFailure } from '../store/index.js';
import { policyFor } from './policy.js';
import { runJob } from './runner.js';

/**
 * The pipeline as the outside sees it.
 *
 * One call takes a workspace and an accession and does everything: read, cut,
 * grade, pack, write, and record. It is the only function in the tree that
 * both computes and writes, and it is deliberately short, because everything
 * it depends on has already been tested without a disk.
 */

export { Job, missingFrom } from './job.js';
export { assertDistinct, describeResult, insertBefore, runStage, stage, without } from './stage.js';
export {
  dependencies,
  describeGraph,
  objectionsToGraph,
  order,
  producers,
  waves,
} from './graph.js';
export {
  DECISIONS,
  HOUSE_POLICY,
  decide,
  describePolicy,
  keepGoing,
  policy,
  policyFor,
} from './policy.js';
export {
  deriveStage,
  describeRun,
  describeRunPlan,
  detectStage,
  gradeStage,
  packStage,
  planStage,
  readStage,
  runBatch,
  runJob,
  standardStages,
} from './runner.js';

/**
 * Work one capture out of a workspace and put the result where the policy
 * says it goes.
 *
 * A failure is recorded as carefully as a success. A shift where forty sheets
 * went out and one threw is a shift with one sheet nobody has looked at, and
 * the only way that sheet gets looked at is if it is in the ledger.
 *
 * @param {import('../store/workspace.js').Workspace} workspace
 * @param {object} args
 * @param {string} args.accession
 * @param {Uint8Array} args.bytes
 * @param {Record<string, any>} args.profile
 * @param {import('../measure/resolution.js').Sampling} [args.sampling]
 * @param {string} [args.path]
 * @param {Parameters<typeof runJob>[1]} [options]
 * @returns {Promise<{run: import('./runner.js').RunResult, directory: string|null, entry: import('../store/ledger.js').Entry}>}
 */
export async function process(workspace, args, options = {}) {
  const rules = options.policy ?? policyFor(args.profile);
  const run = await runJob(args, {
    ...options,
    policy: rules,
    alreadyThere: options.alreadyThere ?? workspace.has(args.accession),
  });

  if (!run.ok) {
    const entry = await recordFailure(workspace, {
      accession: args.accession,
      reason: run.error?.message ?? 'the run stopped without saying why',
      profile: args.profile.name,
      at: options.at,
    });
    return { run, directory: null, entry };
  }

  const decision = run.decision;
  if (!decision || decision.decision === 'hold') {
    const entry = await recordFailure(workspace, {
      accession: args.accession,
      reason: decision?.reason ?? 'nothing was decided',
      outcome: 'skipped',
      profile: args.profile.name,
      at: options.at,
    });
    return { run, directory: null, entry };
  }

  const written = await deliver(workspace, run.job.need('payload'), {
    folder: decision.folder,
    overwrite: rules.overwrite,
    profile: args.profile.name,
    stars: run.job.get('quality')?.stars,
    warnings: run.job.warnings,
    at: options.at,
  });
  return { run, directory: written.directory, entry: written.entry };
}

/**
 * The same over everything waiting in the incoming folder.
 *
 * @param {import('../store/workspace.js').Workspace} workspace
 * @param {object} args
 * @param {Record<string, any>} args.profile
 * @param {import('../measure/resolution.js').Sampling} [args.sampling]
 * @param {Parameters<typeof runJob>[1]} [options]
 * @returns {Promise<{done: Awaited<ReturnType<typeof process>>[], counts: Record<string, number>}>}
 */
export async function processIncoming(workspace, { profile, sampling }, options = {}) {
  const { readBytes } = await import('../store/atomic.js');
  const waiting = await workspace.incoming();

  /** @type {Awaited<ReturnType<typeof process>>[]} */
  const done = [];
  /** @type {Record<string, number>} */
  const counts = {};

  for (const item of waiting) {
    const out = await process(
      workspace,
      {
        accession: item.accession,
        bytes: await readBytes(item.path),
        profile,
        sampling,
        path: item.path,
      },
      options,
    );
    done.push(out);
    counts[out.entry.outcome] = (counts[out.entry.outcome] ?? 0) + 1;
  }
  return { done, counts };
}
