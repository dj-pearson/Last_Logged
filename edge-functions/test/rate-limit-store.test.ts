import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { Hono } from 'hono';

const rpc = vi.fn();

vi.mock('../src/supabase.js', () => ({
  supabase: { rpc: (...args: unknown[]) => rpc(...args) },
}));

// Exercise the shared-store path (the default when RATE_LIMIT_STORE is unset).
delete process.env.RATE_LIMIT_STORE;

const { rateLimit } = await import('../src/middleware.js');

function app(max: number, keyPrefix: string) {
  const a = new Hono();
  a.use('*', rateLimit({ windowMs: 60_000, max, keyPrefix }));
  a.get('/', (c) => c.json({ ok: true }));
  return a;
}

function req(ip: string) {
  return new Request('http://localhost/', { headers: { 'x-forwarded-for': ip } });
}

describe('shared rate-limit store', () => {
  beforeEach(() => {
    rpc.mockReset();
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    // Let the 30s failure-backoff from a previous test expire.
    vi.setSystemTime(new Date(Date.now() + 60_000));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('counts via the increment_rate_limit RPC', async () => {
    const resetAt = new Date(Date.now() + 60_000).toISOString();
    rpc.mockResolvedValue({ data: [{ request_count: 1, reset_at: resetAt }], error: null });

    const res = await app(5, 'shared-ok').fetch(req('10.1.0.1'));

    expect(res.status).toBe(200);
    expect(rpc).toHaveBeenCalledWith('increment_rate_limit', {
      p_bucket_key: 'shared-ok:10.1.0.1',
      p_window_ms: 60_000,
    });
    expect(res.headers.get('X-RateLimit-Remaining')).toBe('4');
  });

  it('blocks with 429 when the shared count exceeds the max', async () => {
    const resetAt = new Date(Date.now() + 60_000).toISOString();
    rpc.mockResolvedValue({ data: [{ request_count: 6, reset_at: resetAt }], error: null });

    const res = await app(5, 'shared-over').fetch(req('10.1.0.2'));

    expect(res.status).toBe(429);
    expect(res.headers.get('X-RateLimit-Remaining')).toBe('0');
  });

  it('accepts a non-array RPC result', async () => {
    const resetAt = new Date(Date.now() + 60_000).toISOString();
    rpc.mockResolvedValue({ data: { request_count: 2, reset_at: resetAt }, error: null });

    const res = await app(5, 'shared-obj').fetch(req('10.1.0.3'));

    expect(res.status).toBe(200);
    expect(res.headers.get('X-RateLimit-Remaining')).toBe('3');
  });

  it('falls back to the in-memory store when the RPC errors', async () => {
    rpc.mockResolvedValue({ data: null, error: { message: 'connection refused' } });

    const a = app(1, 'shared-fallback');
    // Still enforced locally rather than failing open.
    expect((await a.fetch(req('10.1.0.4'))).status).toBe(200);
    expect((await a.fetch(req('10.1.0.4'))).status).toBe(429);
  });

  it('stops hammering the RPC for 30s after a failure', async () => {
    rpc.mockResolvedValue({ data: null, error: { message: 'down' } });

    const a = app(100, 'shared-backoff');
    await a.fetch(req('10.1.0.5'));
    const callsAfterFirst = rpc.mock.calls.length;

    await a.fetch(req('10.1.0.6'));
    await a.fetch(req('10.1.0.7'));

    // Subsequent requests short-circuit to the local store.
    expect(rpc.mock.calls.length).toBe(callsAfterFirst);
  });

  it('falls back when the RPC returns no row', async () => {
    rpc.mockResolvedValue({ data: [], error: null });

    const res = await app(5, 'shared-empty').fetch(req('10.1.0.8'));

    expect(res.status).toBe(200);
  });
});
