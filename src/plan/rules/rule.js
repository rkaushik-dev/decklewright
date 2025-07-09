import { PlanError } from '../../errors.js';
import { assertNonEmptyString, assertOneOf } from '../../util/assert.js';

/**
 * What a crop rule is.
 *
 * A rule takes the plan as it stands and hands back the plan it wants. It does
 * not know what ran before it or what runs after, and it must be safe to run
 * twice, because the engine runs the set until it stops changing.
 *
 * The phase is the only ordering a rule gets to ask for. Four of them, and
 * they are enough:
 *
 *   anchor  what the crop is measured from
 *   shape   what shape it should be, before anything is rounded
 *   grid    whole pixels, multiples, and the other machine constraints
 *   check   nothing moves, only complaints
 */

/** @typedef {import('../plan.js').CropPlan} CropPlan */
/** @typedef {'anchor'|'shape'|'grid'|'check'} Phase */
/** @typedef {{name: string, phase: Phase, apply: (plan: CropPlan, context: Record<string, any>) => CropPlan}} Rule */

export const PHASES = Object.freeze(['anchor', 'shape', 'grid', 'check']);

/**
 * @param {object} args
 * @param {string} args.name
 * @param {Phase} args.phase
 * @param {(plan: CropPlan, context: Record<string, any>) => CropPlan} args.apply
 * @returns {Rule}
 */
export function rule({ name, phase, apply }) {
  assertNonEmptyString(name, 'name');
  assertOneOf(phase, PHASES, 'phase');
  if (typeof apply !== 'function') {
    throw new PlanError(`the ${name} rule has nothing to apply`, { name });
  }
  return Object.freeze({ name, phase, apply });
}

/**
 * @param {unknown} value
 * @returns {value is Rule}
 */
export function isRule(value) {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (/** @type {any} */ (value).name) === 'string' &&
    typeof (/** @type {any} */ (value).apply) === 'function'
  );
}

/**
 * @param {readonly Rule[]} rules
 * @param {Phase} phase
 * @returns {Rule[]}
 */
export function inPhase(rules, phase) {
  assertOneOf(phase, PHASES, 'phase');
  return rules.filter((item) => item.phase === phase);
}

/**
 * Sort by phase, keeping the order rules were given within a phase. A rule set
 * is written in the order a curator thinks about it and that order is worth
 * keeping where it does not conflict with the phases.
 *
 * @param {readonly Rule[]} rules
 * @returns {Rule[]}
 */
export function ordered(rules) {
  return [...rules].sort((a, b) => PHASES.indexOf(a.phase) - PHASES.indexOf(b.phase));
}

/**
 * @param {readonly Rule[]} rules
 * @returns {void}
 */
export function assertNoDuplicates(rules) {
  const seen = new Set();
  for (const item of rules) {
    if (seen.has(item.name)) {
      throw new PlanError(`the rule ${item.name} is in the set twice`, { name: item.name });
    }
    seen.add(item.name);
  }
}
