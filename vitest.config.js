import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['tests/**/*.test.js'],
    environment: 'node',
    clearMocks: true,
    // A capture that goes through the whole pipeline takes a second or two of
    // real work, and under coverage it takes several. The default five seconds
    // was failing the end to end tests on a cold runner and nowhere else,
    // which is the least useful kind of red build there is.
    testTimeout: 30000,
    coverage: {
      provider: 'v8',
      reporter: ['text-summary'],
      include: ['src/**/*.js'],
      // The floors are under where the tree sits, not at it. A gate set at the
      // current number turns every honest refactor into a red build, and a
      // team that has learned to rerun a red build has no gate at all.
      thresholds: {
        statements: 85,
        branches: 80,
        functions: 85,
        lines: 85,
      },
    },
  },
});
