import * as Sentry from "@sentry/node";
import { logError } from "./logger.js";

/**
 * Crash reporting for the edge functions.
 *
 * Gated on `SENTRY_DSN`, so local runs and CI send nothing.
 *
 * Privacy: these handlers see auth tokens, emails, and tracker content. PII
 * collection is off and `beforeSend` strips request headers, cookies, and
 * bodies before anything leaves the process.
 */

const SENTRY_DSN = process.env.SENTRY_DSN;

export function isCrashReportingConfigured(): boolean {
  return Boolean(SENTRY_DSN && !SENTRY_DSN.startsWith("your-"));
}

export function initCrashReporting(): void {
  if (!isCrashReportingConfigured()) {
    console.log("[startup] No SENTRY_DSN configured — crash reporting disabled.");
    return;
  }

  Sentry.init({
    dsn: SENTRY_DSN,
    environment: process.env.APP_ENV ?? "development",
    release: process.env.APP_VERSION ?? "dev",
    tracesSampleRate: 0.1,

    // Never attach IP addresses or user identifiers.
    sendDefaultPii: false,

    beforeSend(event) {
      // Authorization headers and request bodies routinely carry JWTs and the
      // user's tracker data. Drop the whole request envelope rather than trying
      // to enumerate sensitive fields.
      if (event.request) {
        delete event.request.headers;
        delete event.request.cookies;
        delete event.request.data;
        delete event.request.query_string;
      }
      delete event.user;
      delete event.server_name;
      return event;
    },

    beforeBreadcrumb(crumb) {
      // HTTP breadcrumbs include full URLs, which can contain ids.
      if (crumb.data) delete crumb.data;
      return crumb;
    },
  });

  console.log("[startup] Crash reporting initialised.");
}

/**
 * Reports an unhandled error with a stable, developer-authored context label.
 * Always logs, whether or not Sentry is configured.
 */
export function captureError(error: unknown, context: string): void {
  logError("unhandled_error", error, { context });

  if (!isCrashReportingConfigured()) return;

  Sentry.withScope((scope) => {
    scope.setTag("context", context);
    Sentry.captureException(error);
  });
}
