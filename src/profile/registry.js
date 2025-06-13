import { ProfileError } from '../errors.js';
import { assertNonEmptyString } from '../util/assert.js';
import { BUILT_IN } from './builtin.js';
import { parseProfile } from './fields.js';
import { changedPaths, resolveExtends } from './overlay.js';
import { assertUsable, inspectProfile } from './validate.js';

/**
 * Where profiles live at run time.
 *
 * The shipped ones are registered by default and a site adds its own on top. A
 * site profile with the name of a shipped one replaces it, which is deliberate:
 * that is how a lab that disagrees with the house style says so, once, in one
 * place, rather than by passing options at every call.
 *
 * Resolution is cached because a batch of four hundred captures asks for the
 * same profile four hundred times and the extends chain is not free.
 */
export class ProfileRegistry {
  /**
   * @param {object} [options]
   * @param {boolean} [options.builtIn] register the shipped profiles
   */
  constructor({ builtIn = true } = {}) {
    /** @type {Map<string, Record<string, unknown>>} */
    this.documents = new Map();
    /** @type {Map<string, Record<string, any>>} */
    this.resolved = new Map();
    if (builtIn) {
      for (const profile of BUILT_IN) this.documents.set(profile.name, profile);
    }
  }

  /**
   * @param {Record<string, unknown>} document
   * @returns {this}
   */
  add(document) {
    const name = document.name;
    if (typeof name !== 'string' || name.length === 0) {
      throw new ProfileError('a profile needs a name before it can be registered', {});
    }
    this.documents.set(name, document);
    this.resolved.clear();
    return this;
  }

  /**
   * @param {readonly Record<string, unknown>[]} documents
   * @returns {this}
   */
  addAll(documents) {
    for (const document of documents) this.add(document);
    return this;
  }

  /**
   * @param {string} name
   * @returns {boolean}
   */
  has(name) {
    return this.documents.has(name);
  }

  /** @returns {string[]} */
  names() {
    return [...this.documents.keys()];
  }

  /**
   * @param {string} name
   * @returns {Record<string, unknown>} the document as written, before merging
   */
  document(name) {
    assertNonEmptyString(name, 'name');
    const found = this.documents.get(name);
    if (!found) {
      throw new ProfileError(`there is no profile called ${name}`, {
        wanted: name,
        known: this.names(),
      });
    }
    return found;
  }

  /**
   * The flattened, parsed, checked profile. This is what every other stage in
   * the tree takes.
   *
   * @param {string} name
   * @returns {Record<string, any>}
   */
  get(name) {
    const cached = this.resolved.get(name);
    if (cached) return cached;
    const { document } = resolveExtends(this.document(name), (parent) =>
      this.documents.get(parent),
    );
    const profile = assertUsable(parseProfile(document));
    this.resolved.set(name, profile);
    return profile;
  }

  /**
   * @param {string} name
   * @returns {import('./validate.js').Problem[]}
   */
  inspect(name) {
    const { document } = resolveExtends(this.document(name), (parent) =>
      this.documents.get(parent),
    );
    return inspectProfile(parseProfile(document));
  }

  /**
   * @param {string} name
   * @returns {{name: string, chain: string[], changed: string[], problems: number}}
   */
  explain(name) {
    const written = this.document(name);
    const { document, chain } = resolveExtends(written, (parent) => this.documents.get(parent));
    const parentName = chain[0];
    const parent = parentName
      ? resolveExtends(this.document(parentName), (p) => this.documents.get(p)).document
      : {};
    return {
      name,
      chain,
      changed: changedPaths(parent, document).filter((path) => path !== 'name'),
      problems: this.inspect(name).length,
    };
  }

  /**
   * Check every registered profile at once, which is what the verify command
   * does before a shift starts.
   *
   * @returns {Record<string, string[]>} problems keyed by profile name
   */
  inspectAll() {
    /** @type {Record<string, string[]>} */
    const out = {};
    for (const name of this.names()) {
      try {
        const problems = this.inspect(name);
        if (problems.length > 0) {
          out[name] = problems.map((problem) => `${problem.path}: ${problem.message}`);
        }
      } catch (error) {
        out[name] = [error instanceof Error ? error.message : String(error)];
      }
    }
    return out;
  }
}

/**
 * @param {readonly Record<string, unknown>[]} [documents]
 * @returns {ProfileRegistry}
 */
export function registryWith(documents = []) {
  return new ProfileRegistry().addAll(documents);
}
