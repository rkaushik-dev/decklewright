import { describe, expect, it } from 'vitest';

import { describeOptions, missingOptions, parseArgs } from '../../src/cli/args.js';

/** @type {Record<string, import('../../src/cli/args.js').OptionSpec>} */
const SPEC = {
  profile: { short: 'p', type: 'string', describe: 'which collection profile' },
  ppi: { short: 'r', type: 'number', describe: 'the sampling frequency' },
  force: { short: 'f', type: 'boolean', describe: 'replace what is there' },
  quiet: { short: 'q', describe: 'say less' },
  exclude: { type: 'string', repeat: true, describe: 'leave a derivative out' },
  stars: { type: 'number', fallback: 3, describe: 'the level to deliver at' },
};

describe('parseArgs', () => {
  it('reads a long option with the value after a space', () => {
    expect(parseArgs(['--profile', 'quadrangles'], SPEC).options.profile).toBe('quadrangles');
  });

  it('reads a long option with the value after an equals sign', () => {
    expect(parseArgs(['--profile=quadrangles'], SPEC).options.profile).toBe('quadrangles');
  });

  it('reads a short option with the value after a space', () => {
    expect(parseArgs(['-p', 'atlas-plates'], SPEC).options.profile).toBe('atlas-plates');
  });

  it('reads a short option with the value stuck to it', () => {
    expect(parseArgs(['-patlas-plates'], SPEC).options.profile).toBe('atlas-plates');
  });

  it('turns a number option into a number', () => {
    const out = parseArgs(['--ppi', '600'], SPEC).options;
    expect(out.ppi).toBe(600);
    expect(typeof out.ppi).toBe('number');
  });

  it('refuses a number option that is not a number', () => {
    expect(() => parseArgs(['--ppi', 'six hundred'], SPEC)).toThrow(/wants a number/);
  });

  it('reads a flag as true', () => {
    expect(parseArgs(['--force'], SPEC).options.force).toBe(true);
    expect(parseArgs(['--quiet'], SPEC).options.quiet).toBe(true);
  });

  it('reads an explicit false on a flag', () => {
    expect(parseArgs(['--force=false'], SPEC).options.force).toBe(false);
  });

  it('bundles short flags', () => {
    const out = parseArgs(['-fq'], SPEC).options;
    expect(out.force).toBe(true);
    expect(out.quiet).toBe(true);
  });

  it('takes a value at the end of a bundle', () => {
    const out = parseArgs(['-fr', '400'], SPEC).options;
    expect(out.force).toBe(true);
    expect(out.ppi).toBe(400);
  });

  it('collects a repeated option into a list', () => {
    const out = parseArgs(['--exclude', 'thumb', '--exclude', 'service'], SPEC).options;
    expect(out.exclude).toEqual(['thumb', 'service']);
  });

  it('gives an empty list for a repeated option nobody used', () => {
    expect(parseArgs([], SPEC).options.exclude).toEqual([]);
  });

  it('fills in a fallback', () => {
    expect(parseArgs([], SPEC).options.stars).toBe(3);
    expect(parseArgs(['--stars', '4'], SPEC).options.stars).toBe(4);
  });

  it('keeps the positionals in order', () => {
    const out = parseArgs(['crop', 'g4181.png', '--force', 'g4182.png'], SPEC);
    expect(out.positional).toEqual(['crop', 'g4181.png', 'g4182.png']);
  });

  it('stops reading options at a lone double dash', () => {
    const out = parseArgs(['--force', '--', '--profile', 'x'], SPEC);
    expect(out.options.force).toBe(true);
    expect(out.rest).toEqual(['--profile', 'x']);
    expect(out.options.profile).toBeUndefined();
  });

  it('treats a lone dash as a positional, because that is what it means', () => {
    expect(parseArgs(['-'], SPEC).positional).toEqual(['-']);
  });

  it('refuses an option the command does not have', () => {
    expect(() => parseArgs(['--colour'], SPEC)).toThrow(/no such option/);
    expect(() => parseArgs(['-z'], SPEC)).toThrow(/no such option/);
  });

  it('names the option it did not recognise', () => {
    expect(() => parseArgs(['--colour'], SPEC)).toThrow(/no such option/);
  });

  it('refuses an option that wants a value and is last', () => {
    expect(() => parseArgs(['--profile'], SPEC)).toThrow(/wants a value/);
    expect(() => parseArgs(['-p'], SPEC)).toThrow(/wants a value/);
  });

  it('refuses an option that wants a value and got another option', () => {
    expect(() => parseArgs(['--profile', '--force'], SPEC)).toThrow(/wants a value/);
  });

  it('takes a value that looks like a negative number', () => {
    const spec = { skew: { type: 'number', describe: 'degrees' } };
    expect(parseArgs(['--skew', '-2'], spec).options.skew).toBe(-2);
  });

  it('is happy with nothing at all', () => {
    const out = parseArgs([], {});
    expect(out).toEqual({ options: {}, positional: [], rest: [] });
  });

  it('refuses a spec with two options wanting the same letter', () => {
    const clashing = {
      force: { short: 'f', describe: 'one' },
      fast: { short: 'f', describe: 'two' },
    };
    expect(() => parseArgs([], clashing)).toThrow(/same letter/);
  });
});

describe('describeOptions', () => {
  it('writes a line per option', () => {
    expect(describeOptions(SPEC)).toHaveLength(Object.keys(SPEC).length);
  });

  it('shows the short letter and the long name', () => {
    expect(describeOptions(SPEC)[0]).toMatch(/-p, --profile <string>/);
  });

  it('lines the descriptions up', () => {
    const lines = describeOptions(SPEC);
    const at = Object.values(SPEC).map((one, i) => lines[i].indexOf(one.describe));
    expect(new Set(at).size).toBe(1);
    expect(at[0]).toBeGreaterThan(0);
  });

  it('shows a fallback where there is one', () => {
    expect(describeOptions(SPEC).join('\n')).toMatch(/the level to deliver at \(3\)/);
  });

  it('leaves the type off a flag', () => {
    expect(describeOptions({ quiet: { describe: 'say less' } })[0]).not.toMatch(/</);
  });

  it('has nothing to say about a command with no options', () => {
    expect(describeOptions({})).toEqual([]);
  });
});

describe('missingOptions', () => {
  it('names what a command needs and did not get', () => {
    expect(missingOptions({ profile: 'house' }, ['profile', 'ppi'])).toEqual(['ppi']);
  });

  it('counts a false flag as given', () => {
    expect(missingOptions({ force: false }, ['force'])).toEqual([]);
  });

  it('has nothing to say when everything is there', () => {
    expect(missingOptions({ a: 1, b: 2 }, ['a', 'b'])).toEqual([]);
  });
});
