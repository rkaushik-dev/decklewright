import { MAX_STARS } from './check.js';

/**
 * Turning a set of findings into one number.
 *
 * The funder grades a delivery at a star level and the level is the worst
 * check, not the average. That is not a choice this code gets to make, and it
 * is worth stating plainly because averaging looks so much friendlier: a
 * capture with six checks at four stars and one at one star is a one star
 * capture, and the report has to say so.
 *
 * A check that could not run is not a zero. It is an absence, and a delivery
 * with an absence has no level at all until somebody decides whether that
 * check was required.
 */

/** What each level is called on a report. */
export const LEVEL_NAMES = Object.freeze({
  0: 'below the lowest level',
  1: 'one star',
  2: 'two stars',
  3: 'three stars',
  4: 'four stars',
});

/**
 * @param {readonly import('./check.js').Finding[]} findings
 * @returns {{stars: number, worst: string|null, ran: number, skipped: number}}
 */
export function levelOf(findings) {
  const ran = findings.filter((finding) => finding.ran);
  const skipped = findings.length - ran.length;
  if (ran.length === 0) {
    return { stars: 0, worst: null, ran: 0, skipped };
  }

  let stars = MAX_STARS;
  let worst = ran[0].name;
  for (const finding of ran) {
    if (finding.stars < stars) {
      stars = finding.stars;
      worst = finding.name;
    }
  }
  return { stars, worst, ran: ran.length, skipped };
}

/**
 * The level a delivery reaches once the checks that had to run are taken into
 * account. A required check that could not run drops the level to nothing,
 * because an unmeasured capture is not a passing one.
 *
 * @param {readonly import('./check.js').Finding[]} findings
 * @param {readonly string[]} required
 * @returns {{stars: number, worst: string|null, missing: string[]}}
 */
export function levelWithRequired(findings, required) {
  const byName = new Map(findings.map((finding) => [finding.name, finding]));
  const missing = required.filter((name) => !byName.get(name)?.ran);
  const base = levelOf(findings);
  if (missing.length > 0) {
    return { stars: 0, worst: missing[0], missing };
  }
  return { stars: base.stars, worst: base.worst, missing: [] };
}

/**
 * @param {number} stars
 * @returns {string}
 */
export function levelName(stars) {
  return LEVEL_NAMES[/** @type {keyof typeof LEVEL_NAMES} */ (stars)] ?? 'unknown';
}

/**
 * @param {readonly import('./check.js').Finding[]} findings
 * @param {number} minimumStars
 * @returns {boolean}
 */
export function meetsLevel(findings, minimumStars) {
  return levelOf(findings).stars >= minimumStars;
}

/**
 * How far a delivery is from the level it wants, named check by check. This is
 * what an operator reads when a batch comes back short: not that it failed but
 * which two checks are holding it down.
 *
 * @param {readonly import('./check.js').Finding[]} findings
 * @param {number} wanted
 * @returns {{name: string, stars: number, short: number}[]}
 */
export function shortfall(findings, wanted) {
  return findings
    .filter((finding) => finding.ran && finding.stars < wanted)
    .map((finding) => ({
      name: finding.name,
      stars: finding.stars,
      short: wanted - finding.stars,
    }))
    .sort((a, b) => b.short - a.short || a.name.localeCompare(b.name));
}

/**
 * A tally across a whole batch, which is the number a funder asks for.
 *
 * @param {readonly {stars: number}[]} levels
 * @returns {Record<string, number>}
 */
export function tallyLevels(levels) {
  /** @type {Record<string, number>} */
  const out = { 0: 0, 1: 0, 2: 0, 3: 0, 4: 0 };
  for (const level of levels) {
    const key = String(Math.max(0, Math.min(MAX_STARS, Math.round(level.stars))));
    out[key] += 1;
  }
  return out;
}

/**
 * @param {readonly {stars: number}[]} levels
 * @returns {number} the level the whole batch delivers at
 */
export function batchLevel(levels) {
  if (levels.length === 0) return 0;
  return levels.reduce((worst, level) => Math.min(worst, level.stars), MAX_STARS);
}
