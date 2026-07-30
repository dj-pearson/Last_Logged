import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    include: ['test/**/*.test.ts'],
    // Modules in src/ read process.env at import time, so each test file needs
    // its own module registry to set env before importing.
    isolate: true,
  },
});
