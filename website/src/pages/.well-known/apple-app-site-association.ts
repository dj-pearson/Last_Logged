import type { APIRoute } from 'astro';
import {
  APP_LINK_PATHS,
  BUNDLE_ID,
  appleTeamId,
  warnIfPlaceholders,
} from '../../config/app-association';

export const prerender = true;

/**
 * Served at `/.well-known/apple-app-site-association` (no extension, as Apple
 * requires). `_headers` sets the `application/json` content type.
 */
export const GET: APIRoute = () => {
  warnIfPlaceholders();

  const appID = `${appleTeamId}.${BUNDLE_ID}`;

  const body = {
    applinks: {
      apps: [],
      details: [
        {
          appID,
          paths: APP_LINK_PATHS,
        },
      ],
    },
    webcredentials: {
      apps: [appID],
    },
  };

  return new Response(JSON.stringify(body, null, 2), {
    headers: { 'Content-Type': 'application/json' },
  });
};
