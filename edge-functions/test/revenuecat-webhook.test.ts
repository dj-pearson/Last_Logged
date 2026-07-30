import { describe, it, expect, beforeAll, vi } from 'vitest';
import crypto from 'node:crypto';

const SECRET = 'test-webhook-secret';

// revenuecat-webhook.ts reads REVENUECAT_WEBHOOK_SECRET at import time, and
// pulls in the Supabase client, so both must be set up before the import.
process.env.REVENUECAT_WEBHOOK_SECRET = SECRET;

vi.mock('../src/supabase.js', () => ({
  supabase: {
    from: () => ({
      update: () => ({ eq: () => Promise.resolve({ error: null }) }),
      select: () => ({
        eq: () => ({ single: () => Promise.resolve({ data: { id: 'user-1' }, error: null }) }),
      }),
    }),
  },
}));

let app: import('hono').Hono;

beforeAll(async () => {
  const mod = await import('../src/revenuecat-webhook.js');
  app = mod.revenuecatWebhook;
});

function sign(body: string, secret = SECRET) {
  return crypto.createHmac('sha256', secret).update(body).digest('hex');
}

function post(body: string, signature?: string) {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (signature !== undefined) headers['X-RevenueCat-Signature'] = signature;

  return app.fetch(
    new Request('http://localhost/webhooks/revenuecat', {
      method: 'POST',
      headers,
      body,
    })
  );
}

const payload = JSON.stringify({
  api_version: '1.0',
  event: { type: 'INITIAL_PURCHASE', app_user_id: 'user-1', product_id: 'premium_monthly' },
});

describe('revenuecat webhook signature verification', () => {
  it('accepts a correctly signed payload', async () => {
    const res = await post(payload, sign(payload));
    expect(res.status).toBe(200);
  });

  it('rejects a missing signature header with 401', async () => {
    const res = await post(payload);
    expect(res.status).toBe(401);
    await expect(res.json()).resolves.toMatchObject({ error: expect.any(String) });
  });

  it('rejects an empty signature with 401', async () => {
    const res = await post(payload, '');
    expect(res.status).toBe(401);
  });

  it('rejects a signature computed with the wrong secret with 401', async () => {
    const res = await post(payload, sign(payload, 'not-the-secret'));
    expect(res.status).toBe(401);
  });

  it('rejects a valid signature for a different body (tamper check)', async () => {
    const tampered = JSON.stringify({
      api_version: '1.0',
      event: { type: 'INITIAL_PURCHASE', app_user_id: 'attacker', product_id: 'lifetime' },
    });

    const res = await post(tampered, sign(payload));
    expect(res.status).toBe(401);
  });

  it('returns 401 rather than 500 for a malformed signature', async () => {
    // timingSafeEqual throws on a length mismatch; the handler must catch that.
    const res = await post(payload, 'short');
    expect(res.status).toBe(401);
  });

  it('returns 401 rather than 500 for a non-hex signature', async () => {
    const res = await post(payload, 'z'.repeat(64));
    expect(res.status).toBe(401);
  });
});
