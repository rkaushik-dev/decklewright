import { Rect } from '../geom/rect.js';
import { Sampling } from '../measure/resolution.js';
import { cropOptions } from '../profile/settings.js';
import { planWith } from './engine.js';
import { rulesFor } from './ruleset.js';

/**
 * A corpus of planning cases with the answers written down.
 *
 * A profile change is meant to change one collection. What it does in practice
 * is change every collection that inherits from the same house style, and the
 * only way anybody notices is when a plate comes out wrong three weeks later.
 *
 * This is the answer to that: a set of cases with the crop each one produced
 * on the day it was recorded. Run it after a change and read the differences.
 * A difference is not automatically wrong. It has to be looked at, which is
 * the whole point.
 */

/**
 * @typedef {object} Case
 * @property {string} name
 * @property {string} profile
 * @property {{x: number, y: number, width: number, height: number}} capture
 * @property {{x: number, y: number, width: number, height: number}} sheet
 * @property {{x: number, y: number, width: number, height: number}} [neatline]
 * @property {number} ppi
 * @property {{x: number, y: number, width: number, height: number}} expected
 * @property {number} [warnings]
 */

/** The cases as they stood when the rule engine replaced the linear planner. */
export const CASES = Object.freeze([
  {
    name: 'plate, square to the bed',
    profile: 'house',
    capture: { x: 0, y: 0, width: 8000, height: 6400 },
    sheet: { x: 700, y: 600, width: 6800, height: 5600 },
    ppi: 400,
    expected: { x: 700, y: 600, width: 6800, height: 5600 },
    warnings: 0,
  },
  {
    name: 'plate with the atlas bleed',
    profile: 'atlas-plates',
    capture: { x: 0, y: 0, width: 12000, height: 9600 },
    sheet: { x: 1000, y: 900, width: 10200, height: 8400 },
    ppi: 600,
    expected: { x: 952, y: 852, width: 10296, height: 8496 },
    warnings: 0,
  },
  {
    name: 'quadrangle cropped to the neatline',
    profile: 'quadrangles',
    capture: { x: 0, y: 0, width: 14000, height: 17000 },
    sheet: { x: 800, y: 700, width: 12600, height: 15600 },
    neatline: { x: 1400, y: 1300, width: 11400, height: 13600 },
    ppi: 600,
    expected: { x: 1328, y: 1225, width: 11544, height: 14576 },
    warnings: 0,
  },
  {
    name: 'rolled drawing with a wide bleed',
    profile: 'rolled-drawings',
    capture: { x: 0, y: 0, width: 8000, height: 12000 },
    sheet: { x: 500, y: 600, width: 7000, height: 10800 },
    ppi: 300,
    expected: { x: 429, y: 529, width: 7142, height: 10942 },
    warnings: 0,
  },
  {
    name: 'microfilm frame, no bleed at all',
    profile: 'microfilm',
    capture: { x: 0, y: 0, width: 3000, height: 2400 },
    sheet: { x: 200, y: 150, width: 2600, height: 2100 },
    ppi: 300,
    expected: { x: 200, y: 150, width: 2600, height: 2100 },
    warnings: 0,
  },
  {
    name: 'plate placed too near the edge of the bed',
    profile: 'atlas-plates',
    capture: { x: 0, y: 0, width: 8000, height: 6400 },
    sheet: { x: 20, y: 20, width: 7900, height: 6300 },
    ppi: 600,
    expected: { x: -28, y: -28, width: 7996, height: 6396 },
    warnings: 1,
  },
  {
    name: 'plate captured well under the floor',
    profile: 'quadrangles',
    capture: { x: 0, y: 0, width: 4000, height: 5000 },
    sheet: { x: 200, y: 200, width: 3600, height: 4600 },
    neatline: { x: 400, y: 400, width: 3200, height: 4000 },
    ppi: 200,
    expected: { x: 376, y: 374, width: 3248, height: 4328 },
    warnings: 1,
  },
]);

/**
 * @param {Record<string, any>} bag
 * @returns {Rect}
 */
function toRect(bag) {
  return Rect.of(bag.x, bag.y, bag.width, bag.height);
}

/**
 * @param {Case} item
 * @param {import('../profile/registry.js').ProfileRegistry} registry
 * @returns {import('./plan.js').CropPlan}
 */
export function runCase(item, registry) {
  const profile = registry.get(item.profile);
  const sampling = Sampling.of(item.ppi);
  const crop = cropOptions(profile, sampling);
  const found = {
    sheet: toRect(item.sheet),
    neatline: item.neatline ? toRect(item.neatline) : undefined,
  };
  const anchor = crop.anchor === 'neatline' && found.neatline ? found.neatline : found.sheet;
  const rules = rulesFor(crop, found);
  return planWith(rules, { capture: toRect(item.capture), anchor, sampling });
}

/**
 * @typedef {object} CaseResult
 * @property {string} name
 * @property {boolean} matched
 * @property {{x: number, y: number, width: number, height: number}} expected
 * @property {{x: number, y: number, width: number, height: number}} actual
 * @property {number} warnings
 * @property {string|null} error
 */

/**
 * @param {import('../profile/registry.js').ProfileRegistry} registry
 * @param {readonly Case[]} [cases]
 * @returns {CaseResult[]}
 */
export function runCorpus(registry, cases = CASES) {
  return cases.map((item) => {
    try {
      const plan = runCase(item, registry);
      const actual = plan.rect.toJSON();
      const matched =
        plan.rect.equals(toRect(item.expected)) &&
        (item.warnings === undefined || plan.warnings.length === item.warnings);
      return {
        name: item.name,
        matched,
        expected: item.expected,
        actual,
        warnings: plan.warnings.length,
        error: null,
      };
    } catch (error) {
      return {
        name: item.name,
        matched: false,
        expected: item.expected,
        actual: { x: 0, y: 0, width: 0, height: 0 },
        warnings: 0,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  });
}

/**
 * @param {readonly CaseResult[]} results
 * @returns {string[]}
 */
export function describeResults(results) {
  return results.map((result) => {
    if (result.error) return `${result.name}: failed, ${result.error}`;
    if (result.matched) return `${result.name}: as recorded`;
    const { expected: e, actual: a } = result;
    return `${result.name}: was ${e.width}x${e.height}+${e.x}+${e.y}, now ${a.width}x${a.height}+${a.x}+${a.y}`;
  });
}

/**
 * @param {readonly CaseResult[]} results
 * @returns {{total: number, matched: number, changed: number, failed: number}}
 */
export function tally(results) {
  return {
    total: results.length,
    matched: results.filter((r) => r.matched).length,
    changed: results.filter((r) => !r.matched && !r.error).length,
    failed: results.filter((r) => r.error !== null).length,
  };
}
