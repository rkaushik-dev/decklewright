import { readImage } from '../codec/registry.js';
import { deriveFromPlan } from '../derive/index.js';
import { readCapture } from '../detect/capture.js';
import { deliveryFor } from '../manifest/index.js';
import { planCrop } from '../plan/crop.js';
import { derivativeRecipes } from '../profile/settings.js';
import { runQc } from '../qc/runner.js';
import { describeGraph, objectionsToGraph, order } from './graph.js';
import { Job } from './job.js';
import { PipelineError } from '../errors.js';
import { decide, keepGoing, policyFor } from './policy.js';
import { describeResult, runStage, stage } from './stage.js';

/**
 * The standard run, stage by stage.
 *
 * Each stage is a thin wrapper round a call into one of the layers below. That
 * is on purpose: a stage is where the wiring lives and nowhere else, so the
 * detection layer never learns what a job is and the pipeline never learns how
 * a quad is fitted.
 *
 * The whole run is one accession. A batch is a loop over this, not a different
 * code path, because the last thing a lab needs is a batch that behaves
 * differently from the single sheet somebody tested the profile against.
 */

/** Read the bytes that came off the disk into pixels. */
export const readStage = stage({
  name: 'read',
  needs: ['bytes'],
  produces: ['surface'],
  run: (job) => ({ surface: readImage(job.need('bytes'), { path: job.get('path') }) }),
});

/** Find the sheet, the target, the neatline and the bar. */
export const detectStage = stage({
  name: 'detect',
  needs: ['surface'],
  produces: ['reading'],
  run: (job, context) => ({
    reading: readCapture(job.need('surface'), context.capture),
  }),
});

/** Work out where to cut. */
export const planStage = stage({
  name: 'plan',
  needs: ['surface', 'reading'],
  produces: ['plan'],
  run: (job, context) => {
    const out = planCrop(
      {
        reading: job.need('reading'),
        profile: context.profile,
        capture: job.need('surface').bounds,
        sampling: context.sampling,
      },
      context.crop ?? {},
    );
    if (!out.settled) {
      throw new PipelineError('the crop rules did not settle', { passes: out.passes });
    }
    return { plan: out.plan };
  },
});

/** Grade it. Optional, because a lab that only cuts sheets does not grade. */
export const gradeStage = stage({
  name: 'grade',
  needs: ['surface', 'reading'],
  produces: ['quality'],
  run: (job, context) => ({
    quality: runQc({
      surface: job.need('surface'),
      reading: job.need('reading'),
      profile: context.profile,
      sampling: context.sampling,
      crop: job.get('plan')?.rect,
    }),
  }),
});

/** Render, encode and name. */
export const deriveStage = stage({
  name: 'derive',
  needs: ['surface', 'plan'],
  produces: ['files'],
  run: (job, context) => {
    const out = deriveFromPlan(
      {
        surface: job.need('surface'),
        plan: job.need('plan'),
        profile: context.profile,
        accession: job.accession,
        recipes: derivativeRecipes(context.profile),
      },
      { level: context.level },
    );
    return { files: out.files, derivedBytes: out.bytes, derivedSteps: out.steps };
  },
});

/** Build the delivery and check it against itself. */
export const packStage = stage({
  name: 'pack',
  needs: ['surface', 'reading', 'plan', 'files'],
  produces: ['payload'],
  run: (job, context) => ({
    payload: deliveryFor({
      accession: job.accession,
      profile: context.profile,
      surface: job.need('surface'),
      reading: job.need('reading'),
      plan: job.need('plan'),
      files: job.need('files'),
      quality: job.get('quality'),
      warnings: job.warnings,
      version: context.version,
      host: context.host,
      at: context.at,
    }),
  }),
});

/** The run a lab gets when it says nothing. */
export function standardStages() {
  return [readStage, detectStage, planStage, gradeStage, deriveStage, packStage];
}

/**
 * @typedef {object} RunResult
 * @property {string} accession
 * @property {Job} job
 * @property {import('./stage.js').StageResult[]} results
 * @property {boolean} ok every stage that had to run did
 * @property {Error|null} error the first one that failed
 * @property {import('./policy.js').Decision|null} decision
 * @property {number} ms
 */

