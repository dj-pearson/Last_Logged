import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { Hono } from 'hono';
import { rateLimit, bodyLimit, cronAuth } from '../src/middleware.js';

function appWith(middleware: Parameters<Hono['use']>[1]) {
  const app = new Hono();
  app.use('*', middleware);
  app.get('/', (c) => c.json({ ok: true }));
  app.post('/', (c) => c.json({ ok: true }));
  return app;
}

/** Distinct IP per test so the module-level rate-limit store never bleeds across cases. */
function req(ip: string, init: RequestInit = {}) {
  return new Request('http://localhost/', {
    ...init,
    headers: { 'x-forwarded-for': ip, ...(init.headers ?? {}) },
  });
}

describe('rateLimit', () => {
  it('allows requests under the limit and reports the remaining budget', async () => {
    const app = appWith(rateLimit({ windowMs: 60_000, max: 3, keyPrefix: 'under' }));

    const first = await app.fetch(req('10.0.0.1'));
    expect(first.status).toBe(200);
    expect(first.headers.get('X-RateLimit-Limit')).toBe('3');
    expect(first.headers.get('X-RateLimit-Remaining')).toBe('2');

    const second = await app.fetch(req('10.0.0.1'));
    expect(second.headers.get('X-RateLimit-Remaining')).toBe('1');
  });

  it('returns 429 once the limit is exceeded', async () => {
    const app = appWith(rateLimit({ windowMs: 60_000, max: 2, keyPrefix: 'over' }));

    expect((await app.fetch(req('10.0.0.2'))).status).toBe(200);
    expect((await app.fetch(req('10.0.0.2'))).status).toBe(200);

    const blocked = await app.fetch(req('10.0.0.2'));
    expect(blocked.status).toBe(429);
    expect(blocked.headers.get('X-RateLimit-Remaining')).toBe('0');
    await expect(blocked.json()).resolves.toMatchObject({
      error: expect.stringContaining('Too many requests'),
    });
  });

  it('counts each client IP separately', async () => {
    const app = appWith(rateLimit({ windowMs: 60_000, max: 1, keyPrefix: 'per-ip' }));

    expect((await app.fetch(req('10.0.0.3'))).status).toBe(200);
    expect((await app.fetch(req('10.0.0.3'))).status).toBe(429);
    // A different client must not inherit the first one's exhausted budget.
    expect((await app.fetch(req('10.0.0.4'))).status).toBe(200);
  });

  it('resets the budget once the window elapses', async () => {
    vi.useFakeTimers();
    try {
      const app = appWith(rateLimit({ windowMs: 1_000, max: 1, keyPrefix: 'window' }));

      expect((await app.fetch(req('10.0.0.5'))).status).toBe(200);
      expect((await app.fetch(req('10.0.0.5'))).status).toBe(429);

      vi.advanceTimersByTime(1_001);

      expect((await app.fetch(req('10.0.0.5'))).status).toBe(200);
    } finally {
      vi.useRealTimers();
    }
  });

  it('uses the first entry of a comma-separated x-forwarded-for', async () => {
    const app = appWith(rateLimit({ windowMs: 60_000, max: 1, keyPrefix: 'xff' }));

    const chained = (client: string) =>
      new Request('http://localhost/', {
        headers: { 'x-forwarded-for': `${client}, 172.16.0.1, 172.16.0.2` },
      });

    expect((await app.fetch(chained('10.0.0.6'))).status).toBe(200);
    expect((await app.fetch(chained('10.0.0.6'))).status).toBe(429);
    expect((await app.fetch(chained('10.0.0.7'))).status).toBe(200);
  });
});

describe('bodyLimit', () => {
  it('rejects a body larger than the limit with 413', async () => {
    const app = appWith(bodyLimit(100));

    const res = await app.fetch(
      req('10.0.1.1', {
        method: 'POST',
        headers: { 'content-length': '101' },
        body: 'x',
      })
    );

    expect(res.status).toBe(413);
  });

  it('allows a body at or under the limit', async () => {
    const app = appWith(bodyLimit(100));

    const res = await app.fetch(
      req('10.0.1.2', {
        method: 'POST',
        headers: { 'content-length': '100' },
        body: 'x',
      })
    );

    expect(res.status).toBe(200);
  });

  it('allows a request with no content-length header', async () => {
    const app = appWith(bodyLimit(100));
    expect((await app.fetch(req('10.0.1.3'))).status).toBe(200);
  });
});

describe('cronAuth', () => {
  const original = process.env.CRON_SECRET;

  beforeEach(() => {
    process.env.CRON_SECRET = 'super-secret';
  });

  afterEach(() => {
    if (original === undefined) delete process.env.CRON_SECRET;
    else process.env.CRON_SECRET = original;
  });

  it('accepts the correct bearer token', async () => {
    const app = appWith(cronAuth());
    const res = await app.fetch(
      req('10.0.2.1', { headers: { Authorization: 'Bearer super-secret' } })
    );
    expect(res.status).toBe(200);
  });

  it('rejects a missing Authorization header with 401', async () => {
    const app = appWith(cronAuth());
    expect((await app.fetch(req('10.0.2.2'))).status).toBe(401);
  });

  it('rejects a wrong secret with 401', async () => {
    const app = appWith(cronAuth());
    const res = await app.fetch(
      req('10.0.2.3', { headers: { Authorization: 'Bearer wrong' } })
    );
    expect(res.status).toBe(401);
  });

  it('rejects a bare token without the Bearer prefix', async () => {
    const app = appWith(cronAuth());
    const res = await app.fetch(
      req('10.0.2.4', { headers: { Authorization: 'super-secret' } })
    );
    expect(res.status).toBe(401);
  });

  it('blocks manual triggers entirely when no CRON_SECRET is configured', async () => {
    delete process.env.CRON_SECRET;
    const app = appWith(cronAuth());
    const res = await app.fetch(
      req('10.0.2.5', { headers: { Authorization: 'Bearer anything' } })
    );
    expect(res.status).toBe(403);
  });
});
