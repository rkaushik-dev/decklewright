import { describeOptions, parseArgs } from './args.js';
import * as batch from './commands/batch.js';
import { io, report } from './commands/common.js';
import * as crop from './commands/crop.js';
import * as plan from './commands/plan.js';
import * as profile from './commands/profile.js';
import * as qc from './commands/qc.js';
import * as verify from './commands/verify.js';

/**
 * The front door.
 *
 * It dispatches and nothing else. Every command owns its own options and its
 * own help, so adding one means writing one file and adding one line here, and
 * the help for a command cannot drift from what the command actually accepts
 * because both come off the same object.
 *
 * The exit codes are worth stating because a lab will put this in a cron job.
 * Zero means it did what it was asked. One means it ran and the answer was
 * bad: a capture that failed its grading, a delivery that did not verify. Two
 * means the command line itself was wrong, which is a different thing and
 * wants a different alert.
 */

/** Every command, in the order the help lists them. */
export const COMMANDS = Object.freeze({ plan, crop, qc, batch, profile, verify });

/** What the tool answers to. */
export const PROGRAM = 'deckle';

/**
 * @param {string} given
 * @returns {string[]} names close enough to be worth suggesting
 */
export function nearestCommands(given) {
  const names = Object.keys(COMMANDS);
  const exact = names.filter((name) => name.startsWith(given) || given.startsWith(name));
  if (exact.length > 0) return exact;
  return names.filter((name) => {
    const shared = [...name].filter((letter) => given.includes(letter)).length;
    return shared >= Math.max(2, name.length - 2);
  });
}

/**
 * @param {string} [version]
 * @returns {string[]}
 */
export function help(version = '') {
  const width = Math.max(...Object.keys(COMMANDS).map((name) => name.length));
  return [
    `${PROGRAM}${version ? ` ${version}` : ''}, crop and derivative production for oversize sheets`,
    '',
    `usage: ${PROGRAM} <command> [options]`,
    '',
    ...Object.entries(COMMANDS).map(
      ([name, command]) => `  ${name.padEnd(width)}  ${command.describe}`,
    ),
    '',
    `try ${PROGRAM} <command> --help for what a command takes`,
  ];
}

/**
 * @param {string} name
 * @returns {string[]}
 */
export function helpFor(name) {
  const command = COMMANDS[name];
  return [command.usage, '', command.describe, '', ...describeOptions(command.options)];
}

/**
 * @param {readonly string[]} argv what came after the program name
 * @param {object} [args]
 * @param {import('./commands/common.js').Io} [args.sink]
 * @param {string} [args.version]
 * @returns {Promise<number>}
 */
export async function main(argv, { sink, version = '0.0.0' } = {}) {
  const out = sink ?? io();
  const [name, ...rest] = argv;

  if (name === undefined || name === '--help' || name === '-h' || name === 'help') {
    for (const line of help(version)) out.out(line);
    return name === undefined ? 2 : 0;
  }
  if (name === '--version' || name === '-V') {
    out.out(version);
    return 0;
  }

  const command = COMMANDS[name];
  if (!command) {
    out.err(`there is no command called ${name}`);
    const near = nearestCommands(name);
    if (near.length > 0) out.err(`  did you mean ${near.join(' or ')}?`);
    else out.err(`  the commands are ${Object.keys(COMMANDS).join(', ')}`);
    return 2;
  }

  if (rest.includes('--help') || rest.includes('-h')) {
    for (const line of helpFor(name)) out.out(line);
    return 0;
  }

  /** @type {ReturnType<typeof parseArgs>} */
  let parsed;
  try {
    parsed = parseArgs(rest, command.options);
  } catch (caught) {
    report(out, caught);
    out.err(`  try ${PROGRAM} ${name} --help`);
    return 2;
  }

  const withVersion =
    'version' in command.options && parsed.options.version === '0.0.0'
      ? { ...parsed, options: { ...parsed.options, version } }
      : parsed;

  try {
    return await command.run(withVersion, out);
  } catch (caught) {
    return report(out, caught);
  }
}
