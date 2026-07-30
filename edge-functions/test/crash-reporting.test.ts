import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

describe('crash reporting', () => {
  let snapshot: NodeJS.ProcessEnv;

  beforeEach(() => {
    snapshot = { ...process.env };
    vi.resetModules();
    vi.spyOn(console, 'log').mockImplementation(() => {});
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    process.env = snapshot;
    vi.restoreAllMocks();
  });

  it('is disabled when SENTRY_DSN is unset', async () => {
    delete process.env.SENTRY_DSN;
    const mod = await import('../src/crash-reporting.js');

    expect(mod.isCrashReportingConfigured()).toBe(false);
  });

  it('treats a placeholder DSN as unconfigured', async () => {
    process.env.SENTRY_DSN = 'your-sentry-dsn';
    const mod = await import('../src/crash-reporting.js');

    expect(mod.isCrashReportingConfigured()).toBe(false);
  });

  it('is enabled with a real DSN', async () => {
    process.env.SENTRY_DSN = 'https://abc@o1.ingest.sentry.io/1';
    const mod = await import('../src/crash-reporting.js');

    expect(mod.isCrashReportingConfigured()).toBe(true);
  });

  it('initCrashReporting is a no-op that does not throw when unconfigured', async () => {
    delete process.env.SENTRY_DSN;
    const mod = await import('../src/crash-reporting.js');

    expect(() => mod.initCrashReporting()).not.toThrow();
  });

  it('captureError still logs when reporting is disabled', async () => {
    delete process.env.SENTRY_DSN;
    const mod = await import('../src/crash-reporting.js');

    expect(() => mod.captureError(new Error('boom'), 'POST /test')).not.toThrow();
    expect(console.error).toHaveBeenCalled();
    // The structured log carries the context tag so the failure is traceable
    // even without Sentry.
    expect(String((console.error as unknown as { mock: { calls: string[][] } }).mock.calls[0][0]))
      .toContain('POST /test');
  });

  it('captureError tolerates a non-Error value', async () => {
    delete process.env.SENTRY_DSN;
    const mod = await import('../src/crash-reporting.js');

    expect(() => mod.captureError('a string rejection', 'unhandledRejection')).not.toThrow();
    expect(() => mod.captureError(undefined, 'unhandledRejection')).not.toThrow();
  });
});
