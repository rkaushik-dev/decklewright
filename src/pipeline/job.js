import { PipelineError } from '../errors.js';

/**
 * One sheet, moving through the stages.
 *
 * A job carries what has been worked out about an accession so far, and it is
 * immutable: every stage hands back a new job with what it produced added.
 * That is not purity for its own sake. It means a stage cannot quietly reach
 * back and change something an earlier stage decided, which is exactly the
 * failure that makes a crop and the sidecar describing it disagree.
 *
 * The values are a flat record with names the stages agree on. A flat record
 * rather than a class because the set of things a run knows grows with the
 * profile, and because a stage a lab writes itself has to be able to put
 * something in it without changing this file.
 */

export class Job {
  /**
   * @param {object} args
   * @param {string} args.accession
   * @param {Record<string, any>} [args.values]
   * @param {readonly string[]} [args.done] the stages that have run
   * @param {readonly string[]} [args.notes]
   * @param {readonly string[]} [args.warnings]
   */
  constructor({ accession, values = {}, done = [], notes = [], warnings = [] }) {
    if (!accession) throw new PipelineError('a job has to be about something', {});
    this.accession = accession;
    this.values = Object.freeze({ ...values });
    this.done = Object.freeze([...done]);
    this.notes = Object.freeze([...notes]);
    this.warnings = Object.freeze([...warnings]);
    Object.freeze(this);
  }

  /**
   * @param {string} accession
   * @param {Record<string, any>} [values]
   * @returns {Job}
   */
  static start(accession, values) {
    return new Job({ accession, values });
  }

  /**
   * @param {string} name
   * @returns {any}
   */
  get(name) {
    return this.values[name];
  }

  /**
   * The one a stage uses when it cannot go on without something.
   *
   * @param {string} name
   * @returns {any}
   */
  need(name) {
    if (!(name in this.values) || this.values[name] === undefined) {
      throw new PipelineError('the job does not carry that yet', {
        accession: this.accession,
        wanted: name,
        has: Object.keys(this.values).join(', ') || 'nothing',
      });
    }
    return this.values[name];
  }

  /**
   * @param {string} name
   * @returns {boolean}
   */
  has(name) {
    return name in this.values && this.values[name] !== undefined;
  }

  /**
   * A new job with more known about it.
   *
   * Overwriting a value a stage already produced is refused. Two stages both
   * writing `plan` is a graph that was wired wrong, and finding that out here
   * is far cheaper than finding it out from a delivery that was cropped twice.
   *
   * @param {Record<string, any>} values
   * @param {object} [options]
   * @param {boolean} [options.replace] allow overwriting
   * @returns {Job}
   */
  with(values, { replace = false } = {}) {
    if (!replace) {
      for (const key of Object.keys(values)) {
        if (this.has(key)) {
          throw new PipelineError('something has already put that on the job', {
            accession: this.accession,
            key,
          });
        }
      }
    }
    return new Job({
      accession: this.accession,
      values: { ...this.values, ...values },
      done: this.done,
      notes: this.notes,
      warnings: this.warnings,
    });
  }

  /**
   * @param {string} name
   * @returns {Job}
   */
  finished(name) {
    if (this.done.includes(name)) {
      throw new PipelineError('that stage has already run on this job', {
        accession: this.accession,
        stage: name,
      });
    }
    return new Job({ ...this.plain(), done: [...this.done, name] });
  }

  /**
   * @param {string} text
   * @returns {Job}
   */
  noted(text) {
    return new Job({ ...this.plain(), notes: [...this.notes, text] });
  }

  /**
   * @param {string} text
   * @returns {Job}
   */
  warned(text) {
    return new Job({ ...this.plain(), warnings: [...this.warnings, text] });
  }

  /**
   * @param {readonly string[]} texts
   * @returns {Job}
   */
  warnedAll(texts) {
    return texts.length === 0
      ? this
      : new Job({ ...this.plain(), warnings: [...this.warnings, ...texts] });
  }

  /** @returns {{accession: string, values: Record<string, any>, done: string[], notes: string[], warnings: string[]}} */
  plain() {
    return {
      accession: this.accession,
      values: { ...this.values },
      done: [...this.done],
      notes: [...this.notes],
      warnings: [...this.warnings],
    };
  }

  /**
   * What a job looks like in a log. The values are named and not printed,
   * because one of them is four hundred megabytes of pixels.
   *
   * @returns {string}
   */
  toString() {
    const carried = Object.keys(this.values).sort().join(', ') || 'nothing';
    return `${this.accession}: after ${this.done.length} stages, carrying ${carried}`;
  }
}

/**
 * @param {Job} job
 * @param {readonly string[]} names
 * @returns {string[]} the ones the job does not carry
 */
export function missingFrom(job, names) {
  return names.filter((name) => !job.has(name));
}
