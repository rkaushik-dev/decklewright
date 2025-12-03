import { BadArgumentError } from '../errors.js';

/**
 * Reading a command line.
 *
 * This is a small parser rather than a library because the whole tool has no
 * runtime dependencies and because what a lab actually types is short: a
 * command, a path, and two or three options. The rules are the ones people
 * already expect, and the ones they expect and do not get are the ones that
 * waste an afternoon.
 *
 * What it handles: long options with a value after a space or after an equals
 * sign, short options bundled or not, a lone double dash ending the options,
 * and everything else in order as positionals. What it refuses: an option the
 * command does not declare, and an option that wants a value and did not get
 * one. Both refusals name the option, because a typo in a flag is the most
 * common thing that goes wrong here and the least interesting to diagnose.
 */

/**
 * @typedef {object} OptionSpec
 * @property {string} [short] one letter
 * @property {'string'|'number'|'boolean'} [type]
 * @property {string} describe what it does, for the help
 * @property {any} [fallback] what it is when it is not given
 * @property {boolean} [repeat] collect every use into a list
 */

/**
 * @param {string} token
 * @returns {boolean}
 */
function isLong(token) {
  return token.startsWith('--') && token.length > 2;
}

/**
 * @param {string} token
 * @returns {boolean}
 */
function isShort(token) {
  return token.startsWith('-') && token.length > 1 && !token.startsWith('--');
}

/**
 * @param {Record<string, OptionSpec>} spec
 * @returns {Map<string, string>} short letter to long name
 */
function shortNames(spec) {
  /** @type {Map<string, string>} */
  const out = new Map();
  for (const [name, one] of Object.entries(spec)) {
    if (!one.short) continue;
    if (out.has(one.short)) {
      throw new BadArgumentError('two options want the same letter', {
        letter: one.short,
        options: `${out.get(one.short)} and ${name}`,
      });
    }
    out.set(one.short, name);
  }
  return out;
}

/**
 * @param {OptionSpec} spec
 * @param {string} name
 * @param {string} raw
 * @returns {any}
 */
function coerce(spec, name, raw) {
  if (spec.type === 'number') {
    const value = Number(raw);
    if (!Number.isFinite(value)) {
      throw new BadArgumentError('that option wants a number', { option: name, given: raw });
    }
    return value;
  }
  return raw;
}

/**
 * @param {readonly string[]} argv
 * @param {Record<string, OptionSpec>} spec
 * @returns {{options: Record<string, any>, positional: string[], rest: string[]}}
 */
export function parseArgs(argv, spec = {}) {
  const shorts = shortNames(spec);

  /** @type {Record<string, any>} */
  const options = {};
  /** @type {string[]} */
  const positional = [];
  /** @type {string[]} */
  const rest = [];

  /**
   * @param {string} name
   * @param {any} value
   */
  const put = (name, value) => {
    const one = spec[name];
    if (one?.repeat) options[name] = [...(options[name] ?? []), value];
    else options[name] = value;
  };

  /**
   * @param {string} name
   * @returns {OptionSpec}
   */
  const find = (name) => {
    const one = spec[name];
    if (!one) throw new BadArgumentError('there is no such option', { option: `--${name}` });
    return one;
  };

  let i = 0;
  for (; i < argv.length; i += 1) {
    const token = argv[i];

    if (token === '--') {
      rest.push(...argv.slice(i + 1));
      break;
    }

    if (isLong(token)) {
      const body = token.slice(2);
      const at = body.indexOf('=');
      const name = at < 0 ? body : body.slice(0, at);
      const one = find(name);

      if (one.type === 'boolean' || one.type === undefined) {
        if (at >= 0) {
          put(name, body.slice(at + 1) !== 'false');
        } else {
          put(name, true);
        }
        continue;
      }

      if (at >= 0) {
        put(name, coerce(one, name, body.slice(at + 1)));
        continue;
      }
      const next = argv[i + 1];
      if (next === undefined || (isLong(next) && !next.startsWith('--no-'))) {
        throw new BadArgumentError('that option wants a value', { option: `--${name}` });
      }
      put(name, coerce(one, name, next));
      i += 1;
      continue;
    }

    if (isShort(token)) {
      const letters = token.slice(1).split('');
      for (let at = 0; at < letters.length; at += 1) {
        const name = shorts.get(letters[at]);
        if (!name) {
          throw new BadArgumentError('there is no such option', { option: `-${letters[at]}` });
        }
        const one = spec[name];
        if (one.type === 'boolean' || one.type === undefined) {
          put(name, true);
          continue;
        }
        const tail = letters.slice(at + 1).join('');
        if (tail !== '') {
          put(name, coerce(one, name, tail));
          at = letters.length;
          continue;
        }
        const next = argv[i + 1];
        if (next === undefined) {
          throw new BadArgumentError('that option wants a value', { option: `-${letters[at]}` });
        }
        put(name, coerce(one, name, next));
        i += 1;
        at = letters.length;
      }
      continue;
    }

    positional.push(token);
  }

  for (const [name, one] of Object.entries(spec)) {
    if (options[name] === undefined && one.fallback !== undefined) {
      options[name] = one.fallback;
    }
    if (one.repeat && options[name] === undefined) options[name] = [];
  }

  return { options, positional, rest };
}

/**
 * The help for one command's options, aligned so a column of them reads.
 *
 * @param {Record<string, OptionSpec>} spec
 * @returns {string[]}
 */
export function describeOptions(spec) {
  const entries = Object.entries(spec);
  if (entries.length === 0) return [];

  const heads = entries.map(([name, one]) => {
    const short = one.short ? `-${one.short}, ` : '    ';
    const value = one.type && one.type !== 'boolean' ? ` <${one.type}>` : '';
    return `${short}--${name}${value}`;
  });
  const width = Math.max(...heads.map((head) => head.length));

  return entries.map(([, one], at) => {
    const fallback = one.fallback === undefined ? '' : ` (${one.fallback})`;
    return `  ${heads[at].padEnd(width)}  ${one.describe}${fallback}`;
  });
}

/**
 * @param {Record<string, any>} options
 * @param {readonly string[]} names
 * @returns {string[]} the ones a command needs and did not get
 */
export function missingOptions(options, names) {
  return names.filter((name) => options[name] === undefined);
}
