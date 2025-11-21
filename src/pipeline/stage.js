import { PipelineError, isDeckleError } from '../errors.js';
import { missingFrom } from './job.js';

/** @typedef {import('./job.js').Job} Job */

/**
 * One step, and what it needs before it can take one.
 *
 * A stage says what it wants off the job and what it will put back. Those two
 * lists are the whole of the wiring: the graph is worked out from them rather
 * than written down, so a stage inserted in the middle cannot be inserted in
 * the wrong place.
 *
 * A stage's run returns the values it produced, not a job. It never sees the
 * job's history and cannot edit it, and it does not have to remember to record
 * that it ran.
 */

/**
 * @typedef {object} Stage
 * @property {string} name
 * @property {string[]} needs
 * @property {string[]} produces
 * @property {(job: Job, context: Record<string, any>) => Record<string, any>|Promise<Record<string, any>>} run
 * @property {boolean} optional a job that cannot feed it is skipped, not failed
 */

/**
 * @param {object} args
 * @param {string} args.name
 * @param {readonly string[]} [args.needs]
 * @param {readonly string[]} [args.produces]
 * @param {boolean} [args.optional]
 * @param {(job: Job, context: Record<string, any>) => any} args.run
 * @returns {Stage}
 */
export function stage({ name, needs = [], produces = [], optional = false, run }) {
  if (!name) throw new PipelineError('a stage has to have a name', {});
  if (typeof run !== 'function') throw new PipelineError('a stage has to do something', { name });

  return Object.freeze({
    name,
    needs: Object.freeze([...needs]),
    produces: Object.freeze([...produces]),
    optional,
    run,
  });
}

/**
 * @typedef {object} StageResult
 * @property {string} stage
 * @property {Job} job what to carry on with
 * @property {boolean} ran
 * @property {boolean} ok
 * @property {string[]} missing what it wanted and did not get
 * @property {Error|null} error
 * @property {number} ms
 */

/**
 * Run one stage against a job.
 *
 * Three outcomes and they are kept apart on purpose. It ran and worked. It was
 * skipped because the job did not carry what it needs, which for an optional
 * stage is a normal Tuesday. Or it threw, which is a failure that belongs to
 * this accession and not to the run.
 *
 * A stage that says it produces something and does not is a failure here
 * rather than three stages later, because the stage that was going to need it
 * has a much worse error message.
 *
 * @param {Stage} item
 * @param {Job} job
 * @param {Record<string, any>} [context] what every stage gets, like the profile
 * @returns {Promise<StageResult>}
 */
export async function runStage(item, job, context = {}) {
  const started = Date.now();
  const missing = missingFrom(job, item.needs);

  if (missing.length > 0) {
    if (item.optional) {
      return {
        stage: item.name,
        job: job.noted(`${item.name} was skipped, no ${missing.join(' or ')}`),
        ran: false,
        ok: true,
        missing,
        error: null,
        ms: Date.now() - started,
      };
    }
    return {
      stage: item.name,
      job,
      ran: false,
      ok: false,
      missing,
      error: new PipelineError('a stage was reached without what it needs', {
        stage: item.name,
        accession: job.accession,
        wanted: missing.join(', '),
      }),
      ms: Date.now() - started,
    };
  }

  try {
    const produced = (await item.run(job, context)) ?? {};
    const short = item.produces.filter((name) => produced[name] === undefined);
    if (short.length > 0) {
      throw new PipelineError('a stage did not produce what it said it would', {
        stage: item.name,
        accession: job.accession,
        missing: short.join(', '),
      });
    }

    return {
      stage: item.name,
      job: job.with(produced).finished(item.name),
      ran: true,
      ok: true,
      missing: [],
      error: null,
      ms: Date.now() - started,
    };
  } catch (caught) {
    const error = isDeckleError(caught)
      ? caught.annotate({ stage: item.name, accession: job.accession })
      : /** @type {Error} */ (caught);
    return {
      stage: item.name,
      job,
      ran: false,
      ok: false,
      missing: [],
      error,
      ms: Date.now() - started,
    };
  }
}

/**
 * @param {readonly Stage[]} stages
 * @returns {void} throws on a name used twice
 */
export function assertDistinct(stages) {
  const seen = new Set();
  for (const item of stages) {
    if (seen.has(item.name)) {
      throw new PipelineError('two stages have the same name', { name: item.name });
    }
    seen.add(item.name);
  }
}

/**
 * @param {readonly Stage[]} stages
 * @param {readonly string[]} names
 * @returns {Stage[]}
 */
export function without(stages, names) {
  const drop = new Set(names);
  return stages.filter((item) => !drop.has(item.name));
}

/**
 * Put a stage in front of another one by name, which is how a lab adds its own
 * step without editing the standard list.
 *
 * @param {readonly Stage[]} stages
 * @param {string} before
 * @param {Stage} item
 * @returns {Stage[]}
 */
export function insertBefore(stages, before, item) {
  const at = stages.findIndex((one) => one.name === before);
  if (at < 0) throw new PipelineError('there is no stage by that name', { name: before });
  return [...stages.slice(0, at), item, ...stages.slice(at)];
}

/**
 * @param {StageResult} result
 * @returns {string}
 */
export function describeResult(result) {
  if (result.ran) return `${result.stage} ran in ${result.ms} ms`;
  if (result.ok) return `${result.stage} was skipped, no ${result.missing.join(' or ')}`;
  return `${result.stage} failed: ${result.error?.message ?? 'for no stated reason'}`;
}
