import { Workspace } from '../../store/workspace.js';
import { report } from './common.js';

/**
 * Check what is on the disk against what the sidecars say.
 *
 * This is the command that runs on a schedule and is read by nobody until the
 * morning it says something. So it says as little as possible when everything
 * is fine and names every file when it is not, and it never stops at the first
 * bad one. A sweep that reports one problem out of forty is worse than useless
 * because it gets fixed and rerun and reports the next one.
 */

export const usage = 'deckle verify <workspace> [accession ...]';
export const describe = 'check delivered files against the digests beside them';

export const options = Object.freeze({
  folder: {
    short: 'd',
    type: 'string',
    fallback: 'delivered',
    describe: 'which folder to sweep',
  },
  json: { short: 'j', describe: 'write the result as json' },
  quiet: { short: 'q', describe: 'say nothing about the ones that are fine' },
});

/**
 * @param {{options: Record<string, any>, positional: string[]}} args
 * @param {import('./common.js').Io} sink
 * @returns {Promise<number>}
 */
export async function run({ options: given, positional }, sink) {
  const [root, ...named] = positional;
  if (!root) {
    sink.err('which workspace? try: deckle verify /lab/oversize');
    return 2;
  }

  try {
    const workspace = Workspace.at(root);
    const accessions = named.length > 0 ? named : await workspace.list({ folder: given.folder });

    if (accessions.length === 0) {
      sink.out(`nothing in ${given.folder}`);
      return 0;
    }

    /** @type {Awaited<ReturnType<typeof workspace.verify>>[]} */
    const results = [];
    for (const accession of accessions) {
      results.push(await workspace.verify(accession, { folder: given.folder }));
    }

    const bad = results.filter((one) => !one.ok);
    const checked = results.reduce((sum, one) => sum + one.checked, 0);

    if (given.json) {
      sink.out(
        JSON.stringify(
          {
            folder: given.folder,
            accessions: results.length,
            files: checked,
            ok: bad.length === 0,
            problems: results.filter((one) => !one.ok),
          },
          null,
          2,
        ),
      );
      return bad.length === 0 ? 0 : 1;
    }

    if (!given.quiet) {
      for (const one of results.filter((item) => item.ok)) {
        sink.out(`${one.accession}: ${one.checked} files, all as delivered`);
      }
    }
    for (const one of bad) {
      sink.err(`${one.accession}:`);
      for (const problem of one.problems) sink.err(`  ${problem}`);
    }

    sink.out(`${results.length} accessions, ${checked} files, ${bad.length} with problems`);
    return bad.length === 0 ? 0 : 1;
  } catch (caught) {
    return report(sink, caught);
  }
}
