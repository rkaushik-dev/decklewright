#!/usr/bin/env node
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { main } from '../src/cli/main.js';

/**
 * The executable.
 *
 * It is this short on purpose. Everything it does that could go wrong is in
 * main, which takes its argv and its writer as arguments and is therefore
 * tested. What is left here is reading the version off the package and setting
 * the exit code, and neither of those can be tested without spawning a
 * process.
 */

const here = dirname(fileURLToPath(import.meta.url));

/** @returns {Promise<string>} */
async function version() {
  try {
    return JSON.parse(await readFile(join(here, '..', 'package.json'), 'utf8')).version;
  } catch {
    return '0.0.0';
  }
}

process.exitCode = await main(process.argv.slice(2), { version: await version() });
