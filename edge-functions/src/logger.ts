import type { Context } from "hono";

interface LogEntry {
  timestamp: string;
  level: "info" | "warn" | "error";
  event: string;
  requestId?: string;
  [key: string]: unknown;
}

function formatLog(entry: LogEntry): string {
  return JSON.stringify(entry);
}

export function log(
  event: string,
  context?: Record<string, unknown>,
  c?: Context
): void {
  const entry: LogEntry = {
    timestamp: new Date().toISOString(),
    level: "info",
    event,
    requestId: c?.get("requestId") as string | undefined,
    ...context,
  };
  console.log(formatLog(entry));
}

export function logError(
  event: string,
  error: unknown,
  context?: Record<string, unknown>,
  c?: Context
): void {
  const entry: LogEntry = {
    timestamp: new Date().toISOString(),
    level: "error",
    event,
    requestId: c?.get("requestId") as string | undefined,
    error: error instanceof Error ? error.message : String(error),
    ...context,
  };
  console.error(formatLog(entry));
}

export function logWarn(
  event: string,
  context?: Record<string, unknown>,
  c?: Context
): void {
  const entry: LogEntry = {
    timestamp: new Date().toISOString(),
    level: "warn",
    event,
    requestId: c?.get("requestId") as string | undefined,
    ...context,
  };
  console.warn(formatLog(entry));
}
