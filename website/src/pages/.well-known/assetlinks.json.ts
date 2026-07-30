import type { APIRoute } from 'astro';
import {
  ANDROID_PACKAGE,
  androidCertFingerprint,
  warnIfPlaceholders,
} from '../../config/app-association';

export const prerender = true;

/** Served at `/.well-known/assetlinks.json` for Android App Links verification. */
export const GET: APIRoute = () => {
  warnIfPlaceholders();

  const body = [
    {
      relation: ['delegate_permission/common.handle_all_urls'],
      target: {
        namespace: 'android_app',
        package_name: ANDROID_PACKAGE,
        sha256_cert_fingerprints: [androidCertFingerprint],
      },
    },
  ];

  return new Response(JSON.stringify(body, null, 2), {
    headers: { 'Content-Type': 'application/json' },
  });
};
