import { PipelineError } from '../errors.js';
import { assertDistinct } from './stage.js';

/**
 * Working out what order the stages go in.
 *
 * Nobody writes the order down. Each stage says what it needs and what it
 * produces, and the order falls out of that. An order that is written down is
 * an order that goes stale the first time somebody adds a stage in the middle,
 * and the way it goes stale is that a stage runs before the one that feeds it
 * and gets a value from the previous accession.
 *
 * The sort is stable: among stages that could run in any order, the one listed
 * first goes first. That matters for reading a log more than for correctness,
 * and reading a log is most of what anybody does with this.
 */

/** @typedef {import('./stage.js').Stage} Stage */

/**
 * Which stage produces each value. Two stages producing the same value is
 * refused, because then the order between them decides the answer and nothing
 * in the graph says what that order should be.
 *
 * @param {readonly Stage[]} stages
 * @returns {Map<string, string>} value to stage name
 */
export function producers(stages) {
  /** @type {Map<string, string>} */
  const out = new Map();
  for (const item of stages) {
    for (const value of item.produces) {
      const held = out.get(value);
      if (held) {
        throw new PipelineError('two stages produce the same value', {
          value,
          stages: `${held} and ${item.name}`,
        });
      }
      out.set(value, item.name);
    }
  }
  return out;
}

/**
 * What each stage has to wait for.
 *
 * A need nothing produces is not an error here. It is a value the run is
 * expected to start with, like the surface that was read off the disk, and
 * saying which ones those are is more useful than refusing.
 *
 * @param {readonly Stage[]} stages
 * @returns {{edges: Map<string, Set<string>>, fromOutside: string[]}}
 */
export function dependencies(stages) {
  const made = producers(stages);
  /** @type {Map<string, Set<string>>} */
  const edges = new Map();
  /** @type {Set<string>} */
  const outside = new Set();

  for (const item of stages) {
    /** @type {Set<string>} */
    const waits = new Set();
    for (const need of item.needs) {
      const from = made.get(need);
      if (from && from !== item.name) waits.add(from);
      else if (!from) outside.add(need);
    }
    edges.set(item.name, waits);
  }
  return { edges, fromOutside: [...outside].sort() };
}

/**
 * The order to run them in.
 *
 * @param {readonly Stage[]} stages
 * @returns {Stage[]}
 */
export function order(stages) {
  assertDistinct(stages);
  const { edges } = dependencies(stages);

  /** @type {Stage[]} */
  const out = [];
  const placed = new Set();
  const remaining = [...stages];

  while (remaining.length > 0) {
    const at = remaining.findIndex((item) =>
      [...(edges.get(item.name) ?? [])].every((name) => placed.has(name)),
    );
    if (at < 0) {
      throw new PipelineError('these stages wait on each other in a circle', {
        stages: remaining.map((item) => item.name).join(', '),
      });
    }
    const [next] = remaining.splice(at, 1);
    placed.add(next.name);
    out.push(next);
  }
  return out;
}

/**
 * Everything wrong with a set of stages, given what the run will start with.
 *
 * This is the check that runs before anything is read off a disk. A pipeline
 * that cannot possibly work should say so in the first second rather than
 * after the four hundred megabyte master has been decoded.
 *
 * @param {readonly Stage[]} stages
 * @param {readonly string[]} [starting] values the run supplies itself
 * @returns {string[]}
 */
export function objectionsToGraph(stages, starting = []) {
  /** @type {string[]} */
  const out = [];
  if (stages.length === 0) return ['there are no stages to run'];

  try {
    assertDistinct(stages);
    const { fromOutside } = dependencies(stages);
    const supplied = new Set(starting);
    for (const need of fromOutside) {
      if (!supplied.has(need)) out.push(`nothing produces ${need} and the run does not supply it`);
    }
    order(stages);
  } catch (caught) {
    out.push(/** @type {Error} */ (caught).message);
  }
  return out;
}

/**
 * The order, with what each stage is waiting for, for a person reading it.
 *
 * @param {readonly Stage[]} stages
 * @returns {string[]}
 */
export function describeGraph(stages) {
  const { edges, fromOutside } = dependencies(stages);
  const sorted = order(stages);

  /** @type {string[]} */
  const lines = [];
  if (fromOutside.length > 0) lines.push(`starts with ${fromOutside.join(', ')}`);

  sorted.forEach((item, i) => {
    const waits = [...(edges.get(item.name) ?? [])];
    const after = waits.length > 0 ? ` after ${waits.join(', ')}` : '';
    const makes = item.produces.length > 0 ? ` making ${item.produces.join(', ')}` : '';
    lines.push(`${i + 1}. ${item.name}${after}${makes}`);
  });
  return lines;
}

/**
 * The stages that could run at the same time, in waves. Nothing here runs a
 * wave in parallel, and that is deliberate: a master is hundreds of megabytes
 * and two stages holding one each is how a laptop in a reading room runs out
 * of memory. The waves are here because they say something true about the
 * shape of the pipeline, and a lab with a real machine can use them.
 *
 * @param {readonly Stage[]} stages
 * @returns {string[][]}
 */
export function waves(stages) {
  const { edges } = dependencies(stages);
  const sorted = order(stages);

  /** @type {Map<string, number>} */
  const depth = new Map();
  for (const item of sorted) {
    const waits = [...(edges.get(item.name) ?? [])];
    const deepest = waits.reduce((most, name) => Math.max(most, (depth.get(name) ?? 0) + 1), 0);
    depth.set(item.name, deepest);
  }

  /** @type {string[][]} */
  const out = [];
  for (const item of sorted) {
    const at = depth.get(item.name) ?? 0;
    if (!out[at]) out[at] = [];
    out[at].push(item.name);
  }
  return out;
}
