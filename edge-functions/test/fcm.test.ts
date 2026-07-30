import { describe, it, expect, beforeAll, beforeEach, afterEach, vi } from 'vitest';
import crypto from 'node:crypto';

// fcm.ts reads FCM_* at import time, so configure before importing.
const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const PEM = privateKey.export({ type: 'pkcs8', format: 'pem' }) as string;

process.env.FCM_PROJECT_ID = 'demo-project';
process.env.FCM_CLIENT_EMAIL = 'svc@demo.iam.gserviceaccount.com';
// Escaped-newline form, as it survives a CI secret / .env round-trip.
process.env.FCM_PRIVATE_KEY = PEM.replace(/\n/g, '\\n');

let fcm: typeof import('../src/fcm.js');

beforeAll(async () => {
  fcm = await import('../src/fcm.js');
});

const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const SEND_URL = 'https://fcm.googleapis.com/v1/projects/demo-project/messages:send';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

describe('buildDigestPayload', () => {
  it('uses singular copy for one overdue item', () => {
    const payload = fcm.buildDigestPayload([{ name: 'HVAC Filter', daysSinceCompletion: 95 }]);

    expect(payload.title).toBe('Last Logged Reminder');
    expect(payload.body).toContain('95 days');
    expect(payload.body).toContain('HVAC Filter');
  });

  it('summarises multiple overdue items', () => {
    const payload = fcm.buildDigestPayload([
      { name: 'A', daysSinceCompletion: 9 },
      { name: 'B', daysSinceCompletion: 5 },
    ]);

    expect(payload.title).toBe('2 Overdue Items');
    expect(payload.body).toContain('A (9d)');
    expect(payload.body).toContain('B (5d)');
  });

  it('truncates past five items with an ellipsis', () => {
    const items = Array.from({ length: 7 }, (_, i) => ({
      name: `Item${i}`,
      daysSinceCompletion: i,
    }));

    const payload = fcm.buildDigestPayload(items);

    expect(payload.title).toBe('7 Overdue Items');
    expect(payload.body).toContain('...');
    expect(payload.body).not.toContain('Item5');
  });
});

describe('sendFcmNotification', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fcm.resetFcmTokenCache();
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function mockTokenExchange() {
    fetchMock.mockImplementationOnce(async (url: string) => {
      expect(url).toBe(TOKEN_URL);
      return jsonResponse({ access_token: 'ya29.test', expires_in: 3600 });
    });
  }

  it('reports FCM as configured when all three vars are present', () => {
    expect(fcm.isFcmConfigured()).toBe(true);
  });

  it('signs a verifiable RS256 JWT for the token exchange', async () => {
    let assertion = '';
    fetchMock.mockImplementationOnce(async (_url: string, init: RequestInit) => {
      assertion = (init.body as URLSearchParams).get('assertion') ?? '';
      return jsonResponse({ access_token: 'ya29.test', expires_in: 3600 });
    });
    fetchMock.mockImplementationOnce(async () => jsonResponse({ name: 'ok' }));

    await fcm.sendFcmNotification('device-token', { title: 't', body: 'b' });

    const [header, claims, signature] = assertion.split('.');
    expect(header).toBeTruthy();

    const verify = crypto.createVerify('RSA-SHA256');
    verify.update(`${header}.${claims}`);
    const sigBuffer = Buffer.from(signature.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
    expect(verify.verify(publicKey, sigBuffer)).toBe(true);

    const decoded = JSON.parse(Buffer.from(claims, 'base64').toString());
    expect(decoded.iss).toBe('svc@demo.iam.gserviceaccount.com');
    expect(decoded.aud).toBe(TOKEN_URL);
    expect(decoded.scope).toContain('firebase.messaging');
  });

  it('posts the message to the project send endpoint on success', async () => {
    mockTokenExchange();
    fetchMock.mockImplementationOnce(async (url: string, init: RequestInit) => {
      expect(url).toBe(SEND_URL);
      const body = JSON.parse(init.body as string);
      expect(body.message.token).toBe('device-token');
      expect(body.message.notification.title).toBe('Reminder');
      expect(body.message.android.notification.channel_id).toBe('tracker_reminders');
      return jsonResponse({ name: 'projects/demo-project/messages/1' });
    });

    const result = await fcm.sendFcmNotification('device-token', {
      title: 'Reminder',
      body: 'Log it',
    });

    expect(result).toEqual({ ok: true, unregistered: false });
  });

  it('reuses the cached access token across sends', async () => {
    mockTokenExchange();
    fetchMock.mockImplementation(async () => jsonResponse({ name: 'ok' }));

    await fcm.sendFcmNotification('a', { title: 't', body: 'b' });
    await fcm.sendFcmNotification('b', { title: 't', body: 'b' });

    const tokenCalls = fetchMock.mock.calls.filter(([url]) => url === TOKEN_URL);
    expect(tokenCalls).toHaveLength(1);
  });

  it('flags a 404 as unregistered so the device row is pruned', async () => {
    mockTokenExchange();
    fetchMock.mockImplementationOnce(async () =>
      jsonResponse({ error: { status: 'NOT_FOUND' } }, 404)
    );

    const result = await fcm.sendFcmNotification('dead', { title: 't', body: 'b' });

    expect(result.ok).toBe(false);
    expect(result.unregistered).toBe(true);
  });

  it('flags UNREGISTERED in error details as unregistered', async () => {
    mockTokenExchange();
    fetchMock.mockImplementationOnce(async () =>
      jsonResponse({ error: { status: 'INVALID_ARGUMENT', details: [{ errorCode: 'UNREGISTERED' }] } }, 400)
    );

    const result = await fcm.sendFcmNotification('dead', { title: 't', body: 'b' });

    expect(result.unregistered).toBe(true);
  });

  it('does NOT prune on a transient 500', async () => {
    mockTokenExchange();
    fetchMock.mockImplementationOnce(async () => jsonResponse({ error: { status: 'INTERNAL' } }, 500));

    const result = await fcm.sendFcmNotification('good-token', { title: 't', body: 'b' });

    expect(result.ok).toBe(false);
    expect(result.unregistered).toBe(false);
  });

  it('surfaces a token-exchange failure without throwing', async () => {
    fetchMock.mockImplementationOnce(async () => new Response('nope', { status: 401 }));

    const result = await fcm.sendFcmNotification('token', { title: 't', body: 'b' });

    expect(result.ok).toBe(false);
    expect(result.unregistered).toBe(false);
    expect(result.error).toContain('token exchange failed');
  });
});
