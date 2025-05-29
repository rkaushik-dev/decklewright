import { objectMask } from './background.js';
import { labelComponents } from './components.js';
import { close, fillHoles, open } from './morphology.js';
import { findNeatline, collarWidths } from './neatline.js';
import { findScaleBar, samplingFromBar, checkAgainstClaim } from './scalebar.js';
import { findSheet, objections } from './sheet.js';
import { locateTarget } from './target.js';

/** @typedef {import('../raster/surface.js').Surface} Surface */
/** @typedef {import('./sheet.js').SheetFinding} SheetFinding */
/** @typedef {import('./target.js').TargetFinding} TargetFinding */
/** @typedef {import('./scalebar.js').ScaleBarFinding} ScaleBarFinding */
/** @typedef {import('./neatline.js').Neatline} Neatline */
/** @typedef {import('../measure/resolution.js').Sampling} Sampling */

/**
 * Reading everything off one capture.
 *
 * A work order names a capture and a collection. This turns the capture into
 * the four facts a crop plan needs: where the sheet is, how far it is turned,
 * where the neatline is if there is one, and what the sampling frequency
 * really was. Anything it could not establish comes back as null with a
 * warning rather than as a guess.
 */

/**
 * @typedef {object} CaptureReading
 * @property {SheetFinding} sheet
 * @property {TargetFinding|null} target
 * @property {ScaleBarFinding|null} scaleBar
 * @property {Neatline|null} neatline
 * @property {Sampling|null} sampling measured from the rule
 * @property {string[]} warnings
 */

/**
 * @param {Surface} surface
 * @param {object} [options]
 * @param {number} [options.sigma]
 * @param {number} [options.speckle]
 * @param {number} [options.gap]
 * @param {number} [options.barLength] the true length of the scale rule
 * @param {import('../measure/units.js').Unit} [options.barUnit]
 * @param {Sampling} [options.claimed] what the scanner said it captured at
 * @param {boolean} [options.expectNeatline]
 * @returns {CaptureReading}
 */
export function readCapture(surface, options = {}) {
  const {
    sigma = 6,
    speckle = 2,
    gap = 3,
    barLength,
    barUnit = 'in',
    claimed,
    expectNeatline = false,
  } = options;

  const sheet = findSheet(surface, { sigma, speckle, gap });
  const warnings = objections(sheet);

  const { mask: everything } = objectMask(surface, { sigma });
  const cleaned = fillHoles(close(open(everything, speckle), gap));
  const sheetId = componentIdAt(cleaned, sheet);

  const target = locateTarget(surface, cleaned, { ignore: sheetId ? [sheetId] : [] });
  if (!target) warnings.push('no colour target on the bed');

  const scaleBar = findScaleBar(cleaned, { ignore: sheetId ? [sheetId] : [] });
  if (!scaleBar) warnings.push('no scale bar on the bed');

  /** @type {Sampling|null} */
  let sampling = null;
  if (scaleBar && barLength) {
    sampling = samplingFromBar(scaleBar, barLength, barUnit);
    if (claimed) {
      const check = checkAgainstClaim(sampling, claimed);
      if (!check.agrees) {
        warnings.push(
          `the rule measures ${Math.round(check.measured)} ppi against a claimed ${Math.round(check.claimed)}`,
        );
      }
    }
  }

  /** @type {Neatline|null} */
  let neatline = null;
  if (expectNeatline) {
    neatline = findNeatline(surface, sheet.quad.boundingRect.outward());
    if (!neatline.complete) {
      warnings.push(`no neatline found on the ${neatline.missing.join(' or ')}`);
    }
  }

  return { sheet, target, scaleBar, neatline, sampling, warnings };
}

/**
 * Which labelled blob the sheet is, so the other detectors can skip it. The
 * centre of the fitted quad is inside the sheet by construction, so a single
 * lookup is enough.
 *
 * @param {import('./mask.js').Mask} mask
 * @param {SheetFinding} sheet
 * @returns {number|null}
 */
export function componentIdAt(mask, sheet) {
  const centre = sheet.quad.centre;
  const x = Math.round(centre.x);
  const y = Math.round(centre.y);
  if (!mask.get(x, y)) return null;
  const { labels } = labelComponents(mask);
  const id = labels[y * mask.width + x];
  return id > 0 ? id : null;
}

/**
 * @param {CaptureReading} reading
 * @returns {string[]}
 */
export function summarise(reading) {
  const box = reading.sheet.quad.boundingRect;
  const lines = [
    `sheet ${Math.round(box.width)}x${Math.round(box.height)} at ${reading.sheet.skew.degrees.toFixed(2)} deg`,
  ];
  if (reading.target) lines.push(`target over ${Math.round(reading.target.range)} levels`);
  if (reading.scaleBar) lines.push(`rule ${Math.round(reading.scaleBar.lengthPixels)} px long`);
  if (reading.sampling) lines.push(`measured ${reading.sampling}`);
  if (reading.neatline) {
    const widths = collarWidths(box.outward(), reading.neatline.rect);
    lines.push(`collar ${widths.top}/${widths.right}/${widths.bottom}/${widths.left}`);
  }
  for (const warning of reading.warnings) lines.push(`warning: ${warning}`);
  return lines;
}

/**
 * @param {CaptureReading} reading
 * @returns {boolean} whether a plan can be built without a human looking first
 */
export function isUsable(reading) {
  return reading.sheet.confidence >= 0.5 && !reading.sheet.touchesEdge;
}
