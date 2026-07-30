import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { validateRequiredEnv } from '../src/validate-env.js';

const REQUIRED = [
  'SUPABASE_URL',
  'SUPABASE_SERVICE_ROLE_KEY',
  'REVENUECAT_WEBHOOK_SECRET',
  'APNS_KEY_ID',
  'APNS_TEAM_ID',
];

const FCM = ['FCM_PROJECT_ID', 'FCM_CLIENT_EMAIL', 'FCM_PRIVATE_KEY'];

describe('validateRequiredEnv', () => {
  let snapshot: NodeJS.ProcessEnv;
  let exit: ReturnType<typeof vi.spyOn>;
  let error: ReturnType<typeof vi.spyOn>;
  let warn: ReturnType<typeof vi.spyOn>;
  let logSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    snapshot = { ...process.env };
    for (const key of [...REQUIRED, ...FCM]) delete process.env[key];
    for (const key of REQUIRED) process.env[key] = `real-${key}`;

    // process.exit would kill the test runner.
    exit = vi.spyOn(process, 'exit').mockImplementation(((): never => {
      throw new Error('process.exit called');
    }) as never);
    error = vi.spyOn(console, 'error').mockImplementation(() => {});
    warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
  });

  afterEach(() => {
    process.env = snapshot;
    vi.restoreAllMocks();
  });

  it('passes when every required variable is set', () => {
    expect(() => validateRequiredEnv()).not.toThrow();
    expect(exit).not.toHaveBeenCalled();
  });

  it.each(REQUIRED)('exits when %s is missing', (key) => {
    delete process.env[key];

    expect(() => validateRequiredEnv()).toThrow('process.exit called');
    expect(error).toHaveBeenCalled();
    expect(String(error.mock.calls[0][0])).toContain(key);
  });

  it.each(['your-service-role-key', 'YOUR_KEY_ID'])(
    'treats the placeholder %s as missing',
    (placeholder) => {
      process.env.SUPABASE_SERVICE_ROLE_KEY = placeholder;

      expect(() => validateRequiredEnv()).toThrow('process.exit called');
      expect(String(error.mock.calls[0][0])).toContain('SUPABASE_SERVICE_ROLE_KEY');
    }
  );

  it('reports every missing variable at once, not just the first', () => {
    delete process.env.APNS_KEY_ID;
    delete process.env.APNS_TEAM_ID;

    expect(() => validateRequiredEnv()).toThrow('process.exit called');

    const message = String(error.mock.calls[0][0]);
    expect(message).toContain('APNS_KEY_ID');
    expect(message).toContain('APNS_TEAM_ID');
  });

  it('warns (but does not exit) when FCM is entirely unconfigured', () => {
    expect(() => validateRequiredEnv()).not.toThrow();

    expect(warn).toHaveBeenCalled();
    expect(String(warn.mock.calls[0][0])).toContain('Android devices will be SKIPPED');
    expect(logSpy).toHaveBeenCalled();
  });

  it('warns about a partial FCM configuration and names the gap', () => {
    process.env.FCM_PROJECT_ID = 'my-project';
    process.env.FCM_CLIENT_EMAIL = 'svc@my-project.iam.gserviceaccount.com';
    // FCM_PRIVATE_KEY intentionally absent.

    expect(() => validateRequiredEnv()).not.toThrow();

    const message = String(warn.mock.calls[0][0]);
    expect(message).toContain('partially configured');
    expect(message).toContain('FCM_PRIVATE_KEY');
  });

  it('stays quiet when FCM is fully configured', () => {
    process.env.FCM_PROJECT_ID = 'my-project';
    process.env.FCM_CLIENT_EMAIL = 'svc@my-project.iam.gserviceaccount.com';
    process.env.FCM_PRIVATE_KEY = '-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----';

    expect(() => validateRequiredEnv()).not.toThrow();
    expect(warn).not.toHaveBeenCalled();
  });
});
