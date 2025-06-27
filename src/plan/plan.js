import { Angle } from '../geom/angle.js';
import { physicalSize } from '../measure/physical.js';

/** @typedef {import('../geom/rect.js').Rect} Rect */
/** @typedef {import('../measure/resolution.js').Sampling} Sampling */

/**
 * A crop plan, and the record of how it got that way.
 *
 * The rect is the answer. The steps are why, and they matter as much: a
 * curator who thinks a crop is wrong wants to know which rule moved it, and
 * an operator who has to override one wants to know which one to override.
 *
 * A plan is immutable. Every rule hands back a new one with its step recorded,
 * so the trail cannot be lost by a rule that forgot to append to it.
 */

/** @typedef {{rule: string, note: string, before: Rect, after: Rect}} Step */

export class CropPlan {
  /**
   * @param {object} args
   * @param {Rect} args.capture the whole capture, in pixels
   * @param {Rect} args.rect the crop as it stands
   * @param {Sampling} args.sampling
   * @param {Angle} [args.deskew] the turn to take out before cropping
   * @param {readonly Step[]} [args.steps]
   * @param {readonly string[]} [args.warnings]
   */
  constructor({ capture, rect, sampling, deskew, steps = [], warnings = [] }) {
    this.capture = capture;
    this.rect = rect;
    this.sampling = sampling;
    this.deskew = deskew ?? Angle.zero();
    this.steps = Object.freeze([...steps]);
    this.warnings = Object.freeze([...warnings]);
    Object.freeze(this);
  }

  /**
   * @param {object} args
   * @param {Rect} args.capture
   * @param {Rect} args.rect
   * @param {Sampling} args.sampling
   * @param {Angle} [args.deskew]
   * @returns {CropPlan}
   */
  static start({ capture, rect, sampling, deskew }) {
    return new CropPlan({
      capture,
      rect,
      sampling,
      deskew,
      steps: [{ rule: 'start', note: 'the anchor the profile named', before: rect, after: rect }],
    });
  }

  /** @returns {import('../geom/size.js').Size} */
  get size() {
    return this.rect.size;
  }

  /** @returns {boolean} the crop reaches past the capture */
  get overhangs() {
    return !this.capture.containsRect(this.rect);
  }

  /** @returns {number} how much of the crop falls outside the capture */
  get overhangArea() {
    return this.rect.area - this.rect.intersect(this.capture).area;
  }

  /**
   * The physical size the crop will come out at.
   *
   * @param {import('../measure/units.js').Unit} [unit]
   * @returns {{width: number, height: number, unit: import('../measure/units.js').Unit}}
   */
  physical(unit = 'in') {
    return physicalSize(this.rect.size, this.sampling, unit);
  }

  /**
   * Move the crop and say which rule did it. A rule that changes nothing
   * records nothing, so the trail holds only what actually happened.
   *
   * @param {Rect} rect
   * @param {object} args
   * @param {string} args.rule
   * @param {string} args.note
   * @returns {CropPlan}
   */
  moved(rect, { rule, note }) {
    if (rect.equals(this.rect)) return this;
    return new CropPlan({
      capture: this.capture,
      rect,
      sampling: this.sampling,
      deskew: this.deskew,
      steps: [...this.steps, { rule, note, before: this.rect, after: rect }],
      warnings: this.warnings,
    });
  }

  /**
   * @param {string} text
   * @returns {CropPlan}
   */
  warned(text) {
    if (this.warnings.includes(text)) return this;
    return new CropPlan({
      capture: this.capture,
      rect: this.rect,
      sampling: this.sampling,
      deskew: this.deskew,
      steps: this.steps,
      warnings: [...this.warnings, text],
    });
  }

  /**
   * @param {Angle} angle
   * @returns {CropPlan}
   */
  withDeskew(angle) {
    return new CropPlan({
      capture: this.capture,
      rect: this.rect,
      sampling: this.sampling,
      deskew: angle,
      steps: this.steps,
      warnings: this.warnings,
    });
  }

  /**
   * @param {string} rule
   * @returns {boolean}
   */
  touchedBy(rule) {
    return this.steps.some((step) => step.rule === rule);
  }

  /** @returns {string[]} the rules that moved the crop, in order */
  get trail() {
    return this.steps.filter((step) => step.rule !== 'start').map((step) => step.rule);
  }

  /** @returns {Record<string, unknown>} */
  toJSON() {
    return {
      rect: this.rect.toJSON(),
      capture: this.capture.toJSON(),
      sampling: this.sampling.toJSON(),
      deskew: this.deskew.degrees,
      steps: this.steps.map((step) => ({
        rule: step.rule,
        note: step.note,
        before: step.before.toJSON(),
        after: step.after.toJSON(),
      })),
      warnings: [...this.warnings],
    };
  }

  /** @returns {string} */
  toString() {
    return `${this.rect} after ${this.trail.length} rules`;
  }
}
