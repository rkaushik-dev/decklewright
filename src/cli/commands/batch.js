import { describeSummary, shiftReport } from '../../store/index.js';
import { Workspace } from '../../store/workspace.js';
import { describeRun } from '../../pipeline/runner.js';
import { policyFor } from '../../pipeline/policy.js';
import { processIncoming } from '../../pipeline/index.js';
import { CAPTURE_OPTIONS, profileNamed, report, samplingFrom } from './common.js';

/**
 * Work a whole shift out of a workspace.
 *
 * This is the command a lab actually runs. Everything waiting in the incoming
 * folder is read, cut, graded, packed and written, and every one of them ends
 * up either in a folder with a sidecar beside it or as a line in the ledger
 * saying why not.
 *
 * It does not stop on a bad sheet unless it is told to. Two hundred captures
 * come off a stand overnight and one of them is always a lens cap.
 */

export const usage = 'deckle batch <workspace> [options]';
export const describe = 'work everything waiting in a workspace';

export const options = Object.freeze({
  ...CAPTURE_OPTIONS,
  stars: { short: 's', type: 'number', describe: 'the level to deliver at' },
  force: { short: 'f', describe: 'replace a delivery that is already there' },
  stop: { describe: 'stop the whole shift at the first failure' },
  quiet: { short: 'q', describe: 'one line per accession rather than one per stage' },
  version: { type: 'string', fallback: '0.0.0', describe: 'what to record as the agent' },
  host: { type: 'string', describe: 'which stand this is, for the provenance' },
});

/**
 * @param {{options: Record<string, any>, positional: string[]}} args
 * @param {import('./common.js').Io} sink
 * @returns {Promise<number>}
 */
export async function run({ options: given, positional }, sink) {
  const [root] = positional;
  if (!root) {
    sink.err('which workspace? try: deckle batch /lab/oversize');
    return 2;
  }

  try {
    const profile = await profileNamed(given.profile);
    const workspace = Workspace.at(root);
    const sampling = samplingFrom(given.ppi, profile);

    const rules = policyFor(profile, {
      ...(given.stars === undefined ? {} : { minimumStars: given.stars }),
      overwrite: Boolean(given.force),
      stopOnFailure: Boolean(given.stop),
    });

    const waiting = await workspace.incoming();
    if (waiting.length === 0) {
      sink.out('nothing is waiting');
      return 0;
    }
    sink.out(`${waiting.length} waiting, profile ${profile.name}`);

    const out = await processIncoming(
      workspace,
      { profile, sampling },
      { policy: rules, version: given.version, host: given.host, at: sink.now() },
    );

    for (const one of out.done) {
      if (given.quiet) {
        sink.out(`${one.entry.accession}: ${one.entry.outcome}, ${one.entry.reason ?? 'ok'}`);
      } else {
        for (const line of describeRun(one.run)) sink.out(line);
      }
    }

    if (out.stoppedEarly) {
      sink.err(`stopped after ${out.done.length} of ${waiting.length}, the policy says to`);
    }

    sink.out('');
    for (const line of describeSummary(await shiftReport(workspace))) sink.out(line);

    const bad = (out.counts.failed ?? 0) + (out.counts.skipped ?? 0);
    if (bad > 0) {
      sink.err(`${bad} accessions need somebody to look at them`);
      return 1;
    }
    return 0;
  } catch (caught) {
    return report(sink, caught);
  }
}
