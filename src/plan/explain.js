import { describeSize } from '../measure/physical.js';
import { tensionsIn } from './ruleset.js';

/**
 * Saying why a crop is where it is.
 *
 * Every complaint about a crop is the same complaint: it is not where I
 * expected. The trail answers it, but only if it is printed in a way somebody
 * can read at eight in the morning with two hundred plates to get through.
 *
 * The rules that changed nothing are left out. A trail that lists six rules
 * and shows three changes is easier to read than one that lists six rules and
 * shows six, five of which did nothing.
 */

/** @typedef {import('./plan.js').CropPlan} CropPlan */

/**
 * @param {import('../geom/rect.js').Rect} before
 * @param {import('../geom/rect.js').Rect} after
 * @returns {string}
 */
export function describeMove(before, after) {
  const parts = [];
  const dw = after.width - before.width;
  const dh = after.height - before.height;
  const dx = after.x - before.x;
  const dy = after.y - before.y;
  if (dw !== 0 || dh !== 0) {
    parts.push(`${dw >= 0 ? 'grew' : 'shrank'} by ${Math.abs(dw)} by ${Math.abs(dh)}`);
  }
  if (dx !== 0 || dy !== 0) parts.push(`moved by ${dx} and ${dy}`);
  return parts.length > 0 ? parts.join(', ') : 'no change';
}

/**
 * @param {CropPlan} plan
 * @returns {string[]}
 */
export function explainSteps(plan) {
  return plan.steps
    .filter((step) => step.rule !== 'start')
    .map((step) => `${step.rule}: ${step.note} (${describeMove(step.before, step.after)})`);
}

/**
 * @param {CropPlan} plan
 * @param {object} [options]
 * @param {import('../measure/units.js').Unit} [options.unit]
 * @returns {string[]}
 */
export function explain(plan, { unit = 'in' } = {}) {
  const lines = [
    `crop ${plan.rect} at ${plan.sampling}`,
    `which is ${describeSize(plan.physical(unit), unit === 'in' ? 2 : 1)}`,
  ];
  if (!plan.deskew.isZero) lines.push(`after taking out ${plan.deskew} of skew`);

  const steps = explainSteps(plan);
  lines.push(steps.length > 0 ? `${steps.length} rules moved it:` : 'no rule moved it');
  lines.push(...steps.map((line) => `  ${line}`));

  for (const warning of plan.warnings) lines.push(`warning: ${warning}`);
  return lines;
}

/**
 * The same explanation with the rule set alongside, so a curator can see the
 * rules that ran and did nothing as well as the ones that did something.
 *
 * @param {CropPlan} plan
 * @param {readonly import('./rules/rule.js').Rule[]} rules
 * @param {object} [options]
 * @param {import('../measure/units.js').Unit} [options.unit]
 * @returns {string[]}
 */
export function explainWithRules(plan, rules, options) {
  const moved = new Set(plan.trail);
  const quiet = rules.filter((item) => !moved.has(item.name)).map((item) => item.name);
  const lines = explain(plan, options);
  if (quiet.length > 0) lines.push(`rules that changed nothing: ${quiet.join(', ')}`);

  const tensions = tensionsIn(rules);
  for (const tension of tensions) lines.push(`note: ${tension.says}`);
  return lines;
}

/**
 * One line, for a batch log where two hundred of these go past.
 *
 * @param {CropPlan} plan
 * @returns {string}
 */
export function summarise(plan) {
  const trail = plan.trail.length > 0 ? plan.trail.join(' then ') : 'nothing';
  const warned = plan.warnings.length > 0 ? `, ${plan.warnings.length} warnings` : '';
  return `${plan.rect} after ${trail}${warned}`;
}

/**
 * Which rule made the biggest difference, which is nearly always the one
 * somebody wants to argue with.
 *
 * @param {CropPlan} plan
 * @returns {{rule: string, area: number}|null}
 */
export function biggestMove(plan) {
  let best = null;
  for (const step of plan.steps) {
    if (step.rule === 'start') continue;
    const area = Math.abs(step.after.area - step.before.area);
    if (best === null || area > best.area) best = { rule: step.rule, area };
  }
  return best;
}
