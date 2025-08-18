import { anchorFor, anchorOrSheetRule } from './rules/anchor.js';
import { aspectFor } from './rules/aspect.js';
import { bleedFor } from './rules/bleed.js';
import { boundsFor, notEmptyRule } from './rules/bounds.js';
import { resolutionFor } from './rules/resolution.js';
import { roundRule, snapFor } from './rules/snap.js';
import { safeAreaFor } from './rules/safearea.js';
import { assertNoDuplicates, ordered } from './rules/rule.js';

/**
 * Building the rule set a profile asks for.
 *
 * The profile says what it wants in its own words and this turns that into
 * rules. It is the only place that knows which rule goes with which setting,
 * so adding a setting means editing one file and adding a rule means editing
 * the same one.
 *
 * A set always ends with notEmpty, whatever the profile said. There is no
 * collection anywhere that wants a crop with no area.
 */

/**
 * @param {Record<string, any>} crop the crop options, already in pixels
 * @param {object} found
 * @param {import('../geom/rect.js').Rect} found.sheet
 * @param {import('../geom/rect.js').Rect} [found.neatline]
 * @param {object} [options]
 * @param {boolean} [options.fallBackToSheet]
 * @param {string} [options.boundsMode]
 * @param {boolean} [options.strictResolution]
 * @returns {import('./rules/rule.js').Rule[]}
 */
export function rulesFor(crop, found, options = {}) {
  const { fallBackToSheet = false, boundsMode = 'warn', strictResolution = false } = options;

  const anchor = fallBackToSheet
    ? anchorOrSheetRule(crop.anchor, found)
    : anchorFor(crop.anchor, found);

  /** @type {import('./rules/rule.js').Rule[]} */
  const rules = [anchor];

  if (!crop.bleed.isZero) {
    rules.push(bleedFor(crop.bleed, { mode: boundsMode === 'clamp' ? 'trimmed' : 'plain' }));
  }
  if (!crop.safeArea.isZero) rules.push(safeAreaFor(crop.safeArea));

  const aspect = aspectFor(crop.aspect, { direction: crop.aspectDirection });
  if (aspect) rules.push(aspect);

  if (crop.snapTo > 1) rules.push(snapFor(crop.snapTo, { direction: crop.snapDirection }));
  rules.push(roundRule(crop.rounding));
  rules.push(boundsFor(boundsMode));
  rules.push(resolutionFor(crop.minimumPpi, { strict: strictResolution }));
  rules.push(notEmptyRule());

  assertNoDuplicates(rules);
  return rules;
}

/**
 * Rules that are known to argue with each other. They are allowed to be in the
 * same set, because the engine settles most of them, but the pairing is worth
 * naming so an explanation can point at it.
 */
export const KNOWN_TENSIONS = Object.freeze([
  {
    between: ['aspect', 'snap'],
    says: 'a forced aspect and a snap pull against each other and the engine settles them',
  },
  {
    between: ['bleed', 'safeArea'],
    says: 'a bleed pushes out and a safe area pulls in on the same edge',
  },
  {
    between: ['bleed', 'bounds'],
    says: 'a bleed can push the crop off the capture, which the bounds rule then answers',
  },
  {
    between: ['aspect', 'bounds'],
    says: 'a forced aspect can push the crop off the capture',
  },
]);

/**
 * @param {readonly import('./rules/rule.js').Rule[]} rules
 * @returns {{between: string[], says: string}[]}
 */
export function tensionsIn(rules) {
  const present = new Set(rules.map((item) => item.name));
  return KNOWN_TENSIONS.filter((tension) => tension.between.every((name) => present.has(name)));
}

/**
 * @param {readonly import('./rules/rule.js').Rule[]} rules
 * @returns {string[]}
 */
export function describeSet(rules) {
  return ordered(rules).map((item) => `${item.phase}: ${item.name}`);
}

/**
 * The set the house style produces, which is what most collections run and
 * what the regression corpus is measured against.
 *
 * @param {Record<string, any>} crop
 * @param {object} found
 * @param {import('../geom/rect.js').Rect} found.sheet
 * @param {import('../geom/rect.js').Rect} [found.neatline]
 * @returns {import('./rules/rule.js').Rule[]}
 */
export function houseRules(crop, found) {
  return rulesFor(crop, found, { boundsMode: 'warn' });
}