/**
 * Run every stage over one accession.
 *
 * It stops at the first failure. There is nothing useful a later stage can do
 * with a job whose crop could not be planned, and running them anyway produces
 * a second, more confusing error that people then try to fix first.
 *
 * @param {object} args
 * @param {string} args.accession
 * @param {Uint8Array} args.bytes
 * @param {Record<string, any>} args.profile
 * @param {import('../measure/resolution.js').Sampling} [args.sampling]
 * @param {string} [args.path] where the bytes came from, for the codec sniff
 * @param {object} [options]
 * @param {readonly import('./stage.js').Stage[]} [options.stages]
 * @param {import('./policy.js').Policy} [options.policy]
 * @param {boolean} [options.alreadyThere]
 * @param {string} [options.version]
 * @param {string} [options.host]
 * @param {Date} [options.at]
 * @param {number} [options.level]
 * @param {object} [options.capture]
 * @param {object} [options.crop]
 * @returns {Promise<RunResult>}
 */
export async function runJob({ accession, bytes, profile, sampling, path }, options = {}) {
  const started = Date.now();
  const stages = order(options.stages ?? standardStages());

  const objections = objectionsToGraph(stages, ['bytes', 'path']);
  if (objections.length > 0) {
    throw new PipelineError(objections[0], { accession, objections: objections.length });
  }

  const context = {
    profile,
    sampling,
    version: options.version ?? '0.0.0',
    host: options.host,
    at: options.at ?? new Date(),
    level: options.level,
    capture: options.capture,
    crop: options.crop,
  };

  let job = Job.start(accession, { bytes, path });
  /** @type {import('./stage.js').StageResult[]} */
  const results = [];
  /** @type {Error|null} */
  let error = null;

  for (const item of stages) {
    const result = await runStage(item, job, context);
    results.push(result);
    job = result.job;
    if (!result.ok) {
      error = result.error;
      break;
    }
  }

  const rules = options.policy ?? policyFor(profile);
  const decision = error
    ? null
    : decide({
        quality: job.get('quality'),
        warnings: job.warnings,
        alreadyThere: options.alreadyThere ?? false,
        policy: rules,
      });

  return {
    accession,
    job,
    results,
    ok: error === null,
    error,
    decision,
    ms: Date.now() - started,
  };
}

/**
 * @typedef {object} BatchResult
 * @property {RunResult[]} runs
 * @property {number} delivered
 * @property {number} rejected
 * @property {number} held
 * @property {number} failed
 * @property {boolean} stoppedEarly
 */

/**
 * Run a whole shift.
 *
 * @param {readonly Parameters<typeof runJob>[0][]} items
 * @param {Parameters<typeof runJob>[1]} [options]
 * @returns {Promise<BatchResult>}
 */
export async function runBatch(items, options = {}) {
  /** @type {RunResult[]} */
  const runs = [];
  let failed = 0;
  let stoppedEarly = false;

  for (const item of items) {
    const rules = options.policy ?? policyFor(item.profile);
    if (!keepGoing(rules, failed)) {
      stoppedEarly = true;
      break;
    }
    const out = await runJob(item, options);
    runs.push(out);
    if (!out.ok) failed += 1;
  }

  const counted = (/** @type {string} */ name) =>
    runs.filter((run) => run.decision?.decision === name).length;

  return {
    runs,
    delivered: counted('deliver'),
    rejected: counted('reject'),
    held: counted('hold'),
    failed,
    stoppedEarly,
  };
}

/**
 * What a run did, for a log or the terminal.
 *
 * @param {RunResult} run
 * @returns {string[]}
 */
export function describeRun(run) {
  const head = run.ok
    ? `${run.accession}: ${run.decision?.decision}, ${run.decision?.reason}, ${run.ms} ms`
    : `${run.accession}: failed after ${run.results.length} stages, ${run.ms} ms`;

  return [head, ...run.results.map((result) => `  ${describeResult(result)}`)];
}

/**
 * The plan for a run, before anything is read. A lab checking a new profile
 * runs this rather than a sheet.
 *
 * @param {readonly import('./stage.js').Stage[]} [stages]
 * @returns {string[]}
 */
export function describeRunPlan(stages) {
  return describeGraph(stages ?? standardStages());
}
